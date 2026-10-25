package com.realchat.service;

import com.realchat.domain.CallRecord;
import com.realchat.domain.Conversation;
import com.realchat.domain.User;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * One-to-one WebRTC call signalling over the existing STOMP connection. The server only relays small signalling
 * messages between the two participants of a call it created itself; media flows browser-to-browser. WebRTC media is
 * encrypted in transit (DTLS-SRTP) but this is NOT end-to-end encryption in the Signal sense: a TURN relay or anyone who
 * controls the endpoints is outside this guarantee, and the server could in principle interfere with signalling.
 * Active-call state is in memory (single backend instance); history is in MySQL.
 */
@Service
public class CallService {
    private static final Logger log = LoggerFactory.getLogger(CallService.class);
    static final Duration RING_TIMEOUT = Duration.ofSeconds(45);
    static final Duration OFFLINE_GRACE = Duration.ofSeconds(15);
    static final int MAX_SIGNAL_CHARS = 16_384;

    static final class CallState {
        final String uid; final Long recordId; final Long conversationId; final Long callerId; final Long calleeId; final String type; final Instant startedAt;
        String status = "RINGING"; Instant answeredAt; Instant callerOfflineSince; Instant calleeOfflineSince;
        CallState(String uid, Long recordId, Long conversationId, Long callerId, Long calleeId, String type, Instant startedAt) {
            this.uid = uid; this.recordId = recordId; this.conversationId = conversationId; this.callerId = callerId; this.calleeId = calleeId; this.type = type; this.startedAt = startedAt;
        }
        boolean involves(Long u) { return callerId.equals(u) || calleeId.equals(u); }
        Long other(Long u) { return callerId.equals(u) ? calleeId : callerId; }
    }

    public record CallView(Long id, String callId, Long conversationId, String direction, Long peerId, String peerName, String peerAvatar, String type,
                           String status, Instant startedAt, Integer durationSeconds, boolean missed) {}

    private final Map<String, CallState> active = new LinkedHashMap<>();
    private final CallRepository calls;
    private final MemberRepository members;
    private final ConversationRepository convs;
    private final BlockRepository blocks;
    private final UserRepository users;
    private final ChatService chat;
    private final PresenceService presence;
    private final NotificationService notifications;
    private final RateLimitService limiter;

    public CallService(CallRepository calls, MemberRepository members, ConversationRepository convs, BlockRepository blocks, UserRepository users,
                       ChatService chat, PresenceService presence, NotificationService notifications, RateLimitService limiter) {
        this.calls = calls; this.members = members; this.convs = convs; this.blocks = blocks; this.users = users; this.chat = chat;
        this.presence = presence; this.notifications = notifications; this.limiter = limiter;
    }

    @EventListener(ApplicationReadyEvent.class)
    void closeStaleRecords() { int n = calls.closeStale(Instant.now()); if (n > 0) log.warn("Closed {} call(s) left open by a restart", n); }

    private boolean inCall(Long user) { return active.values().stream().anyMatch(s -> s.involves(user)); }

    private CallState require(String uid, Long me) {
        CallState s = uid == null ? null : active.get(uid);
        if (s == null || !s.involves(me)) throw ApiException.notFound("Call not found"); // same answer for "does not exist" and "not yours"
        return s;
    }

