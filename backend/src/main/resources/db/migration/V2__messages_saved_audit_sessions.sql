
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS forwarded_from_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS forwarded_from_name VARCHAR(100) NULL;

ALTER TABLE conversations
    ADD COLUMN IF NOT EXISTS announcement_only BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS saved_messages (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              user_id BIGINT NOT NULL,
                                              message_id BIGINT NOT NULL,
                                              created_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_saved UNIQUE (user_id, message_id),
    CONSTRAINT fk_saved_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_saved_msg FOREIGN KEY (message_id)
    REFERENCES messages(id) ON DELETE CASCADE,
    INDEX idx_saved_user (user_id, id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hidden_messages (
                                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               user_id BIGINT NOT NULL,
                                               message_id BIGINT NOT NULL,
                                               created_at DATETIME(3) NOT NULL,
    CONSTRAINT uq_hidden UNIQUE (user_id, message_id),
    CONSTRAINT fk_hidden_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_hidden_msg FOREIGN KEY (message_id)
    REFERENCES messages(id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS group_audit_events (
                                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                  conversation_id BIGINT NOT NULL,
                                                  actor_id BIGINT NOT NULL,
                                                  action VARCHAR(40) NOT NULL,
    target_id BIGINT NULL,
    detail VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_audit_conv FOREIGN KEY (conversation_id)
    REFERENCES conversations(id) ON DELETE CASCADE,
    INDEX idx_audit_conv (conversation_id, id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_sessions (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             session_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    user_agent VARCHAR(255) NULL,
    ip VARCHAR(45) NULL,
    created_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_session_id UNIQUE (session_id),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_session_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
