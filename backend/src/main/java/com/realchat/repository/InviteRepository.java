package com.realchat.repository;

import com.realchat.domain.GroupInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<GroupInvite, Long> {
    Optional<GroupInvite> findByTokenHash(String tokenHash);
    List<GroupInvite> findByConversationIdOrderByIdDesc(Long conversationId);

    /** Atomically uses one slot; 0 rows means revoked, expired or out of uses (also under concurrent joins). */
    @Modifying
    @Query("update GroupInvite i set i.useCount = i.useCount + 1 where i.id = :id and i.revoked = false "
            + "and (i.maxUses is null or i.useCount < i.maxUses) and (i.expiresAt is null or i.expiresAt > :now)")
    int consume(@Param("id") Long id, @Param("now") Instant now);
}