    // ------------------------------------------------------------------ call setup
    public synchronized void start(Long me, Long conversationId, String type) {
        String t = type == null ? "" : type.strip().toUpperCase(Locale.ROOT);
        if (!t.equals("AUDIO") && !t.equals("VIDEO")) throw ApiException.bad("Unknown call type");
        members.findByConversationIdAndUserId(conversationId, me).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        Conversation c = convs.findById(conversationId).orElseThrow(() -> ApiException.notFound("Conversation not found"));
        if (!"DIRECT".equals(c.getType())) throw ApiException.bad("Calls are only available in direct chats");
        Long other = members.memberIds(conversationId).stream().filter(i -> !i.equals(me)).findFirst().orElseThrow(() -> ApiException.bad("Nobody to call"));
        if (blocks.existsByBlockerIdAndBlockedId(me, other) || blocks.existsByBlockerIdAndBlockedId(other, me)) throw ApiException.forbidden("You cannot call this user");

        // repeated start (double click, reconnect replay): answer with the call that is already ringing, never create a second record
        for (CallState s : active.values()) {
            if (s.callerId.equals(me) && s.calleeId.equals(other) && "RINGING".equals(s.status)) {
                chat.broadcast(List.of(me), "CALL_RINGING", Map.of("callId", s.uid, "conversationId", conversationId, "type", s.type));
                return;
            }
        }
        if (inCall(me)) throw ApiException.conflict("You are already in a call");
        limiter.check("call:start:" + me, 20, Duration.ofMinutes(10));

        CallRecord r = new CallRecord();
        r.setCallUid(UUID.randomUUID().toString()); r.setConversationId(conversationId); r.setCallerId(me); r.setCalleeId(other); r.setType(t);
        r.setStatus("RINGING"); r.setStartedAt(Instant.now());
        r = calls.save(r);
        CallState s = new CallState(r.getCallUid(), r.getId(), conversationId, me, other, t, r.getStartedAt());
        active.put(s.uid, s);

        boolean busy = inCallExcept(other, s);
        if (busy || !presence.isOnline(other)) { finish(s, "MISSED", busy ? "busy" : "unavailable"); return; }
        User caller = users.findById(me).orElse(null);
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("callId", s.uid); incoming.put("conversationId", conversationId); incoming.put("callerId", me); incoming.put("type", t);
        incoming.put("callerName", caller == null ? "Someone" : caller.getName()); incoming.put("callerAvatar", caller == null || caller.getAvatarUrl() == null ? "" : caller.getAvatarUrl());
        chat.broadcast(List.of(other), "CALL_INCOMING", incoming);
        chat.broadcast(List.of(me), "CALL_RINGING", Map.of("callId", s.uid, "conversationId", conversationId, "type", t));
    }

    private boolean inCallExcept(Long user, CallState except) { return active.values().stream().anyMatch(s -> s != except && s.involves(user)); }

    public synchronized void accept(Long me, String uid) {
        CallState s = require(uid, me);
        if (!s.calleeId.equals(me)) throw ApiException.forbidden("Only the person being called can answer");
        if ("ACTIVE".equals(s.status)) return; // repeated accept
        if (!"RINGING".equals(s.status)) throw ApiException.bad("This call is no longer available");
        s.status = "ACTIVE"; s.answeredAt = Instant.now();
        calls.findById(s.recordId).ifPresent(r -> { r.setStatus("ACTIVE"); r.setAnsweredAt(s.answeredAt); calls.save(r); });
        chat.broadcast(List.of(s.callerId), "CALL_ACCEPTED", Map.of("callId", s.uid));
    }

    public synchronized void reject(Long me, String uid) {
        CallState s = require(uid, me);
        if (!s.calleeId.equals(me)) throw ApiException.forbidden("Only the person being called can decline");
        if (!"RINGING".equals(s.status)) return;
        finish(s, "REJECTED", "rejected");
    }

    public synchronized void cancel(Long me, String uid) {
        CallState s = require(uid, me);
        if (!s.callerId.equals(me)) throw ApiException.forbidden("Only the caller can cancel");
        if (!"RINGING".equals(s.status)) return;
        finish(s, "MISSED", "cancelled");
    }

    /** Hang up. Works for both sides; a call that was still ringing is treated as cancel (caller) or decline (callee). Repeats are ignored. */
    public synchronized void end(Long me, String uid, boolean failed) {
        CallState s = uid == null ? null : active.get(uid);
        if (s == null) return; // already finished: idempotent
        if (!s.involves(me)) throw ApiException.notFound("Call not found");
        if ("RINGING".equals(s.status)) { if (s.callerId.equals(me)) finish(s, "MISSED", "cancelled"); else finish(s, "REJECTED", "rejected"); return; }
        finish(s, failed ? "FAILED" : "COMPLETED", failed ? "failed" : "ended");
    }

