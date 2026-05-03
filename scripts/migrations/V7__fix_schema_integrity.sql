-- ==============================================================================
-- V7: Fix schema integrity — students & course_sessions
--
-- PROBLEM 1 – students.guardian_id was NOT NULL forcing a sentinel value (0)
--   when a student has no guardian. This is an anti-pattern; NULL is the
--   correct representation for "no guardian assigned".
--
-- PROBLEM 2 – students.phone_number, document_number, emergency_contact were
--   nullable in DB even though the application always stores a value. This
--   mismatch meant the DB could not enforce the business rule.
--
-- PROBLEM 3 – course_sessions.session_date, start_time, end_time were nullable
--   in DB but NOT NULL in JPA entity. Legacy data was inserted via old scripts
--   that used `scheduled_date` instead of `session_date`, leaving all rows with
--   NULL session_date. Start/end times had no data at all.
--
-- STRATEGY:
--   - Make guardian_id nullable in DB (matches new entity: Long?)
--   - Enforce NOT NULL on the columns that should always have a value
--   - Migrate legacy schedule data: scheduled_date -> session_date
--   - Assign default work hours (09:00–10:00) to sessions missing times
--   - Then add NOT NULL constraints on course_sessions key columns
-- ==============================================================================

-- -----------------------------------------------------------------------
-- Step 1: students — make guardian_id nullable
-- -----------------------------------------------------------------------
ALTER TABLE students ALTER COLUMN guardian_id DROP NOT NULL;

-- -----------------------------------------------------------------------
-- Step 2: students — enforce NOT NULL on mandatory fields
--   (safe: no NULLs exist in current data as verified before migration)
-- -----------------------------------------------------------------------
ALTER TABLE students ALTER COLUMN phone_number      SET NOT NULL;
ALTER TABLE students ALTER COLUMN emergency_contact  SET NOT NULL;
ALTER TABLE students ALTER COLUMN document_number    SET NOT NULL;

-- -----------------------------------------------------------------------
-- Step 3: course_sessions — migrate legacy scheduled_date -> session_date
-- -----------------------------------------------------------------------
UPDATE course_sessions
SET session_date = scheduled_date
WHERE session_date IS NULL
  AND scheduled_date IS NOT NULL;

-- -----------------------------------------------------------------------
-- Step 4: course_sessions — fill default times for legacy rows without hours
-- -----------------------------------------------------------------------
UPDATE course_sessions
SET start_time = '09:00:00',
    end_time   = '10:00:00'
WHERE start_time IS NULL
   OR end_time IS NULL;

-- -----------------------------------------------------------------------
-- Step 5: course_sessions — enforce NOT NULL on key scheduling columns
-- -----------------------------------------------------------------------
ALTER TABLE course_sessions ALTER COLUMN session_date SET NOT NULL;
ALTER TABLE course_sessions ALTER COLUMN start_time   SET NOT NULL;
ALTER TABLE course_sessions ALTER COLUMN end_time     SET NOT NULL;

