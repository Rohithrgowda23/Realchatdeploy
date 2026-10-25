CREATE TABLE stories (
                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                         author_id BIGINT NOT NULL,
                         type VARCHAR(10) NOT NULL,              -- TEXT, IMAGE, VIDEO
                         text VARCHAR(500) NULL,                 -- the story text, or a caption for media
                         bg_color VARCHAR(7) NULL,
                         stored_name CHAR(36) NULL,              -- media lives on disk (see FileStorage), never in MySQL
                         mime_type VARCHAR(100) NULL,
                         size_bytes BIGINT NOT NULL DEFAULT 0,
                         created_at DATETIME(3) NOT NULL,
                         expires_at DATETIME(3) NOT NULL,        -- decided by the server clock when the story is created
                         CONSTRAINT fk_story_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
                         INDEX idx_story_author (author_id, expires_at),
                         INDEX idx_story_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_views (
                             story_id BIGINT NOT NULL,
                             viewer_id BIGINT NOT NULL,
                             viewed_at DATETIME(3) NOT NULL,
                             PRIMARY KEY (story_id, viewer_id),
                             CONSTRAINT fk_view_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
                             CONSTRAINT fk_view_user FOREIGN KEY (viewer_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_settings (
                                user_id BIGINT PRIMARY KEY,
                                audience VARCHAR(20) NOT NULL DEFAULT 'CONTACTS',   -- CONTACTS, CONTACTS_AND_GROUPS, NOBODY
                                CONSTRAINT fk_story_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE story_hidden (
                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                              user_id BIGINT NOT NULL,
                              hidden_user_id BIGINT NOT NULL,
                              CONSTRAINT uq_story_hidden UNIQUE (user_id, hidden_user_id),
                              CONSTRAINT fk_hidden_owner FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                              CONSTRAINT fk_hidden_target FOREIGN KEY (hidden_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;