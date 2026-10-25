package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "stories") @Getter @Setter @NoArgsConstructor
public class Story {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long authorId;
    private String type;
    private String text;
    private String bgColor;
    @Column(columnDefinition = "char(36)") private String storedName;
    private String mimeType;
    private long sizeBytes;
    private Instant createdAt;
    private Instant expiresAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}