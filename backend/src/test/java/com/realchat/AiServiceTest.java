package com.realchat;

import com.realchat.ai.AiProvider;
import com.realchat.domain.ChatMessage;
import com.realchat.domain.ConversationMember;
import com.realchat.domain.MessageTranslation;
import com.realchat.domain.User;
import com.realchat.exception.ApiException;
import com.realchat.repository.*;
import com.realchat.service.AiService;
import com.realchat.service.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
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
class AiServiceTest {
    @Mock AiProvider provider;
    @Mock MessageRepository messages;
    @Mock MemberRepository members;
    @Mock HiddenRepository hidden;
    @Mock TranslationRepository translations;
    @Mock UserRepository users;
    @Mock RateLimitService limiter;
    AiService service;

    @BeforeEach void setUp() { service = new AiService(provider, messages, members, hidden, translations, users, limiter, 60, 10); }

    private static String sha(String s) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    private void enabled() { when(provider.available()).thenReturn(true); }
    private void member(Long conv, Long user) { when(members.findByConversationIdAndUserId(conv, user)).thenReturn(Optional.of(new ConversationMember())); }
    private static ChatMessage msg(long id, String text, boolean deleted, long sender, Instant at) {
        ChatMessage m = new ChatMessage(); m.setId(id); m.setConversationId(1L); m.setSenderId(sender); m.setContent(text); m.setDeleted(deleted); m.setCreatedAt(at); return m;
    }

