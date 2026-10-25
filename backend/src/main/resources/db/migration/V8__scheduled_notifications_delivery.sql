-- delivered receipts: each member's highest message id known to have reached their client
ALTER TABLE conversation_members ADD COLUMN last_delivered_message_id BIGINT NOT NULL DEFAULT 0;
UPDATE conversation_members SET last_delivered_message_id = last_read_message_id;

CREATE TABLE scheduled_messages (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    conversation_id BIGINT NOT NULL,
                                    sender_id BIGINT NOT NULL,
                                    content TEXT NOT NULL,
                                    reply_to_id BIGINT NULL,
                                    scheduled_at DATETIME(3) NOT NULL,         -- UTC instant
                                    status VARCHAR(10) NOT NULL,               -- PENDING, SENDING, SENT, CANCELLED, FAILED
                                    attempts INT NOT NULL DEFAULT 0,
                                    last_error VARCHAR(255) NULL,
                                    claimed_at DATETIME(3) NULL,
                                    sent_message_id BIGINT NULL,
                                    sent_at DATETIME(3) NULL,
                                    created_at DATETIME(3) NOT NULL,
                                    version BIGINT NOT NULL DEFAULT 0,         -- optimistic lock: edit/cancel cannot race the sender
                                    CONSTRAINT fk_sched_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_sched_sender FOREIGN KEY (sender_id) REFERENCES users(id),
                                    INDEX idx_sched_due (status, scheduled_at),
                                    INDEX idx_sched_sender (sender_id, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               user_id BIGINT NOT NULL,
                               type VARCHAR(30) NOT NULL,
                               title VARCHAR(160) NOT NULL,
                               body VARCHAR(255) NULL,
                               conversation_id BIGINT NULL,
                               message_id BIGINT NULL,
                               read_at DATETIME(3) NULL,
                               created_at DATETIME(3) NOT NULL,
                               CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                               INDEX idx_notif_user (user_id, id),
                               INDEX idx_notif_unread (user_id, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_prefs (
                                    user_id BIGINT PRIMARY KEY,
                                    mentions BOOLEAN NOT NULL DEFAULT TRUE,
                                    join_requests BOOLEAN NOT NULL DEFAULT TRUE,
                                    scheduled BOOLEAN NOT NULL DEFAULT TRUE,
                                    CONSTRAINT fk_prefs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;