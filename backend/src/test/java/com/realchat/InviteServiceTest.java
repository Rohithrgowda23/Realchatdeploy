package com.realchat;

import com.realchat.domain.*;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import com.realchat.service.ChatService;
import com.realchat.service.InviteService;
import com.realchat.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {
    @Mock InviteRepository invites;
    @Mock JoinRequestRepository requests;
    @Mock MemberRepository members;
    @Mock ConversationRepository convs;
    @Mock UserRepository users;
    @Mock ChatService chat;
    @Mock RateLimitService limiter;
    @Mock com.realchat.service.NotificationService notifications;
    InviteService service;

    private static final String TOKEN = "t".repeat(43);

    @BeforeEach void setUp() { service = new InviteService(invites, requests, members, convs, users, chat, limiter, notifications); }

    private static String sha(String s) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    private static ConversationMember member(Long conv, Long user, String role) { ConversationMember m = new ConversationMember(); m.setConversationId(conv); m.setUserId(user); m.setRole(role); return m; }
    private static Conversation group() { Conversation c = new Conversation(); c.setId(1L); c.setType("GROUP"); c.setName("Team"); return c; }
    private GroupInvite invite(boolean revoked, Instant expires, Integer max, int used, boolean approval) throws Exception {
        GroupInvite i = new GroupInvite(); i.setId(7L); i.setConversationId(1L); i.setTokenHash(sha(TOKEN)); i.setRevoked(revoked); i.setExpiresAt(expires);
        i.setMaxUses(max); i.setUseCount(used); i.setRequiresApproval(approval); return i;
    }
    private void givenInvite(GroupInvite i) throws Exception { when(invites.findByTokenHash(sha(TOKEN))).thenReturn(Optional.of(i)); }

    @Test void plainMemberCannotCreateInvite() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "MEMBER")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.create(1L, 2L, 24, 5, false)).getStatus());
        verify(invites, never()).save(any());
    }

    @Test void tokenIsReturnedOnceAndOnlyItsHashIsStored() throws Exception {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "ADMIN")));
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        when(invites.save(any(GroupInvite.class))).thenAnswer(i -> { GroupInvite g = i.getArgument(0); g.setId(7L); return g; });
        InviteService.CreatedInvite created = service.create(1L, 2L, 24, 5, true);
        ArgumentCaptor<GroupInvite> saved = ArgumentCaptor.forClass(GroupInvite.class);
        verify(invites).save(saved.capture());
        assertTrue(created.token().length() >= 40);
        assertNotEquals(created.token(), saved.getValue().getTokenHash());
        assertEquals(sha(created.token()), saved.getValue().getTokenHash());
        assertNotNull(saved.getValue().getExpiresAt());
        verify(chat).audit(eq(1L), eq(2L), eq("INVITE_CREATED"), isNull(), isNull());
    }

    @Test void invalidExpiryOrUsesRejected() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "OWNER")));
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        assertThrows(ApiException.class, () -> service.create(1L, 2L, 0, null, false));
        assertThrows(ApiException.class, () -> service.create(1L, 2L, null, 0, false));
    }

    @Test void unknownRevokedExpiredAndUsedUpTokensAllLookTheSame() throws Exception {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.join("short", 5L)).getStatus());
        when(invites.findByTokenHash(sha(TOKEN))).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.join(TOKEN, 5L)).getStatus());
        for (GroupInvite bad : List.of(invite(true, null, null, 0, false), invite(false, Instant.now().minusSeconds(5), null, 0, false), invite(false, null, 2, 2, false))) {
            givenInvite(bad);
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.join(TOKEN, 5L)).getStatus());
        }
        verify(chat, never()).addMember(any(), any(), any());
    }

    @Test void joiningAddsPlainMemberNeverAdmin() throws Exception {
        givenInvite(invite(false, null, 10, 0, false));
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        when(members.findByConversationIdAndUserId(1L, 5L)).thenReturn(Optional.empty());
        when(invites.consume(eq(7L), any())).thenReturn(1);
        assertEquals("JOINED", service.join(TOKEN, 5L).status());
        verify(chat).addMember(1L, 5L, "MEMBER");
        verify(chat, never()).addMember(any(), any(), eq("ADMIN"));
    }

    @Test void lastUseTakenByAnotherJoinerMeansNoMembership() throws Exception {
        givenInvite(invite(false, null, 1, 0, false));
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        when(members.findByConversationIdAndUserId(1L, 5L)).thenReturn(Optional.empty());
        when(invites.consume(eq(7L), any())).thenReturn(0); // lost the race
        assertThrows(ApiException.class, () -> service.join(TOKEN, 5L));
        verify(chat, never()).addMember(any(), any(), any());
    }

    @Test void existingMemberDoesNotUseUpTheInvite() throws Exception {
        givenInvite(invite(false, null, 1, 0, false));
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        when(members.findByConversationIdAndUserId(1L, 5L)).thenReturn(Optional.of(member(1L, 5L, "MEMBER")));
        assertEquals("ALREADY_MEMBER", service.join(TOKEN, 5L).status());
        verify(invites, never()).consume(any(), any());
    }

    @Test void approvalInviteCreatesPendingRequestNotMembership() throws Exception {
        givenInvite(invite(false, null, null, 0, true));
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        when(members.findByConversationIdAndUserId(1L, 5L)).thenReturn(Optional.empty());
        when(requests.existsByConversationIdAndUserIdAndStatus(1L, 5L, "PENDING")).thenReturn(false);
        when(invites.consume(eq(7L), any())).thenReturn(1);
        assertEquals("PENDING", service.join(TOKEN, 5L).status());
        verify(requests).save(any(JoinRequest.class));
        verify(chat, never()).addMember(any(), any(), any());
    }

    @Test void duplicatePendingRequestIsNotCreatedTwice() throws Exception {
        givenInvite(invite(false, null, null, 0, true));
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        when(members.findByConversationIdAndUserId(1L, 5L)).thenReturn(Optional.empty());
        when(requests.existsByConversationIdAndUserIdAndStatus(1L, 5L, "PENDING")).thenReturn(true);
        assertEquals("PENDING", service.join(TOKEN, 5L).status());
        verify(requests, never()).save(any());
        verify(invites, never()).consume(any(), any());
    }

    @Test void plainMemberCannotDecideRequests() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "MEMBER")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.decide(1L, 2L, 9L, true)).getStatus());
        verify(requests, never()).decide(any(), any(), any(), any());
    }

    private JoinRequest request(Long conv) { JoinRequest r = new JoinRequest(); r.setId(9L); r.setConversationId(conv); r.setUserId(5L); r.setStatus("PENDING"); return r; }

    @Test void approvalAddsMemberOnceAndAlreadyHandledRequestIsConflict() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "ADMIN")));
        when(members.findByConversationIdAndUserId(1L, 5L)).thenReturn(Optional.empty());
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        when(requests.findById(9L)).thenReturn(Optional.of(request(1L)));
        when(users.findById(5L)).thenReturn(Optional.of(new User()));
        when(requests.decide(eq(9L), eq("APPROVED"), eq(2L), any())).thenReturn(1, 0);
        service.decide(1L, 2L, 9L, true);
        verify(chat).addMember(1L, 5L, "MEMBER");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class, () -> service.decide(1L, 2L, 9L, true)).getStatus());
        verify(chat, times(1)).addMember(any(), any(), any());
    }

    @Test void rejectionDoesNotAddMember() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "OWNER")));
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        when(requests.findById(9L)).thenReturn(Optional.of(request(1L)));
        when(users.findById(5L)).thenReturn(Optional.of(new User()));
        when(requests.decide(eq(9L), eq("REJECTED"), eq(2L), any())).thenReturn(1);
        service.decide(1L, 2L, 9L, false);
        verify(chat, never()).addMember(any(), any(), any());
        verify(chat).audit(1L, 2L, "JOIN_REJECTED", 5L, null);
    }

    @Test void requestFromAnotherGroupCannotBeDecidedHere() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "OWNER")));
        when(convs.findById(1L)).thenReturn(Optional.of(group()));
        when(requests.findById(9L)).thenReturn(Optional.of(request(99L)));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.decide(1L, 2L, 9L, true)).getStatus());
    }

    @Test void revokingRejectsRequestsThatCameThroughThatLink() throws Exception {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "ADMIN")));
        GroupInvite i = invite(false, null, null, 0, true);
        when(invites.findById(7L)).thenReturn(Optional.of(i));
        service.revoke(1L, 2L, 7L);
        assertTrue(i.isRevoked());
        verify(requests).rejectPendingForInvite(eq(7L), any());
    }
}