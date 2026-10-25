CREATE TABLE calls (
                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                       call_uid CHAR(36) NOT NULL,              -- unguessable id used by clients; also makes repeated signalling idempotent
                       conversation_id BIGINT NOT NULL,
                       caller_id BIGINT NOT NULL,
                       callee_id BIGINT NOT NULL,
                       type VARCHAR(5) NOT NULL,                -- AUDIO, VIDEO
                       status VARCHAR(10) NOT NULL,             -- RINGING, ACTIVE, COMPLETED, MISSED, REJECTED, FAILED
                       started_at DATETIME(3) NOT NULL,
                       answered_at DATETIME(3) NULL,
                       ended_at DATETIME(3) NULL,
                       duration_seconds INT NULL,
                       end_reason VARCHAR(20) NULL,
                       callee_seen BOOLEAN NOT NULL DEFAULT FALSE,
                       CONSTRAINT uq_call_uid UNIQUE (call_uid),
                       CONSTRAINT fk_call_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
                       CONSTRAINT fk_call_caller FOREIGN KEY (caller_id) REFERENCES users(id),
                       CONSTRAINT fk_call_callee FOREIGN KEY (callee_id) REFERENCES users(id),
                       INDEX idx_call_caller (caller_id, id),
                       INDEX idx_call_callee (callee_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE notification_prefs ADD COLUMN calls BOOLEAN NOT NULL DEFAULT TRUE;