package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "conversations") @Getter @Setter @NoArgsConstructor
public class Conversation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String type;
    private String name;
    private String description;
    private String avatarUrl;
    private String directKey;
    private Long createdBy;
    private boolean announcementOnly;
    private Instant createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}
