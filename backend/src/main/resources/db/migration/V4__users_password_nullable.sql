-- Google-only accounts have no password, so password_hash must allow NULL.
-- Safe to run even if it is already nullable.
ALTER TABLE users MODIFY password_hash VARCHAR(100) NULL;