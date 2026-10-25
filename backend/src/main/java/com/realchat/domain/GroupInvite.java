package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "group_invites") @Getter @Setter @NoArgsConstructor
public class GroupInvite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long conversationId;
    @Column(columnDefinition = "char(64)") private String tokenHash;
    private Long createdBy;
    private Instant createdAt;
    private Instant expiresAt;
    private Integer maxUses;
    private int useCount;
    private boolean revoked;
    private boolean requiresApproval;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}