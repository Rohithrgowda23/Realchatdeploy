package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "users") @Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String name;
    @Column(nullable = false, unique = true) private String email;
    private String passwordHash;
    private String about;
    private String avatarUrl;
    private Instant createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}
