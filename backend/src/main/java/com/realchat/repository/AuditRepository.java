package com.realchat.repository;

import com.realchat.domain.GroupAuditEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditRepository extends JpaRepository<GroupAuditEvent, Long> {
    List<GroupAuditEvent> findByConversationIdOrderByIdDesc(Long conversationId, Pageable page);
    List<GroupAuditEvent> findByConversationIdAndActionOrderByIdDesc(Long conversationId, String action, Pageable page);
}
