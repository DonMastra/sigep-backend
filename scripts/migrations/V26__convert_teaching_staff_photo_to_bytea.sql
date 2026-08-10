-- =============================================================================
-- V26__convert_teaching_staff_photo_to_bytea.sql
-- Reconciles schemas where Hibernate created teaching_staff.photo_data as OID
-- while the current JPA mapping expects PostgreSQL BYTEA.
-- =============================================================================

BEGIN;

SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

DO $$
DECLARE
    current_type TEXT;
BEGIN
    SELECT udt_name
    INTO current_type
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'teaching_staff'
      AND column_name = 'photo_data';

    IF current_type IS NULL THEN
        RAISE EXCEPTION 'teaching_staff.photo_data does not exist';
    ELSIF current_type = 'oid' THEN
        IF EXISTS (
            SELECT 1
            FROM teaching_staff
            WHERE photo_data IS NOT NULL
        ) THEN
            RAISE EXCEPTION
                'Cannot convert teaching_staff.photo_data automatically: stored photos must be migrated first';
        END IF;

        ALTER TABLE teaching_staff
            ALTER COLUMN photo_data TYPE BYTEA
            USING NULL::BYTEA;
    ELSIF current_type <> 'bytea' THEN
        RAISE EXCEPTION
            'Unexpected type for teaching_staff.photo_data: %',
            current_type;
    END IF;
END $$;

COMMIT;
