package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "messages") @Getter @Setter @NoArgsConstructor
public class ChatMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long conversationId;
    private Long senderId;
    @Column(columnDefinition = "TEXT") private String content;
    private Long replyToId;
    private boolean edited;
    private boolean deleted;
    private Long forwardedFromId;
    private String forwardedFromName;
    private Instant createdAt;
    @PrePersist void pre() { if (createdAt == null) createdAt = Instant.now(); }
}
