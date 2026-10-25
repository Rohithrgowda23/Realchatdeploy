package com.realchat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "scheduled_messages")
@Getter @Setter @NoArgsConstructor
public class ScheduledMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long conversationId;
    private Long senderId;
    @Column(columnDefinition = "TEXT") private String content;
    private Long replyToId;
    private Instant scheduledAt;
    private String status;
    private int attempts;
    private String lastError;
    private Instant claimedAt;
    private Long sentMessageId;
    private Instant sentAt;
    private Instant createdAt;
    @Version private long version;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}