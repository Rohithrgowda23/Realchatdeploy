package com.realchat;

import com.realchat.domain.ChatMessage;
import com.realchat.domain.ConversationMember;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import com.realchat.service.ChatService;
import com.realchat.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceAccessTest {
    @Mock UserRepository users;
    @Mock ConversationRepository convs;
    @Mock MemberRepository members;
    @Mock MessageRepository messages;
    @Mock ReactionRepository reactions;
    @Mock BlockRepository blocks;
    @Mock SavedRepository saved;
    @Mock HiddenRepository hidden;
    @Mock AuditRepository audits;
    @Mock SimpMessagingTemplate ws;
    @Mock PresenceService presence;
    @Mock com.realchat.service.RateLimitService limiter;
    @Mock com.realchat.service.AppMetrics metrics;
    @Mock AttachmentRepository attachments;
    @Mock MentionRepository mentions;
    @Mock com.realchat.service.NotificationService notifications;
    @Mock TranslationRepository translations;
    @Mock com.realchat.service.FileStorage storage;
    @InjectMocks ChatService chat;

    private static ConversationMember member(Long conv, Long user, String role) {
        ConversationMember m = new ConversationMember();
        m.setConversationId(conv); m.setUserId(user); m.setRole(role);
        return m;
    }

    @Test void nonMemberCannotReadHistory() {
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> chat.history(1L, 9L, null, 30));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        verifyNoInteractions(messages);
    }

    @Test void nonMemberCannotSend() {
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> chat.send(1L, 9L, "hi", null));
        verify(messages, never()).save(any());
    }

    @Test void plainMemberCannotAddMembers() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "MEMBER")));
        ApiException e = assertThrows(ApiException.class, () -> chat.addMembers(1L, 2L, List.of(3L)));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test void adminCannotChangeRoles() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "ADMIN")));
        when(convs.findById(1L)).thenReturn(Optional.of(groupConversation()));
        ApiException e = assertThrows(ApiException.class, () -> chat.setRole(1L, 2L, 3L, "ADMIN"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test void adminCannotRemoveAdmin() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "ADMIN")));
        when(members.findByConversationIdAndUserId(1L, 3L)).thenReturn(Optional.of(member(1L, 3L, "ADMIN")));
        when(convs.findById(1L)).thenReturn(Optional.of(groupConversation()));
        ApiException e = assertThrows(ApiException.class, () -> chat.removeMember(1L, 2L, 3L));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
    }

    @Test void onlyOwnerCanTransferOwnership() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "ADMIN")));
        when(convs.findById(1L)).thenReturn(Optional.of(groupConversation()));
        assertThrows(ApiException.class, () -> chat.transferOwnership(1L, 2L, 3L));
    }

    @Test void cannotChatWithYourself() {
        assertThrows(ApiException.class, () -> chat.createDirect(5L, 5L));
    }

    private static ChatMessage message(Long id, Long conv, Long sender) {
        ChatMessage m = new ChatMessage();
        m.setId(id); m.setConversationId(conv); m.setSenderId(sender); m.setContent("hello");
        m.setCreatedAt(java.time.Instant.now());
        return m;
    }

    @Test void announcementModeBlocksPlainMembersOnTheServer() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "MEMBER")));
        com.realchat.domain.Conversation c = groupConversation();
        c.setAnnouncementOnly(true);
        when(convs.findById(1L)).thenReturn(Optional.of(c));
        ApiException e = assertThrows(ApiException.class, () -> chat.send(1L, 2L, "hi", null));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        verify(messages, never()).save(any());
    }

    @Test void plainMemberCannotToggleAnnouncementMode() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "MEMBER")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> chat.setAnnouncement(1L, 2L, true)).getStatus());
    }

    @Test void onlyAuthorCanEdit() {
        when(messages.findById(5L)).thenReturn(Optional.of(message(5L, 1L, 7L)));
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "ADMIN")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> chat.edit(5L, 2L, "changed")).getStatus());
    }

    @Test void plainMemberCannotDeleteOthersMessages() {
        when(messages.findById(5L)).thenReturn(Optional.of(message(5L, 1L, 7L)));
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "MEMBER")));
        when(convs.findById(1L)).thenReturn(Optional.of(groupConversation()));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> chat.delete(5L, 2L)).getStatus());
    }

    @Test void cannotForwardFromConversationYouAreNotIn() {
        when(messages.findById(5L)).thenReturn(Optional.of(message(5L, 1L, 7L)));
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> chat.forward(5L, 9L, 2L, null)).getStatus());
        verify(messages, never()).save(any());
    }

    @Test void cannotSaveMessageFromForeignConversation() {
        when(messages.findById(5L)).thenReturn(Optional.of(message(5L, 1L, 7L)));
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> chat.save(5L, 9L));
        verify(saved, never()).save(any());
    }

    @Test void searchRequiresMembershipForTheChosenConversation() {
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> chat.searchAll(9L, "hello", 1L, null, null, null, 0, 20));
        verify(messages, never()).searchAll(any(), any(), any(), any(), any(), any(), any());
    }

    @Test void emptySearchWithoutFiltersIsRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> chat.searchAll(9L, "  ", null, null, null, null, 0, 20)).getStatus());
    }

    @Test void cannotMentionSomeoneWhoIsNotInTheConversation() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "MEMBER")));
        when(members.memberIds(1L)).thenReturn(List.of(2L, 3L));
        ApiException e = assertThrows(ApiException.class, () -> chat.send(1L, 2L, "hi @stranger", null, List.of(99L)));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        verify(messages, never()).save(any());
        verify(mentions, never()).save(any());
    }

    @Test void nonMemberCannotMentionAnyoneAndLearnsNothing() {
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> chat.send(1L, 9L, "hi", null, List.of(2L))).getStatus());
        verify(members, never()).memberIds(any());
    }

    @Test void nonMemberCannotListMentions() {
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> chat.mentionsOfMe(1L, 9L, null, 30));
        verifyNoInteractions(mentions);
    }

    @Test void onlyAdminsCanReadAuditLog() {
        when(members.findByConversationIdAndUserId(1L, 2L)).thenReturn(Optional.of(member(1L, 2L, "MEMBER")));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> chat.auditLog(1L, 2L, null, 0, 30)).getStatus());
    }

    private static com.realchat.domain.Conversation groupConversation() {
        com.realchat.domain.Conversation c = new com.realchat.domain.Conversation();
        c.setType("GROUP");
        return c;
    }
}