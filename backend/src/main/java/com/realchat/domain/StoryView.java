package com.realchat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "story_views")
@IdClass(StoryView.Key.class)
@Getter @Setter @NoArgsConstructor
public class StoryView {
    @Id private Long storyId;
    @Id private Long viewerId;
    private Instant viewedAt;

    @Getter @Setter @NoArgsConstructor @EqualsAndHashCode
    public static class Key implements Serializable { private Long storyId; private Long viewerId; }
}