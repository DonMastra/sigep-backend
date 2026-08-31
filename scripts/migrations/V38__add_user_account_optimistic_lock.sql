BEGIN;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS version BIGINT;

UPDATE users
SET version = 0
WHERE version IS NULL;

ALTER TABLE users
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_users_version_non_negative'
          AND conrelid = 'users'::regclass
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT chk_users_version_non_negative
            CHECK (version >= 0) NOT VALID;
    END IF;
END $$;

ALTER TABLE users
    VALIDATE CONSTRAINT chk_users_version_non_negative;

COMMIT;
