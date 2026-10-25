CREATE TABLE group_invites (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               conversation_id BIGINT NOT NULL,
                               token_hash CHAR(64) NOT NULL,           -- SHA-256 of the token; the token itself is never stored
                               created_by BIGINT NOT NULL,
                               created_at DATETIME(3) NOT NULL,
                               expires_at DATETIME(3) NULL,
                               max_uses INT NULL,
                               use_count INT NOT NULL DEFAULT 0,
                               revoked BOOLEAN NOT NULL DEFAULT FALSE,
                               requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
                               CONSTRAINT uq_invite_token UNIQUE (token_hash),
                               CONSTRAINT fk_invite_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
                               CONSTRAINT fk_invite_user FOREIGN KEY (created_by) REFERENCES users(id),
                               INDEX idx_invite_conv (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE join_requests (
                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                               conversation_id BIGINT NOT NULL,
                               user_id BIGINT NOT NULL,
                               invite_id BIGINT NULL,
                               status VARCHAR(10) NOT NULL,            -- PENDING, APPROVED, REJECTED, CANCELLED
                               pending_key TINYINT NULL,               -- 1 while pending, NULL afterwards: allows only one pending request per user and group
                               created_at DATETIME(3) NOT NULL,
                               decided_by BIGINT NULL,
                               decided_at DATETIME(3) NULL,
                               CONSTRAINT uq_join_pending UNIQUE (conversation_id, user_id, pending_key),
                               CONSTRAINT fk_join_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
                               CONSTRAINT fk_join_user FOREIGN KEY (user_id) REFERENCES users(id),
                               INDEX idx_join_conv_status (conversation_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE message_mentions (
                                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                  message_id BIGINT NOT NULL,
                                  user_id BIGINT NOT NULL,
                                  name_at_send VARCHAR(100) NOT NULL,     -- what was typed after the @, so renames do not break highlighting
                                  CONSTRAINT uq_mention UNIQUE (message_id, user_id),
                                  CONSTRAINT fk_mention_msg FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_mention_user FOREIGN KEY (user_id) REFERENCES users(id),
                                  INDEX idx_mention_user (user_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;