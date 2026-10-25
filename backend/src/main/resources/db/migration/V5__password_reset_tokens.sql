-- Only a SHA-256 hash of each reset token is stored, never the token itself.
CREATE TABLE password_reset_tokens (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       user_id BIGINT NOT NULL,
                                       token_hash CHAR(64) NOT NULL,
                                       created_at DATETIME(3) NOT NULL,
                                       expires_at DATETIME(3) NOT NULL,
                                       used_at DATETIME(3) NULL,
                                       CONSTRAINT uq_reset_token_hash UNIQUE (token_hash),
                                       CONSTRAINT fk_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                       INDEX idx_reset_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;