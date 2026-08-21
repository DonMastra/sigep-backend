-- V33 - Add the administrative client profile for GUARDIAN users.
-- Manual PostgreSQL migration. Re-runnable and safe for environments that use ddl-auto=validate.

BEGIN;

CREATE TABLE IF NOT EXISTS guardian_client_profiles (
    guardian_user_id bigint PRIMARY KEY,
    client_number varchar(32) NOT NULL,
    preferred_contact_channel varchar(20) NOT NULL DEFAULT 'EMAIL',
    administrative_notes varchar(1000),
    updated_by bigint,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0
);

INSERT INTO guardian_client_profiles (
    guardian_user_id,
    client_number,
    preferred_contact_channel,
    created_at,
    updated_at,
    version
)
SELECT
    u.id,
    'CLI-' || lpad(u.id::text, 12, '0'),
    CASE
        WHEN u.phone_number IS NOT NULL AND btrim(u.phone_number) <> '' THEN 'WHATSAPP'
        ELSE 'EMAIL'
    END,
    COALESCE(u.created_at, now()),
    now(),
    0
FROM users u
WHERE u.role = 'GUARDIAN'
ON CONFLICT (guardian_user_id) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uq_guardian_client_profile_number
    ON guardian_client_profiles(client_number);
CREATE INDEX IF NOT EXISTS idx_guardian_client_profile_contact_channel
    ON guardian_client_profiles(preferred_contact_channel);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_guardian_client_profile_user') THEN
        ALTER TABLE guardian_client_profiles
            ADD CONSTRAINT fk_guardian_client_profile_user
            FOREIGN KEY (guardian_user_id) REFERENCES users(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_guardian_client_profile_updated_by') THEN
        ALTER TABLE guardian_client_profiles
            ADD CONSTRAINT fk_guardian_client_profile_updated_by
            FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_guardian_client_contact_channel') THEN
        ALTER TABLE guardian_client_profiles
            ADD CONSTRAINT chk_guardian_client_contact_channel
            CHECK (preferred_contact_channel IN ('EMAIL', 'PHONE', 'WHATSAPP'));
    END IF;
END $$;

ALTER TABLE guardian_client_profiles VALIDATE CONSTRAINT fk_guardian_client_profile_user;
ALTER TABLE guardian_client_profiles VALIDATE CONSTRAINT fk_guardian_client_profile_updated_by;

COMMIT;
