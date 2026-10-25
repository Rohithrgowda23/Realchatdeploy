package com.realchat.service;

import com.realchat.domain.*;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import com.realchat.service.FileTypeDetector.Detected;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Stories / status updates. The server clock decides expiry; every read re-checks who may see a story; media is
 * stored on disk (not MySQL) and is served only through an authorised endpoint; expired stories are purged with their files.
 */
@Service
public class StoryService {
    public record StoryDto(Long id, String type, String text, String bgColor, String mimeType, Instant createdAt, Instant expiresAt, boolean viewed, long viewCount) {}
    public record FeedGroup(Long userId, String name, String avatarUrl, boolean own, boolean hasUnviewed, List<StoryDto> stories) {}
    public record Viewer(Long userId, String name, String avatarUrl, Instant viewedAt) {}
    public record HiddenUser(Long id, String name) {}
    public record Settings(String audience, List<HiddenUser> hiddenFrom) {}
    public record MediaResult(Story story, Resource resource) {}

    static final Set<String> AUDIENCES = Set.of("CONTACTS", "CONTACTS_AND_GROUPS", "NOBODY");
    static final int MAX_ACTIVE = 30;
    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    static final long MAX_VIDEO_BYTES = 25L * 1024 * 1024;
    private static final Set<String> IMAGE_MIMES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final Set<String> VIDEO_MIMES = Set.of("video/mp4", "video/webm");

    private final StoryRepository stories;
    private final StoryViewRepository views;
    private final StorySettingsRepository settings;
    private final StoryHiddenRepository hiddenRepo;
    private final BlockRepository blocks;
    private final ConversationRepository convs;
    private final UserRepository users;
    private final FileStorage storage;
    private final RateLimitService limiter;

    public StoryService(StoryRepository stories, StoryViewRepository views, StorySettingsRepository settings, StoryHiddenRepository hiddenRepo,
                        BlockRepository blocks, ConversationRepository convs, UserRepository users, FileStorage storage, RateLimitService limiter) {
        this.stories = stories; this.views = views; this.settings = settings; this.hiddenRepo = hiddenRepo; this.blocks = blocks;
        this.convs = convs; this.users = users; this.storage = storage; this.limiter = limiter;
    }

