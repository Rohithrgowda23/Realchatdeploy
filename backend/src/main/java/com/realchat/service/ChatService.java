package com.realchat.service;

import com.realchat.domain.*;
import com.realchat.dto.Dto.*;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * All conversation/message rules live here and are enforced server-side: the acting user always comes from the
 * authenticated principal, membership and role are re-read from the database on every call.
 */
@Service
@RequiredArgsConstructor
public class ChatService {
    static final String OWNER = "OWNER", ADMIN = "ADMIN", MEMBER = "MEMBER", DIRECT = "DIRECT", GROUP = "GROUP";
    private static final int MAX_LEN = 4000;
    private static final Duration EDIT_WINDOW = Duration.ofHours(24);

    private final UserRepository users;
    private final ConversationRepository convs;
    private final MemberRepository members;
    private final MessageRepository messages;
    private final ReactionRepository reactions;
    private final BlockRepository blocks;
    private final SavedRepository saved;
    private final HiddenRepository hidden;
    private final AuditRepository audits;
    private final SimpMessagingTemplate ws;
    private final PresenceService presence;
    private final RateLimitService limiter;
    private final AppMetrics metrics;
    private final AttachmentRepository attachments;
    private final FileStorage storage;
    private final MentionRepository mentions;
    private final NotificationService notifications;
    private final TranslationRepository translations;

    // ------------------------------------------------------------------ helpers
    ConversationMember requireMember(Long convId, Long userId) {
        // 404 (not 403) so the existence of other people's conversations is not revealed
        return members.findByConversationIdAndUserId(convId, userId)
                .orElseThrow(() -> ApiException.notFound("Conversation not found"));
    }

    private Conversation requireGroup(Long convId) {
        Conversation c = convs.findById(convId).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (!GROUP.equals(c.getType())) throw ApiException.bad("Not a group conversation");
        return c;
    }

    private static boolean canManage(ConversationMember m) { return OWNER.equals(m.getRole()) || ADMIN.equals(m.getRole()); }

    private boolean blockedEitherWay(Long a, Long b) {
        return blocks.existsByBlockerIdAndBlockedId(a, b) || blocks.existsByBlockerIdAndBlockedId(b, a);
    }

    public void addMember(Long convId, Long userId, String role) {
        ConversationMember m = new ConversationMember();
        m.setConversationId(convId); m.setUserId(userId); m.setRole(role);
        members.save(m);
    }

