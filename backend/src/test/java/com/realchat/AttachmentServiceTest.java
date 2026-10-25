package com.realchat;

import com.realchat.domain.Attachment;
import com.realchat.domain.ChatMessage;
import com.realchat.domain.ConversationMember;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import com.realchat.service.AttachmentService;
import com.realchat.service.ChatService;
import com.realchat.service.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {
    @Mock FileStorage storage;
    @Mock AttachmentRepository attachments;
    @Mock MessageRepository messages;
    @Mock MemberRepository members;
    @Mock HiddenRepository hidden;
    @Mock UserRepository users;
    @Mock ChatService chat;
    AttachmentService service;

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @BeforeEach void setUp() { service = new AttachmentService(storage, attachments, messages, members, hidden, users, chat, 1024, 512); }

    private void member(Long conv, Long user) {
        ConversationMember m = new ConversationMember(); m.setConversationId(conv); m.setUserId(user); m.setRole("MEMBER");
        when(members.findByConversationIdAndUserId(conv, user)).thenReturn(Optional.of(m));
    }

    @Test void nonMemberCannotUploadAndNothingIsStored() {
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.upload(1L, 9L, new MockMultipartFile("file", "a.png", "image/png", PNG), null, null));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        verifyNoInteractions(storage);
    }
    @Test void oversizedFileRejectedBeforeStoring() {
        member(1L, 2L);
        ApiException e = assertThrows(ApiException.class, () -> service.upload(1L, 2L, new MockMultipartFile("file", "a.png", "image/png", new byte[2000]), null, null));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, e.getStatus());
        verifyNoInteractions(storage);
    }
    @Test void clientClaimedMimeTypeIsIgnored() {
        member(1L, 2L);
        ApiException e = assertThrows(ApiException.class, () -> service.upload(1L, 2L, new MockMultipartFile("file", "a.png", "image/png", "<html>hi</html>".getBytes()), null, null));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getStatus());
        verifyNoInteractions(storage);
    }
    @Test void emptyFileRejected() {
        member(1L, 2L);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.upload(1L, 2L, new MockMultipartFile("file", "a.png", "image/png", new byte[0]), null, null)).getStatus());
    }
    @Test void voiceDurationOutOfRangeRejected() {
        member(1L, 2L);
        assertThrows(ApiException.class, () -> service.uploadVoice(1L, 2L, new MockMultipartFile("file", "v.webm", "audio/webm", PNG), 100, null));
        assertThrows(ApiException.class, () -> service.uploadVoice(1L, 2L, new MockMultipartFile("file", "v.webm", "audio/webm", PNG), 700_000, null));
    }
    @Test void imageCannotBeUploadedAsVoice() {
        member(1L, 2L);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(ApiException.class, () -> service.uploadVoice(1L, 2L, new MockMultipartFile("file", "v.webm", "audio/webm", PNG), 2000, null)).getStatus());
    }
    @Test void rejectedMessageDeletesTheStoredFile() {
        member(1L, 2L);
        when(storage.store(any())).thenReturn("stored-name");
        when(chat.sendWithAttachment(any(), any(), any(), any(), any())).thenThrow(ApiException.forbidden("Only admins can post in this group"));
        assertThrows(ApiException.class, () -> service.upload(1L, 2L, new MockMultipartFile("file", "a.png", "image/png", PNG), null, null));
        verify(storage).deleteQuietly("stored-name");
    }
    @Test void nonMemberCannotDownload() {
        Attachment a = new Attachment(); a.setId(5L); a.setConversationId(1L); a.setMessageId(7L);
        when(attachments.findById(5L)).thenReturn(Optional.of(a));
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.open(5L, 9L)).getStatus());
        verifyNoInteractions(storage);
    }
    @Test void deletedMessageFileIsNotServed() {
        member(1L, 2L);
        Attachment a = new Attachment(); a.setId(5L); a.setConversationId(1L); a.setMessageId(7L);
        ChatMessage m = new ChatMessage(); m.setId(7L); m.setDeleted(true);
        when(attachments.findById(5L)).thenReturn(Optional.of(a));
        when(messages.findById(7L)).thenReturn(Optional.of(m));
        assertThrows(ApiException.class, () -> service.open(5L, 2L));
        verifyNoInteractions(storage);
    }
    @Test void galleryRequiresMembership() {
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.gallery(1L, 9L, "ALL", null, 24));
        verifyNoInteractions(attachments);
    }
}