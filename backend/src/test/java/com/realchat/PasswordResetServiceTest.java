package com.realchat;

import com.realchat.domain.PasswordResetToken;
import com.realchat.domain.User;
import com.realchat.exception.ApiException;
import com.realchat.repository.PasswordResetRepository;
import com.realchat.repository.UserRepository;
import com.realchat.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock UserRepository users;
    @Mock PasswordResetRepository tokens;
    @Mock PasswordEncoder encoder;
    @Mock SessionService sessions;
    @Mock RateLimitService limiter;
    @Mock ResetMailer mailer;
    PasswordResetService service;

    private static final String RAW = "x".repeat(43);

    @BeforeEach void setUp() { service = new PasswordResetService(users, tokens, encoder, sessions, limiter, mailer, "http://localhost:5173", 30); }

    private static String sha(String s) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }

    private static User user() { User u = new User(); u.setId(1L); u.setEmail("a@x.com"); u.setPasswordHash("old"); return u; }

    private PasswordResetToken token(Instant expires, Instant used) throws Exception {
        PasswordResetToken t = new PasswordResetToken();
        t.setId(10L); t.setUserId(1L); t.setTokenHash(sha(RAW)); t.setExpiresAt(expires); t.setUsedAt(used);
        return t;
    }

    @Test void unknownEmailDoesNothingAndDoesNotFail() {
        when(users.findByEmailIgnoreCase("nobody@x.com")).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.request("nobody@x.com", "1.2.3.4"));
        verify(tokens, never()).save(any());
        verifyNoInteractions(mailer);
    }

    @Test void knownEmailStoresOnlyAHashAndMailsTheRawLink() throws Exception {
        when(users.findByEmailIgnoreCase("a@x.com")).thenReturn(Optional.of(user()));
        service.request("a@x.com", "1.2.3.4");
        verify(limiter).forgotPassword("1.2.3.4", "a@x.com");
        verify(tokens).invalidateAllForUser(eq(1L), any()); // older links stop working
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer, timeout(2000)).sendResetLink(eq("a@x.com"), link.capture());
        String raw = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertNotEquals(raw, saved.getValue().getTokenHash());
        assertEquals(sha(raw), saved.getValue().getTokenHash());
        assertTrue(saved.getValue().getExpiresAt().isAfter(Instant.now()));
    }

    @Test void validTokenChangesPasswordOnceAndSignsOutEverywhere() throws Exception {
        PasswordResetToken t = token(Instant.now().plusSeconds(600), null);
        User u = user();
        when(tokens.findByTokenHash(sha(RAW))).thenReturn(Optional.of(t));
        when(tokens.markUsed(eq(10L), any())).thenReturn(1);
        when(users.findById(1L)).thenReturn(Optional.of(u));
        when(encoder.encode("newpassword1")).thenReturn("HASHED");
        service.reset(RAW, "newpassword1", "1.2.3.4");
        assertEquals("HASHED", u.getPasswordHash());
        verify(sessions).revokeOthers(1L, "");
    }

    @Test void expiredTokenRejected() throws Exception {
        when(tokens.findByTokenHash(sha(RAW))).thenReturn(Optional.of(token(Instant.now().minusSeconds(5), null)));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.reset(RAW, "newpassword1", "ip")).getStatus());
        verify(users, never()).save(any());
    }

    @Test void alreadyUsedTokenRejected() throws Exception {
        when(tokens.findByTokenHash(sha(RAW))).thenReturn(Optional.of(token(Instant.now().plusSeconds(600), Instant.now())));
        assertThrows(ApiException.class, () -> service.reset(RAW, "newpassword1", "ip"));
        verify(users, never()).save(any());
    }

    @Test void concurrentReuseLosesTheRace() throws Exception {
        when(tokens.findByTokenHash(sha(RAW))).thenReturn(Optional.of(token(Instant.now().plusSeconds(600), null)));
        when(tokens.markUsed(eq(10L), any())).thenReturn(0);
        assertThrows(ApiException.class, () -> service.reset(RAW, "newpassword1", "ip"));
        verify(users, never()).save(any());
        verify(sessions, never()).revokeOthers(any(), any());
    }

    @Test void garbageTokenRejectedWithoutTouchingTheDatabase() {
        assertThrows(ApiException.class, () -> service.reset("short", "newpassword1", "ip"));
        verify(tokens, never()).findByTokenHash(any());
    }

    @Test void resetAttemptsAreRateLimited() throws Exception {
        when(tokens.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.reset(RAW, "newpassword1", "9.9.9.9"));
        verify(limiter).resetPassword("9.9.9.9");
    }
}