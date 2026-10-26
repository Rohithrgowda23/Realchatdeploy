package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "join_requests") @Getter @Setter @NoArgsConstructor
public class JoinRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long conversationId;
    private Long userId;
    private Long inviteId;
    private String status;
    @Column(name = "pending_key")
    private Integer pendingKey;
    private Instant createdAt;
    private Long decidedBy;
    private Instant decidedAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}