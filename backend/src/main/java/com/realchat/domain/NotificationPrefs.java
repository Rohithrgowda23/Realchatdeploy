
package com.realchat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notification_prefs")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPrefs {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean mentions = true;

    @Column(name = "join_requests", nullable = false)
    private boolean joinRequests = true;

    @Column(nullable = false)
    private boolean scheduled = true;

    @Column(nullable = false)
    private boolean calls = true;
}
