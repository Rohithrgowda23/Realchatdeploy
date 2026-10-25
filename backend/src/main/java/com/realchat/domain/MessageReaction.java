package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "message_reactions") @Getter @Setter @NoArgsConstructor
public class MessageReaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long messageId;
    private Long userId;
    private String emoji;
}
