package com.realchat;

import com.realchat.domain.*;
import com.realchat.dto.Dto.MessageDto;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import com.realchat.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledMessageServiceTest {
    @Mock ScheduledRepository repo;
    @Mock MemberRepository members;
    @Mock ConversationRepository convs;
    @Mock UserRepository users;
    @Mock ChatService chat;
    @Mock NotificationService notifications;
    @Mock RateLimitService limiter;
    ScheduledMessageService service;

    @BeforeEach void setUp() { service = new ScheduledMessageService(repo, members, convs, users, chat, notifications, limiter); }

    private static ConversationMember member(String role) { ConversationMember m = new ConversationMember(); m.setConversationId(1L); m.setUserId(2L); m.setRole(role); return m; }
    private static Conversation conv(boolean announcement) { Conversation c = new Conversation(); c.setId(1L); c.setType("GROUP"); c.setName("Team"); c.setAnnouncementOnly(announcement); return c; }
    private static Instant in(Duration d) { return Instant.now().plus(d); }

    private void asMember() { when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member("MEMBER"))); when(convs.findById(1L)).thenReturn(Optional.of(conv(false))); }

    private ScheduledMessage row(String status) {
        ScheduledMessage s = new ScheduledMessage(); s.setId(5L); s.setConversationId(1L); s.setSenderId(2L); s.setContent("hello"); s.setStatus(status); s.setScheduledAt(in(Duration.ofHours(1)));
        return s;
    }

    @Test void schedulesInTheFutureAndKeepsTheExactInstant() {
        asMember();
        when(repo.save(any(ScheduledMessage.class))).thenAnswer(i -> { ScheduledMessage s = i.getArgument(0); s.setId(5L); return s; });
        Instant at = Instant.parse("2099-01-01T04:30:00Z");
        // the same moment written with an offset (e.g. a +05:30 browser) is the same instant: no zone shift on the server
        assertEquals(at, Instant.parse("2099-01-01T10:00:00+05:30"));
        // far-future dates are rejected, so use a valid near one for the stored instant check
        Instant ok = in(Duration.ofDays(2));
        var dto = service.create(1L, 2L, "  hi  ", null, ok);
        assertEquals(ok, dto.scheduledAt());
        assertEquals("PENDING", dto.status());
        assertEquals("hi", dto.content());
    }

    @Test void pastTooSoonAndTooFarAreRejected() {
        asMember();
        assertThrows(ApiException.class, () -> service.create(1L, 2L, "hi", null, in(Duration.ofSeconds(-5))));
        assertThrows(ApiException.class, () -> service.create(1L, 2L, "hi", null, in(Duration.ofSeconds(5))));
        assertThrows(ApiException.class, () -> service.create(1L, 2L, "hi", null, in(Duration.ofDays(400))));
        assertThrows(ApiException.class, () -> service.create(1L, 2L, "hi", null, null));
        verify(repo, never()).save(any());
    }

    @Test void emptyMessageRejected() {
        asMember();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.create(1L, 2L, "   ", null, in(Duration.ofHours(1)))).getStatus());
    }

    @Test void nonMemberCannotSchedule() {
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.create(1L, 9L, "hi", null, in(Duration.ofHours(1)))).getStatus());
    }

    @Test void announcementOnlyBlocksPlainMembers() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member("MEMBER")));
        when(convs.findById(1L)).thenReturn(Optional.of(conv(true)));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.create(1L, 2L, "hi", null, in(Duration.ofHours(1)))).getStatus());
    }

    @Test void tooManyPendingRejected() {
        asMember();
        when(repo.countBySenderIdAndStatus(2L, "PENDING")).thenReturn(50L);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.create(1L, 2L, "hi", null, in(Duration.ofHours(1)))).getStatus());
    }

    @Test void onlyTheOwnerCanEditOrCancel() {
        when(repo.findById(5L)).thenReturn(Optional.of(row("PENDING")));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.cancel(5L, 9L)).getStatus());
        assertThrows(ApiException.class, () -> service.update(5L, 9L, "x", null));
    }

    @Test void sentOrBeingSentMessagesCannotBeEditedOrCancelled() {
        for (String st : List.of("SENT", "SENDING", "CANCELLED", "FAILED")) {
            when(repo.findById(5L)).thenReturn(Optional.of(row(st)));
            assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.cancel(5L, 2L)).getStatus());
            assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.update(5L, 2L, "x", null)).getStatus());
        }
    }

    @Test void cancelMarksCancelled() {
        ScheduledMessage s = row("PENDING");
        when(repo.findById(5L)).thenReturn(Optional.of(s));
        service.cancel(5L, 2L);
        assertEquals("CANCELLED", s.getStatus());
        verify(repo).saveAndFlush(s);
    }

    @Test void onlyRowsWonByTheAtomicClaimAreDelivered() {
        when(repo.dueIds(any(), any(Pageable.class))).thenReturn(List.of(1L, 2L, 3L));
        when(repo.claim(eq(1L), any())).thenReturn(1);
        when(repo.claim(eq(2L), any())).thenReturn(0); // another scheduler instance got it
        when(repo.claim(eq(3L), any())).thenReturn(1);
        assertEquals(List.of(1L, 3L), service.claimDue(50));
    }

    private MessageDto sent() { return new MessageDto(99L, 1L, 2L, "A", "hello", null, false, false, Instant.now(), Map.of(), null, null, List.of()); }

    @Test void deliverSendsThroughTheNormalRulesAndMarksSent() {
        ScheduledMessage s = row("SENDING");
        when(repo.findById(5L)).thenReturn(Optional.of(s));
        when(chat.send(1L, 2L, "hello", null)).thenReturn(sent());
        service.deliver(5L);
        assertEquals("SENT", s.getStatus());
        assertEquals(99L, s.getSentMessageId());
        verify(chat, times(1)).send(1L, 2L, "hello", null);
    }

    @Test void deliverIgnoresRowsNotInSendingState() {
        when(repo.findById(5L)).thenReturn(Optional.of(row("SENT")));
        service.deliver(5L);
        verifyNoInteractions(chat);
    }

    @Test void permissionFailureFailsImmediatelyAndNotifiesTheSender() {
        ScheduledMessage s = row("SENDING");
        when(repo.findById(5L)).thenReturn(Optional.of(s));
        service.recordFailure(5L, ApiException.notFound("Conversation not found")); // e.g. the sender was removed from the group
        assertEquals("FAILED", s.getStatus());
        verify(notifications).notify(eq(2L), eq("SCHEDULED_FAILED"), any(), eq("Conversation not found"), eq(1L), isNull());
    }

    @Test void transientFailureRetriesLaterThenGivesUp() {
        ScheduledMessage s = row("SENDING");
        when(repo.findById(5L)).thenReturn(Optional.of(s));
        Instant before = Instant.now();
        service.recordFailure(5L, new RuntimeException("db down"));
        assertEquals("PENDING", s.getStatus());
        assertEquals(1, s.getAttempts());
        assertTrue(s.getScheduledAt().isAfter(before));
        verifyNoInteractions(notifications);
        s.setStatus("SENDING"); s.setAttempts(2);
        service.recordFailure(5L, ApiException.tooManyRequests("slow down"));
        assertEquals("FAILED", s.getStatus());
        verify(notifications).notify(eq(2L), eq("SCHEDULED_FAILED"), any(), any(), any(), any());
    }
}