    /** Pushes an event to each listed user's private queue, after the surrounding transaction commits. */
    public void broadcast(Collection<Long> userIds, String type, Object data) {
        List<Long> ids = List.copyOf(userIds);
        Runnable r = () -> ids.forEach(id -> ws.convertAndSendToUser(String.valueOf(id), "/queue/events", new Event(type, data)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else r.run();
    }

    private String previewOf(ChatMessage m) {
        if (m.isDeleted()) return "Message deleted";
        if (m.getContent() != null && !m.getContent().isBlank()) return truncate(m.getContent(), 80);
        return attachments.findByMessageId(m.getId()).map(a -> "VOICE".equals(a.getKind()) ? "Voice message" : "IMAGE".equals(a.getKind()) ? "Photo" : "Attachment: " + a.getOriginalName()).orElse("");
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { r.run(); } });
        } else r.run();
    }

    private static String truncate(String s, int n) { return s == null ? "" : (s.length() <= n ? s : s.substring(0, n) + "…"); }

    private static String cleanText(String content) {
        String t = content == null ? "" : content.strip();
        if (t.isEmpty()) throw ApiException.bad("Message cannot be empty");
        if (t.length() > MAX_LEN) throw ApiException.bad("Message is too long");
        return t;
    }

    // ------------------------------------------------------------------ conversations
    @Transactional
    public ConversationDto createDirect(Long me, Long otherId) {
        if (me.equals(otherId)) throw ApiException.bad("You cannot chat with yourself");
        users.findById(otherId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (blockedEitherWay(me, otherId)) throw ApiException.forbidden("This conversation is unavailable");
        String key = Math.min(me, otherId) + "_" + Math.max(me, otherId);
        Conversation c = convs.findByDirectKey(key).orElseGet(() -> {
            Conversation n = new Conversation();
            n.setType(DIRECT); n.setDirectKey(key); n.setCreatedBy(me);
            n = convs.save(n);
            addMember(n.getId(), me, MEMBER);
            addMember(n.getId(), otherId, MEMBER);
            return n;
        });
        broadcast(List.of(me, otherId), "CONVERSATION_UPDATED", Map.of("conversationId", c.getId()));
        return summary(requireMember(c.getId(), me), c);
    }

    @Transactional
    public ConversationDto createGroup(Long me, String name, String description, String avatarUrl, List<Long> memberIds) {
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > 100) throw ApiException.bad("Group name must be 1-100 characters");
        Set<Long> ids = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        ids.remove(me);
        if (users.findAllById(ids).size() != ids.size()) throw ApiException.bad("One or more users do not exist");
        Conversation c = new Conversation();
        c.setType(GROUP); c.setName(n); c.setDescription(description); c.setAvatarUrl(blankToNull(avatarUrl)); c.setCreatedBy(me);
        c = convs.save(c);
        addMember(c.getId(), me, OWNER);
        audit(c.getId(), me, "GROUP_CREATED", null, n);
        for (Long id : ids) { addMember(c.getId(), id, MEMBER); audit(c.getId(), me, "MEMBER_ADDED", id, null); }
        broadcast(members.memberIds(c.getId()), "CONVERSATION_UPDATED", Map.of("conversationId", c.getId()));
        return summary(requireMember(c.getId(), me), c);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.strip(); }

    @Transactional(readOnly = true)
    public List<ConversationDto> list(Long me) {
        List<ConversationDto> out = new ArrayList<>();
        for (ConversationMember m : members.findByUserId(me)) {
            convs.findById(m.getConversationId()).ifPresent(c -> out.add(summary(m, c)));
        }
        out.sort((a, b) -> {
            if (a.pinned() != b.pinned()) return a.pinned() ? -1 : 1;
            return activity(b).compareTo(activity(a));
        });
        return out;
    }

    private static java.time.Instant activity(ConversationDto d) { return d.lastMessage() != null ? d.lastMessage().createdAt() : d.createdAt(); }

    private ConversationDto summary(ConversationMember me, Conversation c) {
        List<Long> ids = members.memberIds(c.getId());
        String title = c.getName();
        String avatar = c.getAvatarUrl();
        Long other = null;
        if (DIRECT.equals(c.getType())) {
            other = ids.stream().filter(i -> !i.equals(me.getUserId())).findFirst().orElse(me.getUserId());
            User u = users.findById(other).orElseThrow();
            title = u.getName();
            avatar = u.getAvatarUrl();
        }
        LastMessage last = messages.findFirstByConversationIdOrderByIdDesc(c.getId())
                .map(m -> new LastMessage(m.getId(), m.getSenderId(), previewOf(m), m.getCreatedAt()))
                .orElse(null);
        long unread = messages.countUnread(c.getId(), me.getLastReadMessageId(), me.getUserId());
        long unreadMentions = mentions.countUnreadMentions(c.getId(), me.getLastReadMessageId(), me.getUserId());
        return new ConversationDto(c.getId(), c.getType(), title, c.getDescription(), avatar, other, last, unread,
                me.isPinned(), me.isMuted(), me.isArchived(), ids.size(), me.getRole(), c.getCreatedAt(), c.isAnnouncementOnly(), unreadMentions);
    }

    @Transactional(readOnly = true)
    public ConversationDetail detail(Long convId, Long me) {
        ConversationMember mine = requireMember(convId, me);
        Conversation c = convs.findById(convId).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        List<ConversationMember> all = members.findByConversationId(convId);
        Map<Long, User> byId = users.findAllById(all.stream().map(ConversationMember::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        List<MemberDto> dtos = all.stream().map(m -> {
            User u = byId.get(m.getUserId());
            return new MemberDto(m.getUserId(), u.getName(), u.getAvatarUrl(), m.getRole(), m.getLastReadMessageId(), m.getLastDeliveredMessageId());
        }).toList();
        return new ConversationDetail(summary(mine, c), dtos);
    }

    @Transactional
    public ConversationDto updateSettings(Long convId, Long me, Boolean pinned, Boolean muted, Boolean archived) {
        ConversationMember m = requireMember(convId, me);
        if (pinned != null) m.setPinned(pinned);
        if (muted != null) m.setMuted(muted);
        if (archived != null) m.setArchived(archived);
        members.save(m);
        return summary(m, convs.findById(convId).orElseThrow());
    }

    // ------------------------------------------------------------------ messages
    @Transactional(readOnly = true)
    public List<MessageDto> history(Long convId, Long me, Long before, int size) {
        requireMember(convId, me);
        int n = Math.min(Math.max(size, 1), 100);
        List<ChatMessage> page = new ArrayList<>(messages.page(convId, before == null ? Long.MAX_VALUE : before, me, PageRequest.of(0, n)));
        Collections.reverse(page);
        return toDtos(page);
    }

    @Transactional(readOnly = true)
    public List<MessageDto> searchMessages(Long convId, Long me, String q) {
        requireMember(convId, me);
        if (q == null || q.strip().length() < 2) throw ApiException.bad("Search text must be at least 2 characters");
        return toDtos(messages.search(convId, q.strip(), me, PageRequest.of(0, 50)));
    }

    @Transactional
    public MessageDto send(Long convId, Long me, String content, Long replyToId) {
        return post(convId, me, content, replyToId, null, null);
    }

    private MessageDto post(Long convId, Long me, String content, Long replyToId, Long fwdId, String fwdName) {
        return post(convId, me, content, replyToId, fwdId, fwdName, false, null);
    }

    /** Same rules as a text message (membership, announcement mode, blocks, rate limit); the caption may be empty. */
    /** Text message with structured @mentions: every mentioned user must currently be a member of the conversation. */
    @Transactional
    public MessageDto send(Long convId, Long me, String content, Long replyToId, List<Long> mentionIds) {
        if (mentionIds == null || mentionIds.isEmpty()) return post(convId, me, content, replyToId, null, null);
        requireMember(convId, me); // non-members learn nothing about who is in the conversation
        Set<Long> ids = new LinkedHashSet<>(mentionIds);
        ids.remove(me);
        if (ids.size() > 20) throw ApiException.bad("Too many mentions");
        if (!new HashSet<>(members.memberIds(convId)).containsAll(ids)) throw ApiException.bad("You can only mention members of this conversation");
        Map<Long, String> names = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getName));
        String senderName = users.findById(me).map(User::getName).orElse("Someone");
        return post(convId, me, content, replyToId, null, null, false, msg -> ids.forEach(uid -> {
            MessageMention mm = new MessageMention();
            mm.setMessageId(msg.getId()); mm.setUserId(uid); mm.setNameAtSend(names.getOrDefault(uid, "user"));
            mentions.save(mm);
            notifications.notify(uid, "MENTION", senderName + " mentioned you", truncate(msg.getContent(), 100), convId, msg.getId());
        }));
    }

    @Transactional(readOnly = true)
    public List<MessageDto> mentionsOfMe(Long convId, Long me, Long before, int size) {
        requireMember(convId, me);
        return toDtos(new ArrayList<>(mentions.mentioning(convId, me, before == null ? Long.MAX_VALUE : before, PageRequest.of(0, Math.min(Math.max(size, 1), 50)))));
    }

    @Transactional
    public MessageDto sendWithAttachment(Long convId, Long me, String caption, Long replyToId, java.util.function.Consumer<ChatMessage> afterSave) {
        return post(convId, me, caption, replyToId, null, null, true, afterSave);
    }

    private MessageDto post(Long convId, Long me, String content, Long replyToId, Long fwdId, String fwdName,
                            boolean allowEmpty, java.util.function.Consumer<ChatMessage> afterSave) {
        ConversationMember cm = requireMember(convId, me);
        limiter.sendMessage(me); // 429 when someone floods a conversation (applies to WebSocket and REST alike)
        String text = (allowEmpty && (content == null || content.isBlank())) ? "" : cleanText(content);
        Conversation c = convs.findById(convId).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (c.isAnnouncementOnly() && MEMBER.equals(cm.getRole())) throw ApiException.forbidden("Only admins can post in this group");
        if (DIRECT.equals(c.getType())) {
            members.memberIds(convId).stream().filter(i -> !i.equals(me)).findFirst().ifPresent(other -> {
                if (blockedEitherWay(me, other)) throw ApiException.forbidden("You cannot message this user");
            });
        }
        if (replyToId != null) {
            messages.findById(replyToId).filter(r -> r.getConversationId().equals(convId))
                    .orElseThrow(() -> ApiException.bad("Invalid message to reply to"));
        }
        ChatMessage m = new ChatMessage();
        m.setConversationId(convId); m.setSenderId(me); m.setContent(text); m.setReplyToId(replyToId);
        m.setForwardedFromId(fwdId); m.setForwardedFromName(fwdName);
        m = messages.save(m);
        if (afterSave != null) afterSave.accept(m);
        cm.setLastReadMessageId(m.getId());
        members.save(cm);
        MessageDto dto = toDtos(List.of(m)).get(0);
        broadcast(members.memberIds(convId), "MESSAGE", dto);
        markDeliveredToOnline(convId, me, m.getId());
        metrics.messageSent();
        return dto;
    }

    @Transactional
    public MessageDto edit(Long msgId, Long me, String content) {
        ChatMessage m = messages.findById(msgId).orElseThrow(() -> ApiException.notFound("Message not found"));
        requireMember(m.getConversationId(), me);
        if (!m.getSenderId().equals(me)) throw ApiException.forbidden("Only the author can edit a message");
        if (m.isDeleted()) throw ApiException.bad("Message was deleted");
        if (Duration.between(m.getCreatedAt(), Instant.now()).compareTo(EDIT_WINDOW) > 0) throw ApiException.forbidden("Messages can only be edited within 24 hours");
        m.setContent(cleanText(content));
        m.setEdited(true);
        return publishUpdate(messages.save(m));
    }

    @Transactional
    public MessageDto delete(Long msgId, Long me) {
        ChatMessage m = messages.findById(msgId).orElseThrow(() -> ApiException.notFound("Message not found"));
        requireMember(m.getConversationId(), me);
        if (!m.getSenderId().equals(me)) {
            Conversation c = convs.findById(m.getConversationId()).orElseThrow(() -> ApiException.notFound("Conversation not found"));
            ConversationMember cm = requireMember(m.getConversationId(), me);
            if (!GROUP.equals(c.getType()) || !canManage(cm)) throw ApiException.forbidden("Only the author or a group admin can delete a message");
        }
        m.setDeleted(true);
        m.setContent("");
        translations.deleteByMessageId(m.getId()); // deleted text must not linger in the translation cache
        attachments.findByMessageId(m.getId()).ifPresent(a -> { // deleting for everyone also removes the file
            String stored = a.getStoredName();
            attachments.delete(a);
            afterCommit(() -> storage.deleteQuietly(stored));
        });
        return publishUpdate(messages.save(m));
    }

    @Transactional
    public MessageDto toggleReaction(Long msgId, Long me, String emoji) {
        ChatMessage m = messages.findById(msgId).orElseThrow(() -> ApiException.notFound("Message not found"));
        requireMember(m.getConversationId(), me);
        if (m.isDeleted()) throw ApiException.bad("Message was deleted");
        String e = emoji == null ? "" : emoji.strip();
        if (e.isEmpty() || e.length() > 16) throw ApiException.bad("Invalid emoji");
        Optional<MessageReaction> existing = reactions.findByMessageIdAndUserIdAndEmoji(msgId, me, e);
        if (existing.isPresent()) reactions.delete(existing.get());
        else {
            MessageReaction r = new MessageReaction();
            r.setMessageId(msgId); r.setUserId(me); r.setEmoji(e);
            reactions.save(r);
        }
        reactions.flush();
        return publishUpdate(m);
    }

    /** Recipients whose app is connected right now have received the message: record it and tell everyone. */
    private void markDeliveredToOnline(Long convId, Long sender, Long messageId) {
        List<Long> all = members.memberIds(convId);
        List<Long> online = all.stream().filter(id -> !id.equals(sender) && presence.isOnline(id)).toList();
        if (online.isEmpty()) return;
        members.markDelivered(convId, online, messageId);
        broadcast(all, "DELIVERED", Map.of("conversationId", convId, "userIds", online, "lastDeliveredMessageId", messageId));
    }

    private MessageDto publishUpdate(ChatMessage m) {
        MessageDto dto = toDtos(List.of(m)).get(0);
        broadcast(members.memberIds(m.getConversationId()), "MESSAGE_UPDATED", dto);
        return dto;
    }

    @Transactional
    public void markRead(Long convId, Long me, Long upTo) {
        ConversationMember cm = requireMember(convId, me);
        Long max = messages.maxId(convId);
        long ceiling = max == null ? 0 : max;
        long target = Math.min(upTo == null ? ceiling : upTo, ceiling);
        if (target > cm.getLastReadMessageId()) {
            cm.setLastReadMessageId(target);
            if (target > cm.getLastDeliveredMessageId()) cm.setLastDeliveredMessageId(target);
            members.save(cm);
            broadcast(members.memberIds(convId), "READ", Map.of("conversationId", convId, "userId", me, "lastReadMessageId", target));
        }
    }

    public void typing(Long convId, Long me, boolean typing) {
        requireMember(convId, me);
        String name = users.findById(me).map(User::getName).orElse("Someone");
        List<Long> others = members.memberIds(convId).stream().filter(i -> !i.equals(me)).toList();
        broadcast(others, "TYPING", Map.of("conversationId", convId, "userId", me, "name", name, "typing", typing));
    }

    List<MessageDto> toDtos(List<ChatMessage> ms) {
        if (ms.isEmpty()) return List.of();
        Set<Long> replyIds = ms.stream().map(ChatMessage::getReplyToId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ChatMessage> replies = replyIds.isEmpty() ? Map.of()
                : messages.findAllById(replyIds).stream().collect(Collectors.toMap(ChatMessage::getId, x -> x));
        Set<Long> userIds = new HashSet<>();
        ms.forEach(m -> userIds.add(m.getSenderId()));
        replies.values().forEach(r -> userIds.add(r.getSenderId()));
        Map<Long, String> names = users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getName));
        Map<Long, AttachmentDto> att = attachments.findByMessageIdIn(ms.stream().map(ChatMessage::getId).toList()).stream()
                .collect(Collectors.toMap(Attachment::getMessageId, a -> new AttachmentDto(a.getId(), a.getKind(), a.getOriginalName(), a.getMimeType(), a.getSizeBytes(), a.getDurationMs())));
        Map<Long, List<MentionDto>> mn = new HashMap<>();
        mentions.findByMessageIdIn(ms.stream().map(ChatMessage::getId).toList()).forEach(x ->
                mn.computeIfAbsent(x.getMessageId(), k -> new ArrayList<>()).add(new MentionDto(x.getUserId(), x.getNameAtSend())));
        Map<Long, Map<String, List<Long>>> rx = new HashMap<>();
        reactions.findByMessageIdIn(ms.stream().map(ChatMessage::getId).toList()).forEach(r ->
                rx.computeIfAbsent(r.getMessageId(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(r.getEmoji(), k -> new ArrayList<>()).add(r.getUserId()));
        return ms.stream().map(m -> {
            ReplyRef ref = null;
            if (m.getReplyToId() != null) {
                ChatMessage r = replies.get(m.getReplyToId());
                if (r != null) ref = new ReplyRef(r.getId(), names.getOrDefault(r.getSenderId(), "Unknown"), r.isDeleted() ? null : truncate(r.getContent(), 120));
            }
            return new MessageDto(m.getId(), m.getConversationId(), m.getSenderId(), names.getOrDefault(m.getSenderId(), "Unknown"),
                    m.isDeleted() ? null : m.getContent(), ref, m.isEdited(), m.isDeleted(), m.getCreatedAt(), rx.getOrDefault(m.getId(), Map.of()), m.getForwardedFromName(), att.get(m.getId()), m.isDeleted() ? List.of() : mn.getOrDefault(m.getId(), List.of()));
        }).toList();
    }

    // ------------------------------------------------------------------ group management
    @Transactional
    public ConversationDetail updateGroup(Long convId, Long me, String name, String description, String avatarUrl) {
        ConversationMember actor = requireMember(convId, me);
        if (!canManage(actor)) throw ApiException.forbidden("Only owners and admins can edit the group");
        Conversation c = requireGroup(convId);
        if (name != null) {
            if (name.isBlank() || name.strip().length() > 100) throw ApiException.bad("Group name must be 1-100 characters");
            c.setName(name.strip());
        }
        if (description != null) c.setDescription(blankToNull(description));
        if (avatarUrl != null) c.setAvatarUrl(blankToNull(avatarUrl));
        convs.save(c);
        audit(convId, me, "GROUP_UPDATED", null, null);
        broadcast(members.memberIds(convId), "CONVERSATION_UPDATED", Map.of("conversationId", convId));
        return detail(convId, me);
    }

    @Transactional
    public ConversationDetail addMembers(Long convId, Long me, List<Long> userIds) {
        ConversationMember actor = requireMember(convId, me);
        if (!canManage(actor)) throw ApiException.forbidden("Only owners and admins can add members");
        requireGroup(convId);
        Set<Long> existing = new HashSet<>(members.memberIds(convId));
        Set<Long> toAdd = new LinkedHashSet<>(userIds);
        toAdd.removeAll(existing);
        if (users.findAllById(toAdd).size() != toAdd.size()) throw ApiException.bad("One or more users do not exist");
        toAdd.forEach(id -> { addMember(convId, id, MEMBER); audit(convId, me, "MEMBER_ADDED", id, null); });
        broadcast(members.memberIds(convId), "CONVERSATION_UPDATED", Map.of("conversationId", convId));
        return detail(convId, me);
    }

    @Transactional
    public ConversationDetail removeMember(Long convId, Long me, Long targetId) {
        ConversationMember actor = requireMember(convId, me);
        requireGroup(convId);
        if (targetId.equals(me)) throw ApiException.bad("Use 'leave group' to remove yourself");
        ConversationMember target = members.findByConversationIdAndUserId(convId, targetId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        boolean allowed = OWNER.equals(actor.getRole()) || (ADMIN.equals(actor.getRole()) && MEMBER.equals(target.getRole()));
        if (!allowed) throw ApiException.forbidden("You are not allowed to remove this member");
        List<Long> audience = members.memberIds(convId); // includes the removed user so their UI updates
        members.delete(target);
        audit(convId, me, "MEMBER_REMOVED", targetId, null);
        broadcast(audience, "CONVERSATION_UPDATED", Map.of("conversationId", convId));
        return detail(convId, me);
    }

    @Transactional
    public ConversationDetail setRole(Long convId, Long me, Long targetId, String role) {
        ConversationMember actor = requireMember(convId, me);
        requireGroup(convId);
        if (!OWNER.equals(actor.getRole())) throw ApiException.forbidden("Only the owner can change roles");
        if (!ADMIN.equals(role) && !MEMBER.equals(role)) throw ApiException.bad("Role must be ADMIN or MEMBER");
        ConversationMember target = members.findByConversationIdAndUserId(convId, targetId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        if (OWNER.equals(target.getRole())) throw ApiException.bad("The owner's role cannot be changed; transfer ownership instead");
        target.setRole(role);
        members.save(target);
        audit(convId, me, "ROLE_CHANGED", targetId, role);
        broadcast(members.memberIds(convId), "CONVERSATION_UPDATED", Map.of("conversationId", convId));
        return detail(convId, me);
    }

    @Transactional
    public ConversationDetail transferOwnership(Long convId, Long me, Long targetId) {
        ConversationMember actor = requireMember(convId, me);
        requireGroup(convId);
        if (!OWNER.equals(actor.getRole())) throw ApiException.forbidden("Only the owner can transfer ownership");
        if (targetId.equals(me)) throw ApiException.bad("You already own this group");
        ConversationMember target = members.findByConversationIdAndUserId(convId, targetId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        target.setRole(OWNER);
        audit(convId, me, "OWNERSHIP_TRANSFERRED", targetId, null);
        actor.setRole(ADMIN);
        members.save(target);
        members.save(actor);
        broadcast(members.memberIds(convId), "CONVERSATION_UPDATED", Map.of("conversationId", convId));
        return detail(convId, me);
    }

    @Transactional
    public void leave(Long convId, Long me) {
        ConversationMember actor = requireMember(convId, me);
        requireGroup(convId);
        List<ConversationMember> others = members.findByConversationId(convId).stream().filter(m -> !m.getUserId().equals(me)).toList();
        List<Long> audience = members.memberIds(convId);
        if (others.isEmpty()) {
            convs.deleteById(convId); // last person out: DB cascades remove members, messages, reactions
        } else {
            if (OWNER.equals(actor.getRole())) {
                // hand ownership to the longest-standing admin, else the longest-standing member
                ConversationMember heir = others.stream().filter(m -> ADMIN.equals(m.getRole())).min(Comparator.comparing(ConversationMember::getId))
                        .orElseGet(() -> others.stream().min(Comparator.comparing(ConversationMember::getId)).orElseThrow());
                heir.setRole(OWNER);
                members.save(heir);
            }
            members.delete(actor);
            audit(convId, me, "MEMBER_LEFT", null, null);
        }
        broadcast(audience, "CONVERSATION_UPDATED", Map.of("conversationId", convId));
    }

    // ------------------------------------------------------------------ users / blocks
    @Transactional(readOnly = true)
    public List<UserDto> searchUsers(Long me, String q) {
        if (q == null || q.strip().length() < 2) return List.of();
        return users.search(me, q.strip(), PageRequest.of(0, 20)).stream().map(u -> UserDto.of(u, false)).toList();
    }

    @Transactional
    public void block(Long me, Long target) {
        if (me.equals(target)) throw ApiException.bad("You cannot block yourself");
        users.findById(target).orElseThrow(() -> ApiException.notFound("User not found"));
        if (!blocks.existsByBlockerIdAndBlockedId(me, target)) {
            UserBlock b = new UserBlock();
            b.setBlockerId(me); b.setBlockedId(target);
            blocks.save(b);
        }
    }

    @Transactional
    public void unblock(Long me, Long target) {
        blocks.findByBlockerIdAndBlockedId(me, target).ifPresent(blocks::delete);
    }

    @Transactional(readOnly = true)
    public List<UserDto> blockedUsers(Long me) {
        List<Long> ids = blocks.findByBlockerId(me).stream().map(UserBlock::getBlockedId).toList();
        return users.findAllById(ids).stream().map(u -> UserDto.of(u, false)).toList();
    }

    public List<Long> onlineIds() { return presence.onlineIds(); }

    // ------------------------------------------------------------------ audit, announcement mode
    /** Written in the same transaction as the action it records, so both commit or neither does. */
    public void audit(Long convId, Long actor, String action, Long target, String detail) {
        GroupAuditEvent e = new GroupAuditEvent();
        e.setConversationId(convId); e.setActorId(actor); e.setAction(action); e.setTargetId(target);
        e.setDetail(detail == null ? null : truncate(detail, 255));
        audits.save(e);
    }

    @Transactional(readOnly = true)
    public List<AuditDto> auditLog(Long convId, Long me, String action, int page, int size) {
        ConversationMember actor = requireMember(convId, me);
        if (!canManage(actor)) throw ApiException.forbidden("Only owners and admins can view the activity history");
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        List<GroupAuditEvent> ev = (action == null || action.isBlank())
                ? audits.findByConversationIdOrderByIdDesc(convId, pr) : audits.findByConversationIdAndActionOrderByIdDesc(convId, action.strip(), pr);
        Set<Long> ids = new HashSet<>();
        ev.forEach(e -> { ids.add(e.getActorId()); if (e.getTargetId() != null) ids.add(e.getTargetId()); });
        Map<Long, String> names = users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, User::getName));
        return ev.stream().map(e -> new AuditDto(e.getId(), e.getActorId(), names.getOrDefault(e.getActorId(), "Former user"), e.getAction(),
                e.getTargetId(), e.getTargetId() == null ? null : names.getOrDefault(e.getTargetId(), "Former user"), e.getDetail(), e.getCreatedAt())).toList();
    }

    @Transactional
    public ConversationDto setAnnouncement(Long convId, Long me, boolean enabled) {
        ConversationMember actor = requireMember(convId, me);
        if (!canManage(actor)) throw ApiException.forbidden("Only owners and admins can change announcement mode");
        Conversation c = requireGroup(convId);
        c.setAnnouncementOnly(enabled);
        convs.save(c);
        audit(convId, me, enabled ? "ANNOUNCEMENT_ON" : "ANNOUNCEMENT_OFF", null, null);
        broadcast(members.memberIds(convId), "CONVERSATION_UPDATED", Map.of("conversationId", convId));
        return summary(actor, c);
    }

    // ------------------------------------------------------------------ forward, delete-for-me, saved, global search
    @Transactional
    public MessageDto forward(Long msgId, Long me, Long destId, String caption) {
        ChatMessage src = messages.findById(msgId).orElseThrow(() -> ApiException.notFound("Message not found"));
        requireMember(src.getConversationId(), me); // you may only forward what you can currently read
        if (src.isDeleted() || hidden.existsByUserIdAndMessageId(me, msgId)) throw ApiException.bad("This message is no longer available");
        if (attachments.findByMessageId(msgId).isPresent()) throw ApiException.bad("Messages with attachments cannot be forwarded yet");
        String original = src.getForwardedFromName() != null ? src.getForwardedFromName()
                : users.findById(src.getSenderId()).map(User::getName).orElse("Unknown");
        MessageDto dto = post(destId, me, src.getContent(), null, src.getSenderId(), original); // destination membership/rules checked here
        if (caption != null && !caption.isBlank()) post(destId, me, caption, null, null, null);
        return dto;
    }

    @Transactional
    public void hideForMe(Long msgId, Long me) {
        ChatMessage m = messages.findById(msgId).orElseThrow(() -> ApiException.notFound("Message not found"));
        requireMember(m.getConversationId(), me);
        if (!hidden.existsByUserIdAndMessageId(me, msgId)) {
            HiddenMessage h = new HiddenMessage();
            h.setUserId(me); h.setMessageId(msgId);
            hidden.save(h);
        }
    }

    @Transactional
    public void save(Long msgId, Long me) {
        ChatMessage m = messages.findById(msgId).orElseThrow(() -> ApiException.notFound("Message not found"));
        requireMember(m.getConversationId(), me);
        if (m.isDeleted() || hidden.existsByUserIdAndMessageId(me, msgId)) throw ApiException.bad("This message is no longer available");
        if (saved.findByUserIdAndMessageId(me, msgId).isEmpty()) {
            SavedMessage s = new SavedMessage();
            s.setUserId(me); s.setMessageId(msgId);
            saved.save(s);
        }
    }

    @Transactional
    public void unsave(Long msgId, Long me) { saved.findByUserIdAndMessageId(me, msgId).ifPresent(saved::delete); }

    @Transactional(readOnly = true)
    public List<Long> savedIds(Long me) { return saved.messageIds(me); }

    @Transactional(readOnly = true)
    public List<MessageDto> savedMessages(Long me, String q, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return toDtos(saved.saved(me, q == null ? "" : q.strip(), pr));
    }

    @Transactional(readOnly = true)
    public List<MessageDto> searchAll(Long me, String q, Long convId, Long senderId, Instant from, Instant to, int page, int size) {
        String text = q == null ? "" : q.strip();
        if (text.isEmpty() && convId == null && senderId == null && from == null && to == null) throw ApiException.bad("Enter text or choose a filter");
        if (!text.isEmpty() && text.length() < 2) throw ApiException.bad("Search text must be at least 2 characters");
        if (convId != null) requireMember(convId, me);
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50));
        return toDtos(messages.searchAll(me, text, convId == null ? 0L : convId, senderId == null ? 0L : senderId,
                from == null ? Instant.EPOCH : from, to == null ? Instant.parse("9999-01-01T00:00:00Z") : to, pr));
    }
}