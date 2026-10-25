package com.realchat.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "calls")
@Getter @Setter @NoArgsConstructor
public class CallRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(columnDefinition = "char(36)") private String callUid;
    private Long conversationId;
    private Long callerId;
    private Long calleeId;
    private String type;
    private String status;
    private Instant startedAt;
    private Instant answeredAt;
    private Instant endedAt;
    private Integer durationSeconds;
    private String endReason;
    private boolean calleeSeen;
}