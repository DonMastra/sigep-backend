-- V11: Extend users table with guardian profile fields
-- Adds optional columns for extended profile information (phoneNumber, address, dateOfBirth, documentNumber, emergencyContact)
-- to support GUARDIAN and TEACHER profile persistence and student self-registration flows.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20) NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS address VARCHAR(500) NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS date_of_birth DATE NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS document_number VARCHAR(50) NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS emergency_contact VARCHAR(255) NULL;

-- Create index on document_number for faster lookups (useful for student registration)
CREATE INDEX IF NOT EXISTS idx_users_document_number
    ON users(document_number);

-- Add unique constraint on document_number where it is not null
-- (allows multiple null values, but non-null values must be unique)
ALTER TABLE users
    ADD CONSTRAINT users_document_number_unique_not_null
    UNIQUE NULLS DISTINCT (document_number)
    WHERE document_number IS NOT NULL;

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE '[V11] Extended users table with guardian profile fields (phone_number, address, date_of_birth, document_number, emergency_contact)';
END $$;

