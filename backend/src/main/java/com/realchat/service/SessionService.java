package com.realchat.service;

import com.realchat.domain.UserSession;
import com.realchat.dto.Dto.SessionDto;
import com.realchat.exception.ApiException;
import com.realchat.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side session registry. Every issued JWT carries a session id (sid); a token is honoured only while its
 * session row exists, is not revoked and has not expired. This is what makes logout / "revoke device" real for
 * stateless JWTs. Cost: one indexed lookup per request. Already-open WebSockets are re-checked only on (re)connect.
 */
@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionRepository repo;

    @Transactional
    public String create(Long userId, String userAgent, String ip, long ttlMinutes) {
        UserSession s = new UserSession();
        s.setSessionId(UUID.randomUUID().toString());
        s.setUserId(userId);
        s.setUserAgent(clip(userAgent, 255));
        s.setIp(clip(ip, 45));
        s.setExpiresAt(Instant.now().plus(Duration.ofMinutes(ttlMinutes)));
        return repo.save(s).getSessionId();
    }

    @Transactional
    public boolean validate(String sid, Long userId) {
        if (sid == null || userId == null) return false;
        Optional<UserSession> o = repo.findBySessionId(sid);
        if (o.isEmpty()) return false;
        UserSession s = o.get();
        Instant now = Instant.now();
        if (s.isRevoked() || !s.getUserId().equals(userId) || !s.getExpiresAt().isAfter(now)) return false;
        if (s.getLastSeenAt().isBefore(now.minusSeconds(60))) { s.setLastSeenAt(now); repo.save(s); }
        return true;
    }

    @Transactional(readOnly = true)
    public List<SessionDto> list(Long userId, String currentSid) {
        return repo.findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtDesc(userId, Instant.now()).stream()
                .map(s -> new SessionDto(s.getSessionId(), s.getUserAgent(), s.getIp(), s.getCreatedAt(), s.getLastSeenAt(), s.getSessionId().equals(currentSid)))
                .toList();
    }

    /** A user can only revoke their own sessions; anything else looks like "not found". */
    @Transactional
    public void revoke(Long userId, String sessionId) {
        UserSession s = repo.findBySessionId(sessionId).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Session not found"));
        s.setRevoked(true);
        repo.save(s);
    }

    @Transactional
    public int revokeOthers(Long userId, String currentSid) { return repo.revokeOthers(userId, currentSid == null ? "" : currentSid); }

    private static String clip(String s, int n) { return s == null ? null : (s.length() <= n ? s : s.substring(0, n)); }
}