    private static ApiException nf() { return ApiException.notFound("Story not found"); }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { r.run(); } });
        } else r.run();
    }

    // ---------------------------------------------------------------- who may see whose stories
    public boolean canView(Long viewer, Long author) {
        if (viewer.equals(author)) return true;
        if (blocks.existsByBlockerIdAndBlockedId(viewer, author) || blocks.existsByBlockerIdAndBlockedId(author, viewer)) return false;
        if (hiddenRepo.existsByUserIdAndHiddenUserId(author, viewer)) return false;
        String audience = settings.findById(author).map(StorySettings::getAudience).orElse("CONTACTS");
        return switch (audience) {
            case "NOBODY" -> false;
            case "CONTACTS_AND_GROUPS" -> isContact(viewer, author) || stories.sharedGroups(viewer, author) > 0;
            default -> isContact(viewer, author); // contacts = people you have a direct chat with
        };
    }

    private boolean isContact(Long a, Long b) { return convs.findByDirectKey(Math.min(a, b) + "_" + Math.max(a, b)).isPresent(); }

    // ---------------------------------------------------------------- create / delete
    @Transactional
    public StoryDto create(Long me, String type, String text, String bgColor, Integer ttlHours, MultipartFile file) {
        String t = type == null ? "" : type.strip().toUpperCase(Locale.ROOT);
        if (!Set.of("TEXT", "IMAGE", "VIDEO").contains(t)) throw ApiException.bad("Unknown story type");
        int ttl = ttlHours == null ? 24 : ttlHours;
        if (ttl < 1 || ttl > 48) throw ApiException.bad("A story can last between 1 and 48 hours");
        String body = text == null ? null : text.strip();
        if (body != null && body.length() > 500) throw ApiException.bad("Text is too long (500 characters max)");
        if (body != null && body.isEmpty()) body = null;
        String color = null;
        if ("TEXT".equals(t)) {
            if (body == null) throw ApiException.bad("Write something for your story");
            color = bgColor == null || bgColor.isBlank() ? "#4c1d95" : bgColor.strip();
            if (!color.matches("^#[0-9a-fA-F]{6}$")) throw ApiException.bad("Invalid background colour");
        }
        limiter.check("story:create:" + me, 20, Duration.ofHours(1));
        Instant now = Instant.now();
        if (stories.countByAuthorIdAndExpiresAtAfter(me, now) >= MAX_ACTIVE) throw ApiException.conflict("You have too many active stories. Delete one first.");

        Story s = new Story();
        s.setAuthorId(me); s.setType(t); s.setText(body); s.setBgColor(color); s.setCreatedAt(now); s.setExpiresAt(now.plus(Duration.ofHours(ttl))); // server time only
        String stored = null;
        if (!"TEXT".equals(t)) {
            if (file == null || file.isEmpty()) throw ApiException.bad("Choose a photo or video");
            byte[] head;
            try (InputStream in = file.getInputStream()) { head = in.readNBytes(32); } catch (IOException e) { throw ApiException.bad("The file could not be read"); }
            Detected d = FileTypeDetector.detect(head, FileTypeDetector.safeName(file.getOriginalFilename()), false)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This file type is not allowed"));
            boolean ok = "IMAGE".equals(t) ? "IMAGE".equals(d.kind()) && IMAGE_MIMES.contains(d.mime()) : "VIDEO".equals(d.kind()) && VIDEO_MIMES.contains(d.mime());
            if (!ok) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "That is not a supported " + t.toLowerCase(Locale.ROOT) + " file");
            long limit = "IMAGE".equals(t) ? MAX_IMAGE_BYTES : MAX_VIDEO_BYTES;
            if (file.getSize() > limit) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large (limit " + (limit / 1_048_576) + " MB)");
            stored = storage.store(file);
            s.setStoredName(stored); s.setMimeType(d.mime()); s.setSizeBytes(file.getSize());
        }
        try {
            return toDto(stories.save(s), false, 0);
        } catch (RuntimeException e) {
            if (stored != null) storage.deleteQuietly(stored);
            throw e;
        }
    }

    @Transactional
    public void delete(Long id, Long me) {
        Story s = stories.findById(id).filter(x -> x.getAuthorId().equals(me)).orElseThrow(StoryService::nf);
        String stored = s.getStoredName();
        stories.delete(s);
        if (stored != null) afterCommit(() -> storage.deleteQuietly(stored));
    }

    /** Called by the scheduler: removes expired rows (views cascade) and their media files. */
    @Transactional
    public int cleanupExpired() {
        List<Story> old = stories.expired(Instant.now(), PageRequest.of(0, 200));
        for (Story s : old) {
            String stored = s.getStoredName();
            stories.delete(s);
            if (stored != null) afterCommit(() -> storage.deleteQuietly(stored));
        }
        return old.size();
    }

    // ---------------------------------------------------------------- reading
    @Transactional(readOnly = true)
    public List<FeedGroup> feed(Long me) {
        Set<Long> candidates = new LinkedHashSet<>();
        candidates.add(me);
        candidates.addAll(stories.directPartners(me));
        candidates.addAll(stories.groupmates(me));
        List<Long> allowed = candidates.stream().filter(a -> canView(me, a)).toList();
        List<Story> active = stories.active(allowed, Instant.now());
        if (active.isEmpty()) return List.of();

        List<Long> ids = active.stream().map(Story::getId).toList();
        Set<Long> viewed = new HashSet<>(views.viewedIds(me, ids));
        Map<Long, Long> counts = new HashMap<>();
        views.counts(active.stream().filter(s -> s.getAuthorId().equals(me)).map(Story::getId).toList()).forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        Map<Long, User> byId = users.findAllById(active.stream().map(Story::getAuthorId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, u -> u));

        Map<Long, List<StoryDto>> grouped = new LinkedHashMap<>();
        for (Story s : active) grouped.computeIfAbsent(s.getAuthorId(), k -> new ArrayList<>()).add(toDto(s, viewed.contains(s.getId()), counts.getOrDefault(s.getId(), 0L)));
        List<FeedGroup> out = new ArrayList<>();
        grouped.forEach((author, list) -> {
            User u = byId.get(author);
            if (u == null) return;
            boolean own = author.equals(me);
            out.add(new FeedGroup(author, u.getName(), u.getAvatarUrl(), own, !own && list.stream().anyMatch(x -> !x.viewed()), list));
        });
        out.sort(Comparator.comparing(FeedGroup::own).reversed().thenComparing(FeedGroup::hasUnviewed, Comparator.reverseOrder())
                .thenComparing(g -> g.stories().get(g.stories().size() - 1).createdAt(), Comparator.reverseOrder()));
        return out;
    }

    private Story visible(Long id, Long me) {
        Story s = stories.findById(id).orElseThrow(StoryService::nf);
        if (!s.getExpiresAt().isAfter(Instant.now())) throw nf(); // expired stories are gone for everyone, including the author
        if (!canView(me, s.getAuthorId())) throw nf();
        return s;
    }

    @Transactional(readOnly = true)
    public MediaResult openMedia(Long id, Long me) {
        Story s = visible(id, me);
        if (s.getStoredName() == null) throw nf();
        return new MediaResult(s, storage.load(s.getStoredName()));
    }

    @Transactional
    public void markViewed(Long id, Long me) {
        Story s = visible(id, me);
        if (!s.getAuthorId().equals(me)) views.markViewed(id, me, Instant.now()); // authors do not count as viewers of their own story
    }

    @Transactional(readOnly = true)
    public List<Viewer> viewers(Long id, Long me) {
        Story s = visible(id, me);
        if (!s.getAuthorId().equals(me)) throw ApiException.forbidden("Only the author can see who viewed a story");
        List<StoryView> list = views.findByStoryIdOrderByViewedAtDesc(id);
        Map<Long, User> byId = users.findAllById(list.stream().map(StoryView::getViewerId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, u -> u));
        return list.stream().filter(v -> byId.containsKey(v.getViewerId()))
                .map(v -> new Viewer(v.getViewerId(), byId.get(v.getViewerId()).getName(), byId.get(v.getViewerId()).getAvatarUrl(), v.getViewedAt())).toList();
    }

    // ---------------------------------------------------------------- privacy settings
    @Transactional(readOnly = true)
    public Settings getSettings(Long me) {
        String audience = settings.findById(me).map(StorySettings::getAudience).orElse("CONTACTS");
        List<Long> ids = hiddenRepo.findByUserId(me).stream().map(StoryHidden::getHiddenUserId).toList();
        List<HiddenUser> hidden = users.findAllById(ids).stream().map(u -> new HiddenUser(u.getId(), u.getName())).toList();
        return new Settings(audience, hidden);
    }

    @Transactional
    public Settings setSettings(Long me, String audience, List<Long> hiddenIds) {
        if (audience == null || !AUDIENCES.contains(audience)) throw ApiException.bad("Unknown audience");
        Set<Long> ids = new LinkedHashSet<>(hiddenIds == null ? List.of() : hiddenIds);
        ids.remove(me);
        if (ids.size() > 100) throw ApiException.bad("Too many people in the hidden list");
        if (users.findAllById(ids).size() != ids.size()) throw ApiException.bad("One or more users do not exist");
        StorySettings s = settings.findById(me).orElseGet(() -> { StorySettings x = new StorySettings(); x.setUserId(me); return x; });
        s.setAudience(audience);
        settings.save(s);
        hiddenRepo.deleteByUserId(me);
        for (Long id : ids) { StoryHidden h = new StoryHidden(); h.setUserId(me); h.setHiddenUserId(id); hiddenRepo.save(h); }
        return getSettings(me);
    }

    private StoryDto toDto(Story s, boolean viewed, long viewCount) {
        return new StoryDto(s.getId(), s.getType(), s.getText(), s.getBgColor(), s.getMimeType(), s.getCreatedAt(), s.getExpiresAt(), viewed, viewCount);
    }
}