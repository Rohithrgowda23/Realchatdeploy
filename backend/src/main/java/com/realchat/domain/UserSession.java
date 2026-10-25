package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "user_sessions") @Getter @Setter @NoArgsConstructor
public class UserSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(columnDefinition = "char(36)") private String sessionId;
    private Long userId;
    private String userAgent;
    private String ip;
    private Instant createdAt;
    private Instant lastSeenAt;
    private Instant expiresAt;
    private boolean revoked;
    @PrePersist void pre() { Instant n = Instant.now(); if (createdAt == null) createdAt = n; if (lastSeenAt == null) lastSeenAt = n; }
}