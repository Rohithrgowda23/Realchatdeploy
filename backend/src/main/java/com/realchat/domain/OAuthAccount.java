package com.realchat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "oauth_accounts")
@Getter @Setter @NoArgsConstructor
public class OAuthAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long userId;
    private String provider;          // "google"
    private String providerSubject;   // Google's stable "sub" claim (never the email)
    private String email;
    private Instant createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}