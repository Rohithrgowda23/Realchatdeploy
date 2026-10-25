package com.realchat.service;

import com.realchat.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window limiter kept in memory (one backend instance). Limits are per key and expire by themselves, so a
 * legitimate user behind a shared IP is slowed down, never locked out for good. Keys use the socket address, not
 * X-Forwarded-For, so a client cannot dodge limits by spoofing headers. Behind a reverse proxy, configure Tomcat's
 * RemoteIpValve / server.forward-headers-strategy for that proxy only. For several instances, swap the map for Redis.
 */
@Service
public class RateLimitService {
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final boolean enabled;
    private final int loginPerIp, loginPerEmail, registerPerIp, forgotPerIp, forgotPerEmail, resetPerIp, messagesPerMinute, passwordChangePerUser;

    public RateLimitService(@Value("${app.rate-limit.enabled:true}") boolean enabled,
                            @Value("${app.rate-limit.login-per-ip:30}") int loginPerIp,
                            @Value("${app.rate-limit.login-per-email:10}") int loginPerEmail,
                            @Value("${app.rate-limit.register-per-ip:10}") int registerPerIp,
                            @Value("${app.rate-limit.forgot-per-ip:10}") int forgotPerIp,
                            @Value("${app.rate-limit.forgot-per-email:3}") int forgotPerEmail,
                            @Value("${app.rate-limit.reset-per-ip:10}") int resetPerIp,
                            @Value("${app.rate-limit.messages-per-minute:60}") int messagesPerMinute,
                            @Value("${app.rate-limit.password-change-per-user:5}") int passwordChangePerUser) {
        this.enabled = enabled; this.loginPerIp = loginPerIp; this.loginPerEmail = loginPerEmail; this.registerPerIp = registerPerIp;
        this.forgotPerIp = forgotPerIp; this.forgotPerEmail = forgotPerEmail; this.resetPerIp = resetPerIp;
        this.messagesPerMinute = messagesPerMinute; this.passwordChangePerUser = passwordChangePerUser;
    }

    public void login(String ip, String email) {
        check("login:ip:" + ip, loginPerIp, Duration.ofMinutes(15));
        check("login:email:" + email, loginPerEmail, Duration.ofMinutes(15));
    }
    public void register(String ip) { check("register:ip:" + ip, registerPerIp, Duration.ofHours(1)); }
    /** Counted whether or not the account exists, so the 429 reveals nothing about the email. */
    public void forgotPassword(String ip, String email) {
        check("forgot:ip:" + ip, forgotPerIp, Duration.ofHours(1));
        check("forgot:email:" + email.strip().toLowerCase(), forgotPerEmail, Duration.ofHours(1));
    }
    public void resetPassword(String ip) { check("reset:ip:" + ip, resetPerIp, Duration.ofHours(1)); }
    public void sendMessage(Long userId) { check("msg:user:" + userId, messagesPerMinute, Duration.ofMinutes(1)); }
    public void passwordChange(Long userId) { check("pwchange:user:" + userId, passwordChangePerUser, Duration.ofMinutes(15)); }

    public void check(String key, int max, Duration window) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < now - windowMs) q.pollFirst();
            if (q.size() >= max) {
                long retry = Math.max(1, (q.peekFirst() + windowMs - now + 999) / 1000);
                throw ApiException.tooManyRequests("Too many requests. Please try again in " + retry + " seconds.");
            }
            q.addLast(now);
        }
        if (calls.incrementAndGet() % 1000 == 0) purge(now);
    }

    private void purge(long now) {
        hits.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty() || e.getValue().peekLast() < now - 3_600_000L; } });
    }
}