    // ---- translation
    @Test void disabledProviderAnswers503WithoutTouchingData() {
        when(provider.available()).thenReturn(false);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ApiException.class, () -> service.translate(5L, 2L, "es")).getStatus());
        verifyNoInteractions(messages);
    }
    @Test void unsupportedLanguageRejected() {
        enabled();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.translate(5L, 2L, "xx-klingon")).getStatus());
        verify(provider, never()).complete(any(), any());
    }
    @Test void nonMemberCannotTranslateAndNothingIsSentToTheProvider() {
        enabled();
        when(messages.findById(5L)).thenReturn(Optional.of(msg(5, "secret", false, 7, Instant.now())));
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.translate(5L, 9L, "es")).getStatus());
        verify(provider, never()).complete(any(), any());
    }
    @Test void deletedOrHiddenMessagesAreNotTranslated() {
        enabled(); member(1L, 2L);
        when(messages.findById(5L)).thenReturn(Optional.of(msg(5, "", true, 7, Instant.now())));
        assertThrows(ApiException.class, () -> service.translate(5L, 2L, "es"));
        when(messages.findById(6L)).thenReturn(Optional.of(msg(6, "hello", false, 7, Instant.now())));
        when(hidden.existsByUserIdAndMessageId(2L, 6L)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.translate(6L, 2L, "es"));
        verify(provider, never()).complete(any(), any());
    }
    @Test void translatesAndCachesWithoutChangingTheOriginal() {
        enabled(); member(1L, 2L);
        ChatMessage m = msg(5, "hello world", false, 7, Instant.now());
        when(messages.findById(5L)).thenReturn(Optional.of(m));
        when(translations.findByMessageIdAndTargetLang(5L, "es")).thenReturn(Optional.empty());
        when(provider.complete(any(), eq("hello world"))).thenReturn("hola mundo");
        var t = service.translate(5L, 2L, "ES");
        assertEquals("hola mundo", t.translatedText());
        assertTrue(t.aiGenerated());
        assertFalse(t.cached());
        assertEquals("hello world", m.getContent());
        verify(translations).save(argThat(x -> "es".equals(x.getTargetLang()) && "hola mundo".equals(x.getTranslatedText())));
    }
    @Test void freshCacheIsServedWithoutCallingTheProviderOrTheRateLimit() throws Exception {
        enabled(); member(1L, 2L);
        when(messages.findById(5L)).thenReturn(Optional.of(msg(5, "hello world", false, 7, Instant.now())));
        MessageTranslation c = new MessageTranslation(); c.setSourceHash(sha("hello world")); c.setTranslatedText("hola mundo");
        when(translations.findByMessageIdAndTargetLang(5L, "es")).thenReturn(Optional.of(c));
        assertTrue(service.translate(5L, 2L, "es").cached());
        verify(provider, never()).complete(any(), any());
        verifyNoInteractions(limiter);
    }
    @Test void editedMessageInvalidatesTheCache() throws Exception {
        enabled(); member(1L, 2L);
        when(messages.findById(5L)).thenReturn(Optional.of(msg(5, "hello, edited", false, 7, Instant.now())));
        MessageTranslation c = new MessageTranslation(); c.setSourceHash(sha("hello world")); c.setTranslatedText("hola mundo");
        when(translations.findByMessageIdAndTargetLang(5L, "es")).thenReturn(Optional.of(c));
        when(provider.complete(any(), eq("hello, edited"))).thenReturn("hola, editado");
        assertEquals("hola, editado", service.translate(5L, 2L, "es").translatedText());
    }
    @Test void providerFailureIsReportedAndNothingIsCached() {
        enabled(); member(1L, 2L);
        when(messages.findById(5L)).thenReturn(Optional.of(msg(5, "hello", false, 7, Instant.now())));
        when(translations.findByMessageIdAndTargetLang(5L, "es")).thenReturn(Optional.empty());
        when(provider.complete(any(), any())).thenThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT, "The AI provider took too long to answer"));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, assertThrows(ApiException.class, () -> service.translate(5L, 2L, "es")).getStatus());
        verify(translations, never()).save(any());
    }

    // ---- summaries
    @Test void nonMemberCannotSummarizeAndNothingIsSentToTheProvider() {
        enabled();
        when(members.findByConversationIdAndUserId(1L, 9L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.summarize(1L, 9L, 50, null)).getStatus());
        verifyNoInteractions(messages);
        verify(provider, never()).complete(any(), any());
    }
    @Test void tooFewMessagesRejected() {
        enabled(); member(1L, 2L);
        when(messages.page(eq(1L), anyLong(), eq(2L), any(Pageable.class))).thenReturn(List.of(msg(2, "hi", false, 7, Instant.now()), msg(1, "yo", false, 7, Instant.now())));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.summarize(1L, 2L, 50, null)).getStatus());
        verify(provider, never()).complete(any(), any());
    }
    @Test void onlyLiveTextOfThatConversationReachesTheProvider() {
        enabled(); member(1L, 2L);
        Instant t = Instant.now();
        User ann = new User(); ann.setId(7L); ann.setName("Ann"); ann.setEmail("ann@secret.example");
        when(users.findAllById(any())).thenReturn(List.of(ann));
        when(messages.page(eq(1L), anyLong(), eq(2L), any(Pageable.class))).thenReturn(List.of(
                msg(5, "third message", false, 7, t), msg(4, "DELETED CONTENT", true, 7, t), msg(3, "second message", false, 7, t), msg(2, "", false, 7, t), msg(1, "first message", false, 7, t)));
        when(provider.complete(any(), any())).thenReturn("A short summary");
        var s = service.summarize(1L, 2L, 50, null);
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(provider).complete(any(), sent.capture());
        assertTrue(sent.getValue().contains("first message") && sent.getValue().contains("third message"));
        assertFalse(sent.getValue().contains("DELETED CONTENT"));
        assertFalse(sent.getValue().contains("ann@secret.example"));
        assertTrue(sent.getValue().indexOf("first message") < sent.getValue().indexOf("third message")); // chronological
        assertEquals(3, s.messageCount());
        assertTrue(s.aiGenerated());
        verify(limiter).check(eq("ai:summary:2"), eq(10), any());
    }
    @Test void sinceWindowExcludesOlderMessages() {
        enabled(); member(1L, 2L);
        Instant now = Instant.now();
        when(users.findAllById(any())).thenReturn(List.of());
        when(messages.page(eq(1L), anyLong(), eq(2L), any(Pageable.class))).thenReturn(List.of(
                msg(5, "recent c", false, 7, now), msg(4, "recent b", false, 7, now), msg(3, "recent a", false, 7, now), msg(2, "ancient", false, 7, now.minusSeconds(86400L * 3))));
        when(provider.complete(any(), any())).thenReturn("ok");
        service.summarize(1L, 2L, null, 24);
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(provider).complete(any(), sent.capture());
        assertFalse(sent.getValue().contains("ancient"));
    }
    @Test void hugeTranscriptsKeepTheNewestMessagesWithinTheLimit() {
        enabled(); member(1L, 2L);
        Instant t = Instant.now();
        String big = "x".repeat(480);
        java.util.ArrayList<ChatMessage> list = new java.util.ArrayList<>();
        for (int i = 60; i >= 1; i--) list.add(msg(i, (i == 1 ? "OLDEST " : i == 60 ? "NEWEST " : "") + big, false, 7, t));
        when(users.findAllById(any())).thenReturn(List.of());
        when(messages.page(eq(1L), anyLong(), eq(2L), any(Pageable.class))).thenReturn(list);
        when(provider.complete(any(), any())).thenReturn("ok");
        service.summarize(1L, 2L, 200, null);
        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(provider).complete(any(), sent.capture());
        assertTrue(sent.getValue().length() < 13_000);
        assertTrue(sent.getValue().contains("NEWEST"));
        assertFalse(sent.getValue().contains("OLDEST"));
    }
}