package com.realchat;

import com.realchat.domain.*;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import com.realchat.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallServiceTest {
    @Mock CallRepository calls;
    @Mock MemberRepository members;
    @Mock ConversationRepository convs;
    @Mock BlockRepository blocks;
    @Mock UserRepository users;
    @Mock ChatService chat;
    @Mock PresenceService presence;
    @Mock NotificationService notifications;
    @Mock RateLimitService limiter;
    CallService service;

    final Map<Long, CallRecord> stored = new HashMap<>();
    long nextId = 100;

    @BeforeEach void setUp() {
        service = new CallService(calls, members, convs, blocks, users, chat, presence, notifications, limiter);
        lenient().when(calls.save(any(CallRecord.class))).thenAnswer(i -> { CallRecord r = i.getArgument(0); if (r.getId() == null) r.setId(nextId++); stored.put(r.getId(), r); return r; });
        lenient().when(calls.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0))));
    }

    private static Conversation conv(Long id, String type) { Conversation c = new Conversation(); c.setId(id); c.setType(type); return c; }
    private void direct(Long convId, Long a, Long b) {
        lenient().when(members.findByConversationIdAndUserId(convId, a)).thenReturn(Optional.of(new ConversationMember()));
        lenient().when(convs.findById(convId)).thenReturn(Optional.of(conv(convId, "DIRECT")));
        lenient().when(members.memberIds(convId)).thenReturn(List.of(a, b));
    }
    private String startCall(Long caller, Long conv) {
        service.start(caller, conv, "AUDIO");
        return stored.values().stream().filter(r -> r.getCallerId().equals(caller) && r.getConversationId().equals(conv)).findFirst().orElseThrow().getCallUid();
    }
    private String ringing() { direct(1L, 2L, 3L); when(presence.isOnline(3L)).thenReturn(true); return startCall(2L, 1L); }
    private String activeCall() { String id = ringing(); service.accept(3L, id); return id; }

    // ---- authorisation to start
    @Test void nonMemberCannotCall() {
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.start(9L, 1L, "AUDIO")).getStatus());
        verify(calls, never()).save(any());
    }
    @Test void callsAreOnlyForDirectChats() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(new ConversationMember()));
        when(convs.findById(1L)).thenReturn(Optional.of(conv(1L, "GROUP")));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.start(2L, 1L, "VIDEO")).getStatus());
    }
    @Test void blockedUsersCannotCallEachOther() {
        direct(1L, 2L, 3L);
        when(blocks.existsByBlockerIdAndBlockedId(3L, 2L)).thenReturn(true);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.start(2L, 1L, "AUDIO")).getStatus());
        verify(calls, never()).save(any());
    }
    @Test void unknownCallTypeRejected() { assertThrows(ApiException.class, () -> service.start(2L, 1L, "HOLOGRAM")); }

    // ---- ringing, busy, offline
    @Test void startRingsOnlyTheCalleeAndTellsTheCaller() {
        direct(1L, 2L, 3L); when(presence.isOnline(3L)).thenReturn(true);
        User caller = new User(); caller.setId(2L); caller.setName("Ann");
        when(users.findById(2L)).thenReturn(Optional.of(caller));
        service.start(2L, 1L, "video");
        verify(chat).broadcast(eq(List.of(3L)), eq("CALL_INCOMING"), argThat(o -> o instanceof Map<?, ?> m && "Ann".equals(m.get("callerName")) && "VIDEO".equals(m.get("type"))));
        verify(chat).broadcast(eq(List.of(2L)), eq("CALL_RINGING"), any());
        assertEquals("RINGING", stored.values().iterator().next().getStatus());
    }
    @Test void offlineCalleeIsRecordedAsMissedAndNotified() {
        direct(1L, 2L, 3L); when(presence.isOnline(3L)).thenReturn(false);
        User caller = new User(); caller.setId(2L); caller.setName("Ann");
        when(users.findById(2L)).thenReturn(Optional.of(caller));
        service.start(2L, 1L, "AUDIO");
        CallRecord r = stored.values().iterator().next();
        assertEquals("MISSED", r.getStatus());
        assertEquals("unavailable", r.getEndReason());
        verify(notifications).notify(eq(3L), eq("MISSED_CALL"), contains("Ann"), isNull(), eq(1L), isNull());
    }
    @Test void busyCalleeMeansMissedWithBusyReason() {
        direct(1L, 2L, 3L); direct(5L, 4L, 3L);
        when(presence.isOnline(3L)).thenReturn(true);
        startCall(2L, 1L);
        service.start(4L, 5L, "AUDIO");
        CallRecord second = stored.values().stream().filter(r -> r.getCallerId().equals(4L)).findFirst().orElseThrow();
        assertEquals("MISSED", second.getStatus());
        assertEquals("busy", second.getEndReason());
    }
    @Test void repeatedStartDoesNotCreateASecondCall() {
        direct(1L, 2L, 3L); when(presence.isOnline(3L)).thenReturn(true);
        service.start(2L, 1L, "AUDIO");
        service.start(2L, 1L, "AUDIO"); // double click / replay
        verify(calls, times(1)).save(any(CallRecord.class));
        verify(chat, times(2)).broadcast(eq(List.of(2L)), eq("CALL_RINGING"), any());
        verify(chat, times(1)).broadcast(eq(List.of(3L)), eq("CALL_INCOMING"), any());
    }
    @Test void aCallerAlreadyInACallCannotStartAnother() {
        direct(1L, 2L, 3L); direct(6L, 2L, 7L); when(presence.isOnline(3L)).thenReturn(true);
        startCall(2L, 1L);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.start(2L, 6L, "AUDIO")).getStatus());
    }

    // ---- answering
    @Test void onlyTheCalleeCanAnswerAndRepeatsAreIgnored() {
        String id = ringing();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.accept(2L, id)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.accept(9L, id)).getStatus());
        service.accept(3L, id);
        service.accept(3L, id);
        verify(chat, times(1)).broadcast(eq(List.of(2L)), eq("CALL_ACCEPTED"), any());
        assertEquals("ACTIVE", stored.values().iterator().next().getStatus());
    }
    @Test void onlyTheCalleeCanDecline() {
        String id = ringing();
        assertThrows(ApiException.class, () -> service.reject(2L, id));
        service.reject(3L, id);
        assertEquals("REJECTED", stored.values().iterator().next().getStatus());
        verify(notifications, never()).notify(any(), any(), any(), any(), any(), any());
    }
    @Test void cancellingBeforeAnswerIsAMissedCallForTheCallee() {
        String id = ringing();
        assertThrows(ApiException.class, () -> service.cancel(3L, id));
        service.cancel(2L, id);
        assertEquals("MISSED", stored.values().iterator().next().getStatus());
        verify(notifications).notify(eq(3L), eq("MISSED_CALL"), any(), any(), any(), any());
    }

    // ---- signalling authorisation
    @Test void signalsAreRefusedUntilTheCallIsAnswered() {
        String id = ringing();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.signal(2L, id, "offer", "{}")).getStatus());
    }
    @Test void outsidersCannotInjectOrReadSignalsAndLearnNothing() {
        String id = activeCall();
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.signal(9L, id, "ice", "{}")).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.signal(9L, "no-such-call", "ice", "{}")).getStatus());
        verify(chat, never()).broadcast(any(), eq("CALL_SIGNAL"), any());
    }
    @Test void offerFromCallerAndAnswerFromCalleeAreRelayedToTheOtherSideOnly() {
        String id = activeCall();
        service.signal(2L, id, "offer", "{\"sdp\":\"x\"}");
        verify(chat).broadcast(eq(List.of(3L)), eq("CALL_SIGNAL"), argThat(o -> o instanceof Map<?, ?> m && "offer".equals(m.get("kind")) && id.equals(m.get("callId"))));
        service.signal(3L, id, "answer", "{\"sdp\":\"y\"}");
        verify(chat).broadcast(eq(List.of(2L)), eq("CALL_SIGNAL"), argThat(o -> o instanceof Map<?, ?> m && "answer".equals(m.get("kind"))));
        service.signal(2L, id, "ice", "{}"); service.signal(3L, id, "ice", "{}");
        verify(chat, times(2)).broadcast(any(), eq("CALL_SIGNAL"), argThat(o -> o instanceof Map<?, ?> m && "ice".equals(m.get("kind"))));
    }
    @Test void roleRulesForOfferAndAnswerAreEnforced() {
        String id = activeCall();
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.signal(3L, id, "offer", "{}")).getStatus());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.signal(2L, id, "answer", "{}")).getStatus());
    }
    @Test void malformedSignalsRejected() {
        String id = activeCall();
        assertThrows(ApiException.class, () -> service.signal(2L, id, "script", "{}"));
        assertThrows(ApiException.class, () -> service.signal(2L, id, "ice", ""));
        assertThrows(ApiException.class, () -> service.signal(2L, id, "ice", "x".repeat(20_000)));
    }

    // ---- ending
    @Test void hangUpCompletesOnceAndRepeatsAreIgnored() {
        String id = activeCall();
        service.end(2L, id, false);
        service.end(2L, id, false); // duplicate signal
        service.end(3L, id, false);
        CallRecord r = stored.values().iterator().next();
        assertEquals("COMPLETED", r.getStatus());
        assertNotNull(r.getDurationSeconds());
        verify(chat, times(1)).broadcast(eq(List.of(2L, 3L)), eq("CALL_ENDED"), any());
        verify(calls, times(3)).save(any()); // create, accept, finish: no duplicate history rows
    }
    @Test void clientReportedFailureIsRecordedAsFailed() {
        String id = activeCall();
        service.end(3L, id, true);
        assertEquals("FAILED", stored.values().iterator().next().getStatus());
    }
    @Test void hangingUpWhileRingingActsAsCancelOrDecline() {
        String id = ringing();
        service.end(2L, id, false);
        assertEquals("MISSED", stored.values().iterator().next().getStatus());
    }

    // ---- sweep: ring timeout and lost connections
    @Test void unansweredCallsTimeOutAsMissed() {
        ringing();
        service.sweep(Instant.now().plus(Duration.ofSeconds(60)));
        CallRecord r = stored.values().iterator().next();
        assertEquals("MISSED", r.getStatus());
        assertEquals("timeout", r.getEndReason());
    }
    @Test void aCallStillRingingInsideTheWindowSurvivesTheSweep() {
        ringing();
        service.sweep(Instant.now().plus(Duration.ofSeconds(10)));
        assertEquals("RINGING", stored.values().iterator().next().getStatus());
    }
    @Test void aParticipantWhoStaysOfflineBreaksTheCallAfterTheGracePeriod() {
        String id = activeCall();
        when(presence.isOnline(2L)).thenReturn(true);
        when(presence.isOnline(3L)).thenReturn(false);
        Instant t0 = Instant.now();
        service.sweep(t0);
        service.sweep(t0.plus(Duration.ofSeconds(5)));
        assertEquals("ACTIVE", stored.values().iterator().next().getStatus()); // short drops are tolerated
        service.sweep(t0.plus(Duration.ofSeconds(20)));
        CallRecord r = stored.values().iterator().next();
        assertEquals("FAILED", r.getStatus());
        assertEquals("disconnected", r.getEndReason());
    }

    // ---- history
    @Test void historyIsScopedToTheUserAndFlagsMissedIncomingCalls() {
        CallRecord missed = new CallRecord(); missed.setId(5L); missed.setCallUid("u1"); missed.setCallerId(3L); missed.setCalleeId(2L); missed.setConversationId(1L); missed.setType("AUDIO"); missed.setStatus("MISSED"); missed.setStartedAt(Instant.now());
        CallRecord out = new CallRecord(); out.setId(4L); out.setCallUid("u2"); out.setCallerId(2L); out.setCalleeId(3L); out.setConversationId(1L); out.setType("VIDEO"); out.setStatus("COMPLETED"); out.setDurationSeconds(61); out.setStartedAt(Instant.now());
        when(calls.history(eq(2L), anyLong(), any(Pageable.class))).thenReturn(List.of(missed, out));
        User peer = new User(); peer.setId(3L); peer.setName("Bob");
        when(users.findAllById(any())).thenReturn(List.of(peer));
        var h = service.history(2L, null, 30);
        assertEquals("INCOMING", h.get(0).direction()); assertTrue(h.get(0).missed()); assertEquals("Bob", h.get(0).peerName());
        assertEquals("OUTGOING", h.get(1).direction()); assertFalse(h.get(1).missed()); assertEquals(61, h.get(1).durationSeconds());
    }
    @Test void missedCountAndSeenUseTheCallersId() {
        when(calls.unseenMissed(2L)).thenReturn(3L);
        assertEquals(3L, service.missedCount(2L));
        service.markSeen(2L);
        verify(calls).markSeen(2L);
    }
}