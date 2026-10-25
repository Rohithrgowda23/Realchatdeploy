package com.realchat.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "story_settings")
@Getter @Setter @NoArgsConstructor
public class StorySettings {
    @Id private Long userId;
    private String audience = "CONTACTS";
}