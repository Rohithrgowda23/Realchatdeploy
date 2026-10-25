package com.realchat.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-use codes. They keep the RealChat JWT out of redirect URLs:
 *  - login code: issued after Google sign-in, exchanged once by the SPA for a real JWT (60 s)
 *  - link code: issued to a signed-in user who wants to connect Google, consumed once by the browser redirect (5 min)
 * In memory = single backend instance. For several instances move this to the database or Redis.
 */
@Component
public class OneTimeCodeStore {
    private record Entry(Long userId, Instant expires) {}

    private final Map<String, Entry> login = new ConcurrentHashMap<>();
    private final Map<String, Entry> link = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issueLogin(Long userId) { return issue(login, userId, Duration.ofSeconds(60)); }
    public String issueLogin(Long userId, Duration ttl) { return issue(login, userId, ttl); }
    public Optional<Long> consumeLogin(String code) { return consume(login, code); }

    public String issueLink(Long userId) { return issue(link, userId, Duration.ofMinutes(5)); }
    public Optional<Long> consumeLink(String code) { return consume(link, code); }

    private String issue(Map<String, Entry> map, Long userId, Duration ttl) {
        Instant now = Instant.now();
        map.values().removeIf(e -> e.expires().isBefore(now));
        byte[] b = new byte[32];
        random.nextBytes(b);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        map.put(code, new Entry(userId, now.plus(ttl)));
        return code;
    }

    /** remove() is atomic, so a code can only ever be used once. */
    private Optional<Long> consume(Map<String, Entry> map, String code) {
        if (code == null) return Optional.empty();
        Entry e = map.remove(code);
        if (e == null || e.expires().isBefore(Instant.now())) return Optional.empty();
        return Optional.of(e.userId());
    }
}