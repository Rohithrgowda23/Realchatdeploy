package com.realchat;

import com.realchat.exception.ApiException;
import com.realchat.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {
    private RateLimitService limiter(boolean enabled) { return new RateLimitService(enabled, 30, 10, 10, 10, 3, 10, 60, 5); }

    @Test void allowsUpToMaxThenReturns429() {
        RateLimitService l = limiter(true);
        for (int i = 0; i < 3; i++) l.check("k", 3, Duration.ofMinutes(1));
        ApiException e = assertThrows(ApiException.class, () -> l.check("k", 3, Duration.ofMinutes(1)));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
    }
    @Test void keysAreIndependent() {
        RateLimitService l = limiter(true);
        l.check("a", 1, Duration.ofMinutes(1));
        assertDoesNotThrow(() -> l.check("b", 1, Duration.ofMinutes(1)));
    }
    @Test void limitExpiresSoNobodyIsLockedOutPermanently() throws Exception {
        RateLimitService l = limiter(true);
        l.check("k", 1, Duration.ofMillis(100));
        assertThrows(ApiException.class, () -> l.check("k", 1, Duration.ofMillis(100)));
        Thread.sleep(180);
        assertDoesNotThrow(() -> l.check("k", 1, Duration.ofMillis(100)));
    }
    @Test void disabledLimiterNeverBlocks() {
        RateLimitService l = limiter(false);
        for (int i = 0; i < 100; i++) l.check("k", 1, Duration.ofMinutes(1));
    }
    @Test void forgotPasswordLimitedPerEmailCaseInsensitively() {
        RateLimitService l = limiter(true);
        l.forgotPassword("1.1.1.1", "A@x.com"); l.forgotPassword("1.1.1.2", "a@X.com"); l.forgotPassword("1.1.1.3", "a@x.com");
        assertThrows(ApiException.class, () -> l.forgotPassword("1.1.1.4", "a@x.com"));
    }
    @Test void loginLimitedPerEmailEvenFromDifferentIps() {
        RateLimitService l = new RateLimitService(true, 100, 2, 10, 10, 3, 10, 60, 5);
        l.login("1.1.1.1", "a@x.com"); l.login("1.1.1.2", "a@x.com");
        assertThrows(ApiException.class, () -> l.login("1.1.1.3", "a@x.com"));
    }
}