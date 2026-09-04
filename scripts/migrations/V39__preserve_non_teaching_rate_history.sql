-- Additive migration: existing rows remain unchanged because their historic currency/rate
-- cannot be inferred safely. Administration must confirm currency on existing staff records.

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM staff_attendance
        WHERE hours_worked IS NOT NULL
          AND (hours_worked < 0 OR hours_worked > 24)
    ) THEN
        RAISE EXCEPTION 'V39 aborted: staff_attendance contains hours_worked values outside 0..24';
    END IF;
END $$;

ALTER TABLE non_teaching_staff
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

ALTER TABLE teaching_staff
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

ALTER TABLE staff_attendance
    ADD COLUMN IF NOT EXISTS hourly_rate_snapshot NUMERIC(19, 2),
    ADD COLUMN IF NOT EXISTS currency_snapshot VARCHAR(3);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_non_teaching_staff_currency') THEN
        ALTER TABLE non_teaching_staff
            ADD CONSTRAINT chk_non_teaching_staff_currency
            CHECK (currency IS NULL OR currency IN ('ARS', 'USD'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_teaching_staff_currency') THEN
        ALTER TABLE teaching_staff
            ADD CONSTRAINT chk_teaching_staff_currency
            CHECK (currency IS NULL OR currency IN ('ARS', 'USD'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_staff_attendance_currency_snapshot') THEN
        ALTER TABLE staff_attendance
            ADD CONSTRAINT chk_staff_attendance_currency_snapshot
            CHECK (currency_snapshot IS NULL OR currency_snapshot IN ('ARS', 'USD'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_staff_attendance_rate_snapshot_pair') THEN
        ALTER TABLE staff_attendance
            ADD CONSTRAINT chk_staff_attendance_rate_snapshot_pair
            CHECK ((hourly_rate_snapshot IS NULL) = (currency_snapshot IS NULL));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_staff_attendance_non_negative_values') THEN
        ALTER TABLE staff_attendance
            ADD CONSTRAINT chk_staff_attendance_non_negative_values
            CHECK (
                (hours_worked IS NULL OR (hours_worked >= 0 AND hours_worked <= 24))
                AND (hourly_rate_snapshot IS NULL OR hourly_rate_snapshot >= 0)
            );
    END IF;
END $$;

COMMIT;
