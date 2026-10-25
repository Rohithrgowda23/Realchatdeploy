package com.realchat.repository;

import com.realchat.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findBySessionId(String sessionId);
    List<UserSession> findByUserIdAndRevokedFalseAndExpiresAtAfterOrderByLastSeenAtDesc(Long userId, Instant now);

    @Modifying
    @Query("update UserSession s set s.revoked = true where s.userId = :u and s.sessionId <> :sid and s.revoked = false")
    int revokeOthers(@Param("u") Long userId, @Param("sid") String currentSid);
}
