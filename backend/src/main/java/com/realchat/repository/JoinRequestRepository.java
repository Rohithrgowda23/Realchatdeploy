package com.realchat.repository;

import com.realchat.domain.JoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {
    boolean existsByConversationIdAndUserIdAndStatus(Long conversationId, Long userId, String status);
    List<JoinRequest> findByConversationIdAndStatusOrderByIdAsc(Long conversationId, String status);
    List<JoinRequest> findByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    /** Decide exactly once: only a still-PENDING row changes, so two admins cannot both apply a decision. */
    @Modifying
    @Query("update JoinRequest r set r.status = :status, r.pendingKey = null, r.decidedBy = :by, r.decidedAt = :now where r.id = :id and r.status = 'PENDING'")
    int decide(@Param("id") Long id, @Param("status") String status, @Param("by") Long decidedBy, @Param("now") Instant now);

    @Modifying
    @Query("update JoinRequest r set r.status = 'REJECTED', r.pendingKey = null, r.decidedAt = :now where r.inviteId = :invite and r.status = 'PENDING'")
    int rejectPendingForInvite(@Param("invite") Long inviteId, @Param("now") Instant now);
}