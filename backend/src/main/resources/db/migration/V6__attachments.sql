CREATE TABLE attachments (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                             message_id BIGINT NOT NULL,
                             conversation_id BIGINT NOT NULL,
                             kind VARCHAR(10) NOT NULL,            -- IMAGE, VIDEO, AUDIO, VOICE, DOCUMENT
                             original_name VARCHAR(255) NOT NULL,  -- display only; never used as a path
                             stored_name CHAR(36) NOT NULL,        -- server-generated UUID file name
                             mime_type VARCHAR(100) NOT NULL,      -- decided by the server from the file's bytes
                             size_bytes BIGINT NOT NULL,
                             duration_ms INT NULL,
                             created_at DATETIME(3) NOT NULL,
                             CONSTRAINT uq_attachment_message UNIQUE (message_id),
                             CONSTRAINT uq_attachment_stored UNIQUE (stored_name),
                             CONSTRAINT fk_attachment_message FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE,
                             INDEX idx_attachment_gallery (conversation_id, kind, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;