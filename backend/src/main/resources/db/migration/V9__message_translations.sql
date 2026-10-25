CREATE TABLE message_translations (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      message_id BIGINT NOT NULL,
                                      target_lang VARCHAR(8) NOT NULL,
                                      source_hash CHAR(64) NOT NULL,          -- SHA-256 of the text that was translated; an edit makes the cached row stale
                                      translated_text TEXT NOT NULL,
                                      created_at DATETIME(3) NOT NULL,
                                      CONSTRAINT uq_translation UNIQUE (message_id, target_lang),
                                      CONSTRAINT fk_translation_msg FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;