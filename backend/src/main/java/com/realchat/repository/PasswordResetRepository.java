package com.realchat.repository;

import com.realchat.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Atomic "use once": returns 1 only for the single caller that flips used_at from NULL. */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.userId = :u and t.usedAt is null")
    int invalidateAllForUser(@Param("u") Long userId, @Param("now") Instant now);
}