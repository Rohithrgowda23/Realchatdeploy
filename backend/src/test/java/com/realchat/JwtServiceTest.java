package com.realchat;

import com.realchat.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    @Test void validTokenRoundTrips() {
        JwtService j = new JwtService(SECRET, 60);
        assertEquals(42L, j.parseUserId(j.generate(42L, "Ann")).orElseThrow());
    }
    @Test void tamperedTokenRejected() {
        JwtService j = new JwtService(SECRET, 60);
        String t = j.generate(1L, "Ann");
        assertTrue(j.parseUserId(t.substring(0, t.length() - 2) + "xx").isEmpty());
        assertTrue(j.parseUserId("garbage").isEmpty());
    }
    @Test void tokenSignedWithOtherKeyRejected() {
        String foreign = new JwtService("another-secret-another-secret-123456", 60).generate(1L, "Ann");
        assertTrue(new JwtService(SECRET, 60).parseUserId(foreign).isEmpty());
    }
    @Test void expiredTokenRejected() {
        JwtService j = new JwtService(SECRET, -1);
        assertTrue(j.parseUserId(j.generate(1L, "Ann")).isEmpty());
    }
    @Test void shortSecretRefused() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short", 60));
    }
    @Test void passwordsAreHashedWithBcrypt() {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        String hash = enc.encode("correct horse");
        assertNotEquals("correct horse", hash);
        assertTrue(hash.startsWith("$2"));
        assertTrue(enc.matches("correct horse", hash));
        assertFalse(enc.matches("wrong", hash));
    }
}
