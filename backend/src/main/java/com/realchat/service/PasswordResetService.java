package com.realchat.service;

import com.realchat.domain.PasswordResetToken;
import com.realchat.domain.User;
import com.realchat.exception.ApiException;
import com.realchat.repository.PasswordResetRepository;
import com.realchat.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Forgot / reset password. Tokens are 256-bit random values, stored only as SHA-256 hashes, expire (default 30 min),
 * work once, and requesting a new link voids older ones. The request endpoint always behaves the same whether or not
 * the email exists (the email is sent in the background so response time does not leak it either).
 */
@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Executor MAIL_EXECUTOR = Executors.newFixedThreadPool(2, r -> { Thread t = new Thread(r, "reset-mail"); t.setDaemon(true); return t; });
    private static final String INVALID = "This reset link is invalid or has expired";

    private final UserRepository users;
    private final PasswordResetRepository tokens;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final RateLimitService limiter;
    private final ResetMailer mailer;
    private final String frontendBase;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetRepository tokens, PasswordEncoder encoder, SessionService sessions,
                                RateLimitService limiter, ResetMailer mailer,
                                @Value("${app.oauth.frontend-base-url}") String frontendBase,
                                @Value("${app.password-reset.ttl-minutes:30}") long ttlMinutes) {
        this.users = users; this.tokens = tokens; this.encoder = encoder; this.sessions = sessions; this.limiter = limiter;
        this.mailer = mailer; this.frontendBase = frontendBase; this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Transactional
    public void request(String email, String ip) {
        limiter.forgotPassword(ip, email);
        users.findByEmailIgnoreCase(email.strip()).ifPresent(u -> {
            tokens.invalidateAllForUser(u.getId(), Instant.now());
            byte[] b = new byte[32];
            random.nextBytes(b);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
            PasswordResetToken t = new PasswordResetToken();
            t.setUserId(u.getId());
            t.setTokenHash(sha256(raw));
            t.setExpiresAt(Instant.now().plus(ttl));
            tokens.save(t);
            String link = frontendBase + "/reset-password?token=" + raw;
            afterCommit(() -> CompletableFuture.runAsync(() -> {
                try { mailer.sendResetLink(u.getEmail(), link); } catch (Exception e) { log.warn("Could not deliver password-reset email: {}", e.getClass().getSimpleName()); }
            }, MAIL_EXECUTOR));
        });
    }

    @Transactional(readOnly = true)
    public boolean isValid(String raw, String ip) {
        limiter.resetPassword(ip);
        return find(raw).isPresent();
    }

    @Transactional
    public void reset(String raw, String newPassword, String ip) {
        limiter.resetPassword(ip);
        PasswordResetToken t = find(raw).orElseThrow(() -> ApiException.bad(INVALID));
        if (tokens.markUsed(t.getId(), Instant.now()) != 1) throw ApiException.bad(INVALID); // lost a race: already used
        User u = users.findById(t.getUserId()).orElseThrow(() -> ApiException.bad(INVALID));
        u.setPasswordHash(encoder.encode(newPassword));
        users.save(u);
        tokens.invalidateAllForUser(u.getId(), Instant.now());
        sessions.revokeOthers(u.getId(), ""); // sign the account out everywhere
    }

    private Optional<PasswordResetToken> find(String raw) {
        if (raw == null || raw.length() < 20 || raw.length() > 100) return Optional.empty();
        return tokens.findByTokenHash(sha256(raw)).filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()));
    }

    static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { r.run(); } });
        } else r.run();
    }
}