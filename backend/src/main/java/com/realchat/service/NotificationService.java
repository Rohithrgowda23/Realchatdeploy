package com.realchat.service;

import com.realchat.domain.Notification;
import com.realchat.domain.NotificationPrefs;
import com.realchat.dto.Dto.Event;
import com.realchat.exception.ApiException;
import com.realchat.repository.NotificationPrefsRepository;
import com.realchat.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Persistent notifications (separate from live toasts). Every read/write is keyed by the authenticated user id. */
@Service
public class NotificationService {
    public record NotificationDto(Long id, String type, String title, String body, Long conversationId, Long messageId, boolean read, Instant createdAt) {}
    public record Prefs(boolean mentions, boolean joinRequests, boolean scheduled, boolean calls) {}

    private final NotificationRepository repo;
    private final NotificationPrefsRepository prefs;
    private final SimpMessagingTemplate ws;

    public NotificationService(NotificationRepository repo, NotificationPrefsRepository prefs, SimpMessagingTemplate ws) {
        this.repo = repo; this.prefs = prefs; this.ws = ws;
    }

    @Transactional
    public void notify(Long userId, String type, String title, String body, Long conversationId, Long messageId) {
        if (!enabled(userId, type)) return;
        Notification n = new Notification();
        n.setUserId(userId); n.setType(type); n.setTitle(clip(title, 160)); n.setBody(clip(body, 255)); n.setConversationId(conversationId); n.setMessageId(messageId);
        NotificationDto dto = toDto(repo.save(n));
        Runnable push = () -> ws.convertAndSendToUser(String.valueOf(userId), "/queue/events", new Event("NOTIFICATION", dto));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { push.run(); } });
        } else push.run();
    }

    private boolean enabled(Long userId, String type) {
        Prefs p = getPrefs(userId);
        return switch (type) {
            case "MENTION" -> p.mentions();
            case "JOIN_REQUEST", "JOIN_DECISION" -> p.joinRequests();
            case "SCHEDULED_FAILED" -> p.scheduled();
            case "MISSED_CALL" -> p.calls();
            default -> true;
        };
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(Long me, Long before, int size, boolean unreadOnly) {
        PageRequest pr = PageRequest.of(0, Math.min(Math.max(size, 1), 50));
        long b = before == null ? Long.MAX_VALUE : before;
        return (unreadOnly ? repo.findByUserIdAndReadAtIsNullAndIdLessThanOrderByIdDesc(me, b, pr) : repo.findByUserIdAndIdLessThanOrderByIdDesc(me, b, pr))
                .stream().map(NotificationService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long me) { return repo.countByUserIdAndReadAtIsNull(me); }

    /** Marks the given ids (or everything when none are given) as read; ids belonging to other users are simply ignored. */
    @Transactional
    public void markRead(Long me, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) repo.markAllRead(me, Instant.now());
        else repo.markRead(me, ids, Instant.now());
    }

    @Transactional
    public void dismiss(Long me, Long id) {
        if (repo.deleteByIdAndUserId(id, me) == 0) throw ApiException.notFound("Notification not found");
    }

    @Transactional
    public void clear(Long me) { repo.deleteByUserId(me); }

    @Transactional(readOnly = true)
    public Prefs getPrefs(Long me) {
        return prefs.findById(me).map(p -> new Prefs(p.isMentions(), p.isJoinRequests(), p.isScheduled(), p.isCalls())).orElse(new Prefs(true, true, true, true));
    }

    @Transactional
    public Prefs setPrefs(Long me, Prefs in) {
        NotificationPrefs p = prefs.findById(me).orElseGet(() -> { NotificationPrefs x = new NotificationPrefs(); x.setUserId(me); return x; });
        p.setMentions(in.mentions()); p.setJoinRequests(in.joinRequests()); p.setScheduled(in.scheduled()); p.setCalls(in.calls());
        prefs.save(p);
        return in;
    }

    private static NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getConversationId(), n.getMessageId(), n.getReadAt() != null, n.getCreatedAt());
    }
    private static String clip(String s, int n) { return s == null ? null : (s.length() <= n ? s : s.substring(0, n - 1) + "…"); }
}