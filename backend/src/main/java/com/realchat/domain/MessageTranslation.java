package com.realchat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "message_translations")
@Getter @Setter @NoArgsConstructor
public class MessageTranslation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long messageId;
    private String targetLang;
    @Column(columnDefinition = "char(64)") private String sourceHash;
    @Column(columnDefinition = "TEXT") private String translatedText;
    private Instant createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}