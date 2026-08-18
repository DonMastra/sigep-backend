-- V31 - Require selected training administrators to replace temporary passwords.
-- Manual migration. Re-runnable and safe for PostgreSQL/Neon.

BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS must_change_password boolean;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_changed_at timestamp;

UPDATE users
SET must_change_password = false
WHERE must_change_password IS NULL;

ALTER TABLE users
    ALTER COLUMN must_change_password SET DEFAULT false;

ALTER TABLE users
    ALTER COLUMN must_change_password SET NOT NULL;

UPDATE users
SET must_change_password = true,
    password_changed_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE LOWER(username) IN ('rmainero', 'agomez', 'amastracchio')
  AND must_change_password = false
  AND password_changed_at IS NULL;

COMMIT;
