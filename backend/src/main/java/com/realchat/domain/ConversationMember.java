package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity
@Table(name = "conversation_members")
@Getter
@Setter
@NoArgsConstructor
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role")
    private String role;

    @Column(name = "pinned")
    private boolean pinned;

    @Column(name = "muted")
    private boolean muted;

    @Column(name = "archived")
    private boolean archived;

    @Column(name = "last_read_message_id")
    private long lastReadMessageId;

    @Column(name = "last_delivered_message_id")
    private long lastDeliveredMessageId;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @PrePersist
    void pre() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }
}