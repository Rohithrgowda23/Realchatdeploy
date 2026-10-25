package com.realchat.repository;

import com.realchat.domain.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<ConversationMember, Long> {
    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);
    List<ConversationMember> findByConversationId(Long conversationId);
    List<ConversationMember> findByUserId(Long userId);

    @Query("select m.userId from ConversationMember m where m.conversationId = :c")
    List<Long> memberIds(@Param("c") Long conversationId);

    /** Delivered only moves forward. */
    @Transactional
    @Modifying
    @Query("update ConversationMember m set m.lastDeliveredMessageId = :id where m.conversationId = :c and m.userId in :users and m.lastDeliveredMessageId < :id")
    int markDelivered(@Param("c") Long conversationId, @Param("users") Collection<Long> users, @Param("id") long messageId);
}