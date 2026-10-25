package com.realchat.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public record TokenInfo(Long userId, String sid) {}

    public long getExpirationMinutes() { return expirationMinutes; }

    public String generate(Long userId, String name) { return generate(userId, name, null); }

    public String generate(Long userId, String name, String sessionId) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject(String.valueOf(userId)).claim("name", name).claim("sid", sessionId)
                .issuedAt(new Date(now)).expiration(new Date(now + expirationMinutes * 60_000))
                .signWith(key).compact();
    }

    public Optional<Long> parseUserId(String token) { return parse(token).map(TokenInfo::userId); }

    /** Returns user id + session id if the token is valid, signed by us and not expired. */
    public Optional<TokenInfo> parse(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new TokenInfo(Long.valueOf(claims.getSubject()), claims.get("sid", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
