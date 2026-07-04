-- V11: Extend users table with guardian profile fields
-- Adds optional columns for extended profile information (phoneNumber, address, dateOfBirth, documentNumber, emergencyContact)
-- to support GUARDIAN and TEACHER profile persistence and student self-registration flows.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(255) NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS address VARCHAR(255) NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS date_of_birth DATE NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS document_number VARCHAR(255) NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS emergency_contact VARCHAR(255) NULL;

-- Log migration completion
DO $$
BEGIN
    RAISE NOTICE '[V11] Extended users table with guardian profile fields (phone_number, address, date_of_birth, document_number, emergency_contact)';
END $$;

