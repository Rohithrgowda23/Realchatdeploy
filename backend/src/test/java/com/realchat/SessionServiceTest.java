package com.realchat;

import com.realchat.domain.UserSession;
import com.realchat.exception.ApiException;
import com.realchat.repository.SessionRepository;
import com.realchat.security.JwtService;
import com.realchat.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {
    @Mock SessionRepository repo;
    @InjectMocks SessionService sessions;

    private static UserSession session(Long user, boolean revoked, Instant expires) {
        UserSession s = new UserSession();
        s.setSessionId("sid-1"); s.setUserId(user); s.setRevoked(revoked); s.setExpiresAt(expires);
        s.setLastSeenAt(Instant.now());
        return s;
    }

    @Test void activeSessionValidates() {
        when(repo.findBySessionId("sid-1")).thenReturn(Optional.of(session(1L, false, Instant.now().plusSeconds(600))));
        assertTrue(sessions.validate("sid-1", 1L));
    }
    @Test void revokedSessionRejected() {
        when(repo.findBySessionId("sid-1")).thenReturn(Optional.of(session(1L, true, Instant.now().plusSeconds(600))));
        assertFalse(sessions.validate("sid-1", 1L));
    }
    @Test void expiredSessionRejected() {
        when(repo.findBySessionId("sid-1")).thenReturn(Optional.of(session(1L, false, Instant.now().minusSeconds(5))));
        assertFalse(sessions.validate("sid-1", 1L));
    }
    @Test void sessionOfAnotherUserRejected() {
        when(repo.findBySessionId("sid-1")).thenReturn(Optional.of(session(1L, false, Instant.now().plusSeconds(600))));
        assertFalse(sessions.validate("sid-1", 2L));
    }
    @Test void tokenWithoutSessionIdRejected() {
        assertFalse(sessions.validate(null, 1L));
        verifyNoInteractions(repo);
    }
    @Test void cannotRevokeSomeoneElsesSession() {
        when(repo.findBySessionId("sid-1")).thenReturn(Optional.of(session(1L, false, Instant.now().plusSeconds(600))));
        assertThrows(ApiException.class, () -> sessions.revoke(2L, "sid-1"));
        verify(repo, never()).save(any());
    }
    @Test void ownerCanRevokeOwnSession() {
        UserSession s = session(1L, false, Instant.now().plusSeconds(600));
        when(repo.findBySessionId("sid-1")).thenReturn(Optional.of(s));
        sessions.revoke(1L, "sid-1");
        assertTrue(s.isRevoked());
    }
    @Test void jwtCarriesSessionId() {
        JwtService j = new JwtService("unit-test-secret-unit-test-secret-1234", 60);
        JwtService.TokenInfo t = j.parse(j.generate(9L, "Ann", "abc")).orElseThrow();
        assertEquals(9L, t.userId());
        assertEquals("abc", t.sid());
    }
}
