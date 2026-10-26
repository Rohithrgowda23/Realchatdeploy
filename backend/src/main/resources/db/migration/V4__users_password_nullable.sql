
-- Allow Google OAuth-only accounts to have no password.
-- Safe to run even if password_hash is already nullable.
ALTER TABLE public.users
    ALTER COLUMN password_hash DROP NOT NULL;
