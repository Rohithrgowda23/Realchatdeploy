package com.realchat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
@Getter @Setter @NoArgsConstructor
public class PasswordResetToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long userId;
    @Column(columnDefinition = "char(64)") private String tokenHash;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}