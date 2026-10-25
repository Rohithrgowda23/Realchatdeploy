package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "user_blocks") @Getter @Setter @NoArgsConstructor
public class UserBlock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long blockerId;
    private Long blockedId;
    private Instant createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}
