package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "message_mentions") @Getter @Setter @NoArgsConstructor
public class MessageMention {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long messageId;
    private Long userId;
    private String nameAtSend;
}