package com.realchat.service;

import com.realchat.domain.Conversation;
import com.realchat.domain.ConversationMember;
import com.realchat.domain.ScheduledMessage;
import com.realchat.domain.User;
import com.realchat.dto.Dto.MessageDto;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backend-driven scheduled messages. Times are UTC instants (the browser converts from the user's time zone).
 * Delivery re-runs every normal send rule (membership, announcement mode, blocks, rate limit) at send time. A row is
 * claimed atomically, and "send + mark SENT" commit as one transaction, so a message is never sent twice.
 */
@Service
public class ScheduledMessageService {
    public record ScheduledDto(Long id, Long conversationId, String conversationTitle, String content, Instant scheduledAt, String status,
                               int attempts, String lastError, Instant sentAt) {}

    static final Duration MIN_LEAD = Duration.ofSeconds(30);
    static final Duration MAX_LEAD = Duration.ofDays(365);
    static final int MAX_PENDING = 50;
    static final int MAX_ATTEMPTS = 3;
    private static final int MAX_LEN = 4000;

    private final ScheduledRepository repo;
    private final MemberRepository members;
    private final ConversationRepository convs;
    private final UserRepository users;
    private final ChatService chat;
    private final NotificationService notifications;
    private final RateLimitService limiter;

    public ScheduledMessageService(ScheduledRepository repo, MemberRepository members, ConversationRepository convs, UserRepository users,
                                   ChatService chat, NotificationService notifications, RateLimitService limiter) {
        this.repo = repo; this.members = members; this.convs = convs; this.users = users; this.chat = chat; this.notifications = notifications; this.limiter = limiter;
    }

    private static String clean(String content) {
        String t = content == null ? "" : content.strip();
        if (t.isEmpty()) throw ApiException.bad("Message cannot be empty");
        if (t.length() > MAX_LEN) throw ApiException.bad("Message is too long");
        return t;
    }

    private static void checkTime(Instant at) {
        if (at == null) throw ApiException.bad("Choose a date and time");
        Instant now = Instant.now();
        if (at.isBefore(now.plus(MIN_LEAD))) throw ApiException.bad("Choose a time at least a minute in the future");
        if (at.isAfter(now.plus(MAX_LEAD))) throw ApiException.bad("Messages can be scheduled up to one year ahead");
    }

    @Transactional
    public ScheduledDto create(Long convId, Long me, String content, Long replyToId, Instant at) {
        ConversationMember cm = members.findByConversationIdAndUserId(convId, me).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        Conversation c = convs.findById(convId).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (c.isAnnouncementOnly() && "MEMBER".equals(cm.getRole())) throw ApiException.forbidden("Only admins can post in this group");
        String text = clean(content);
        checkTime(at);
        limiter.check("sched:create:" + me, 30, Duration.ofHours(1));
        if (repo.countBySenderIdAndStatus(me, "PENDING") >= MAX_PENDING) throw ApiException.conflict("You have too many scheduled messages. Cancel some first.");
        ScheduledMessage s = new ScheduledMessage();
        s.setConversationId(convId); s.setSenderId(me); s.setContent(text); s.setReplyToId(replyToId); s.setScheduledAt(at); s.setStatus("PENDING");
        return toDto(repo.save(s), c.getName());
    }

    @Transactional(readOnly = true)
    public List<ScheduledDto> list(Long me) {
        List<ScheduledMessage> all = repo.findTop100BySenderIdOrderByScheduledAtDesc(me);
        Map<Long, String> titles = convs.findAllById(all.stream().map(ScheduledMessage::getConversationId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Conversation::getId, c -> c.getName() == null ? "" : c.getName()));
        return all.stream().map(s -> toDto(s, titles.getOrDefault(s.getConversationId(), ""))).toList();
    }

    private ScheduledMessage ownedPending(Long id, Long me) {
        ScheduledMessage s = repo.findById(id).filter(x -> x.getSenderId().equals(me)).orElseThrow(() -> ApiException.notFound("Scheduled message not found"));
        if (!"PENDING".equals(s.getStatus())) throw ApiException.conflict("This message was already sent, cancelled or is being sent");
        return s;
    }

    @Transactional
    public ScheduledDto update(Long id, Long me, String content, Instant at) {
        ScheduledMessage s = ownedPending(id, me);
        if (content != null) s.setContent(clean(content));
        if (at != null) { checkTime(at); s.setScheduledAt(at); }
        return toDto(repo.saveAndFlush(s), null); // optimistic lock: fails with 409 if the scheduler claimed it meanwhile
    }

    @Transactional
    public void cancel(Long id, Long me) {
        ScheduledMessage s = ownedPending(id, me);
        s.setStatus("CANCELLED");
        repo.saveAndFlush(s);
    }

    // ------------------------------------------------------------ scheduler side
    @Transactional
    public List<Long> claimDue(int limit) {
        Instant now = Instant.now();
        return repo.dueIds(now, PageRequest.of(0, limit)).stream().filter(id -> repo.claim(id, now) == 1).toList();
    }

    @Transactional
    public int releaseStuck() { return repo.releaseStuck(Instant.now().minus(Duration.ofMinutes(5))); }

    /** Sends and marks SENT in ONE transaction. If anything throws, nothing was sent and the caller records the failure. */
    @Transactional
    public void deliver(Long id) {
        ScheduledMessage s = repo.findById(id).orElse(null);
        if (s == null || !"SENDING".equals(s.getStatus())) return;
        MessageDto sent = chat.send(s.getConversationId(), s.getSenderId(), s.getContent(), s.getReplyToId());
        s.setStatus("SENT"); s.setSentMessageId(sent.id()); s.setSentAt(Instant.now()); s.setLastError(null);
        repo.save(s);
    }

    /** Separate transaction from deliver(): rule violations fail at once, transient problems retry with a growing delay. */
    @Transactional
    public void recordFailure(Long id, Exception e) {
        ScheduledMessage s = repo.findById(id).orElse(null);
        if (s == null || !"SENDING".equals(s.getStatus())) return;
        s.setAttempts(s.getAttempts() + 1);
        boolean transientError = !(e instanceof ApiException a) || a.getStatus() == HttpStatus.TOO_MANY_REQUESTS;
        String reason = e instanceof ApiException a ? a.getMessage() : "Temporary error";
        s.setLastError(reason.length() > 250 ? reason.substring(0, 250) : reason);
        s.setClaimedAt(null);
        if (transientError && s.getAttempts() < MAX_ATTEMPTS) {
            s.setStatus("PENDING");
            s.setScheduledAt(Instant.now().plus(Duration.ofMinutes(s.getAttempts())));
        } else {
            s.setStatus("FAILED");
            notifications.notify(s.getSenderId(), "SCHEDULED_FAILED", "Scheduled message was not sent", reason, s.getConversationId(), null);
        }
        repo.save(s);
    }

    private ScheduledDto toDto(ScheduledMessage s, String title) {
        String t = title;
        if (t == null || t.isBlank()) t = directTitle(s.getConversationId(), s.getSenderId());
        return new ScheduledDto(s.getId(), s.getConversationId(), t, s.getContent(), s.getScheduledAt(), s.getStatus(), s.getAttempts(), s.getLastError(), s.getSentAt());
    }

    private String directTitle(Long convId, Long me) {
        return members.memberIds(convId).stream().filter(i -> !i.equals(me)).findFirst().flatMap(users::findById).map(User::getName).orElse("Chat");
    }
}