    // ------------------------------------------------------------------ signalling relay
    public synchronized void signal(Long me, String uid, String kind, String payload) {
        CallState s = require(uid, me);
        if (!"ACTIVE".equals(s.status)) throw ApiException.bad("The call has not been answered yet");
        if (kind == null || !(kind.equals("offer") || kind.equals("answer") || kind.equals("ice"))) throw ApiException.bad("Unknown signal");
        if (payload == null || payload.isEmpty() || payload.length() > MAX_SIGNAL_CHARS) throw ApiException.bad("Invalid signal");
        if (kind.equals("offer") && !s.callerId.equals(me)) throw ApiException.forbidden("Only the caller can send an offer");
        if (kind.equals("answer") && !s.calleeId.equals(me)) throw ApiException.forbidden("Only the callee can send an answer");
        limiter.check("call:signal:" + me, 600, Duration.ofMinutes(1));
        chat.broadcast(List.of(s.other(me)), "CALL_SIGNAL", Map.of("callId", s.uid, "kind", kind, "payload", payload)); // sender comes from the server-side call state
    }

    // ------------------------------------------------------------------ housekeeping
    private void finish(CallState s, String status, String reason) {
        active.remove(s.uid);
        Instant now = Instant.now();
        calls.findById(s.recordId).ifPresent(r -> {
            r.setStatus(status); r.setEndedAt(now); r.setEndReason(reason);
            if (s.answeredAt != null) r.setDurationSeconds((int) Math.max(0, Duration.between(s.answeredAt, now).getSeconds()));
            calls.save(r);
        });
        chat.broadcast(List.of(s.callerId, s.calleeId), "CALL_ENDED", Map.of("callId", s.uid, "reason", reason, "status", status));
        if ("MISSED".equals(status)) {
            String name = users.findById(s.callerId).map(User::getName).orElse("Someone");
            notifications.notify(s.calleeId, "MISSED_CALL", "Missed " + ("VIDEO".equals(s.type) ? "video call" : "call") + " from " + name, null, s.conversationId, null);
        }
    }

    /** Every few seconds: ring timeouts and participants whose connection vanished mid-call. */
    public synchronized void sweep(Instant now) {
        for (CallState s : new ArrayList<>(active.values())) {
            if ("RINGING".equals(s.status)) {
                if (Duration.between(s.startedAt, now).compareTo(RING_TIMEOUT) > 0) finish(s, "MISSED", "timeout");
                continue;
            }
            s.callerOfflineSince = track(s.callerOfflineSince, presence.isOnline(s.callerId), now);
            s.calleeOfflineSince = track(s.calleeOfflineSince, presence.isOnline(s.calleeId), now);
            if (tooLong(s.callerOfflineSince, now) || tooLong(s.calleeOfflineSince, now)) finish(s, "FAILED", "disconnected");
        }
    }
    private static Instant track(Instant since, boolean online, Instant now) { return online ? null : (since == null ? now : since); }
    private static boolean tooLong(Instant since, Instant now) { return since != null && Duration.between(since, now).compareTo(OFFLINE_GRACE) > 0; }

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    void scheduledSweep() { try { sweep(Instant.now()); } catch (RuntimeException e) { log.warn("Call sweep failed: {}", e.getClass().getSimpleName()); } }

    // ------------------------------------------------------------------ history
    public List<CallView> history(Long me, Long before, int size) {
        List<CallRecord> list = calls.history(me, before == null ? Long.MAX_VALUE : before, PageRequest.of(0, Math.min(Math.max(size, 1), 50)));
        Set<Long> peerIds = list.stream().map(c -> c.getCallerId().equals(me) ? c.getCalleeId() : c.getCallerId()).collect(Collectors.toSet());
        Map<Long, User> byId = users.findAllById(peerIds).stream().collect(Collectors.toMap(User::getId, u -> u));
        return list.stream().map(c -> {
            boolean outgoing = c.getCallerId().equals(me);
            Long peer = outgoing ? c.getCalleeId() : c.getCallerId();
            User u = byId.get(peer);
            return new CallView(c.getId(), c.getCallUid(), c.getConversationId(), outgoing ? "OUTGOING" : "INCOMING", peer, u == null ? "Unknown" : u.getName(),
                    u == null ? null : u.getAvatarUrl(), c.getType(), c.getStatus(), c.getStartedAt(), c.getDurationSeconds(), !outgoing && "MISSED".equals(c.getStatus()));
        }).toList();
    }

    public long missedCount(Long me) { return calls.unseenMissed(me); }
    public void markSeen(Long me) { calls.markSeen(me); }
}