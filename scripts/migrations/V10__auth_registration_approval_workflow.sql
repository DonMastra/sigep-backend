-- V10: Auth registration approval workflow
-- Adds account status to users and creates registration_requests table.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS status VARCHAR(255);

UPDATE users
SET status = CASE
    WHEN active = TRUE THEN 'ACTIVE'
    ELSE 'REJECTED'
END
WHERE status IS NULL;

ALTER TABLE users
    ALTER COLUMN status SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'users_status_check'
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT users_status_check
            CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS registration_requests (
    id VARCHAR(255) PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    requested_role VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP NULL,
    reviewed_by BIGINT NULL,
    admin_notes VARCHAR(1000) NULL,
    CONSTRAINT fk_registration_request_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT registration_requests_requested_role_check
        CHECK (requested_role IN ('ADMIN', 'TEACHER', 'GUARDIAN')),
    CONSTRAINT registration_requests_status_check
        CHECK (status IN ('PENDING_APPROVAL', 'ACTIVE', 'REJECTED'))
);

