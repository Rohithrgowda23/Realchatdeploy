-- OAuth-only users have no password
ALTER TABLE users MODIFY password_hash VARCHAR(100) NULL;

CREATE TABLE oauth_accounts (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                user_id BIGINT NOT NULL,
                                provider VARCHAR(20) NOT NULL,
                                provider_subject VARCHAR(100) NOT NULL,
                                email VARCHAR(190) NULL,
                                created_at DATETIME(3) NOT NULL,
                                CONSTRAINT uq_oauth_provider_subject UNIQUE (provider, provider_subject),
                                CONSTRAINT uq_oauth_user_provider UNIQUE (user_id, provider),
                                CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;