package com.realchat.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "story_hidden") @Getter @Setter @NoArgsConstructor
public class StoryHidden {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long userId;
    private Long hiddenUserId;
}