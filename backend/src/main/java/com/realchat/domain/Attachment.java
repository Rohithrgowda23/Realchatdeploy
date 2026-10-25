package com.realchat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "attachments")
@Getter @Setter @NoArgsConstructor
public class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long messageId;
    private Long conversationId;
    private String kind;
    private String originalName;
    @Column(columnDefinition = "char(36)") private String storedName;
    private String mimeType;
    private long sizeBytes;
    private Integer durationMs;
    private Instant createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}