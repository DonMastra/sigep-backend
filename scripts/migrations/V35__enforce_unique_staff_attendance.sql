-- Preflight: stop without changing constraints when legacy rows are ambiguous or duplicated.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM staff_attendance
        WHERE (teaching_staff_id IS NULL) = (non_teaching_staff_id IS NULL)
    ) THEN
        RAISE EXCEPTION
            'staff_attendance contains rows without exactly one staff reference; reconcile them before V35';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM staff_attendance
        GROUP BY teaching_staff_id, attendance_date
        HAVING teaching_staff_id IS NOT NULL AND COUNT(*) > 1
    ) OR EXISTS (
        SELECT 1
        FROM staff_attendance
        GROUP BY non_teaching_staff_id, attendance_date
        HAVING non_teaching_staff_id IS NOT NULL AND COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'staff_attendance contains duplicate staff/date rows; reconcile them before V35';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_staff_attendance_one_staff') THEN
        ALTER TABLE staff_attendance
            ADD CONSTRAINT chk_staff_attendance_one_staff
            CHECK ((teaching_staff_id IS NOT NULL) <> (non_teaching_staff_id IS NOT NULL));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_attendance_teaching_date
    ON staff_attendance(teaching_staff_id, attendance_date)
    WHERE teaching_staff_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_attendance_non_teaching_date
    ON staff_attendance(non_teaching_staff_id, attendance_date)
    WHERE non_teaching_staff_id IS NOT NULL;
