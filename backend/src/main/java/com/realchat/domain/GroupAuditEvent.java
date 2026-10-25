package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "group_audit_events") @Getter @Setter @NoArgsConstructor
public class GroupAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long conversationId;
    private Long actorId;
    private String action;
    private Long targetId;
    private String detail;
    private Instant createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}
