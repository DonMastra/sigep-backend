-- First complete manual flow: staff accounts/photos, flexible courses,
-- explicit tuition levels and attendance tied to real sessions.

ALTER TABLE teaching_staff
    ADD COLUMN IF NOT EXISTS linked_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS photo_data BYTEA,
    ADD COLUMN IF NOT EXISTS photo_content_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS photo_filename VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_teaching_staff_linked_user') THEN
        ALTER TABLE teaching_staff
            ADD CONSTRAINT fk_teaching_staff_linked_user
            FOREIGN KEY (linked_user_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_teaching_staff_linked_user
    ON teaching_staff(linked_user_id) WHERE linked_user_id IS NOT NULL;

-- Only link legacy rows when both sides have one unambiguous full-name match.
WITH unique_staff AS (
    SELECT LOWER(TRIM(first_name)) AS first_name, LOWER(TRIM(last_name)) AS last_name, MIN(id) AS staff_id
    FROM teaching_staff
    WHERE linked_user_id IS NULL
    GROUP BY LOWER(TRIM(first_name)), LOWER(TRIM(last_name))
    HAVING COUNT(*) = 1
), unique_users AS (
    SELECT LOWER(TRIM(first_name)) AS first_name, LOWER(TRIM(last_name)) AS last_name, MIN(id) AS user_id
    FROM users
    WHERE role = 'TEACHER' AND status = 'ACTIVE' AND active = TRUE
    GROUP BY LOWER(TRIM(first_name)), LOWER(TRIM(last_name))
    HAVING COUNT(*) = 1
)
UPDATE teaching_staff ts
SET linked_user_id = uu.user_id
FROM unique_staff us
JOIN unique_users uu USING (first_name, last_name)
WHERE ts.id = us.staff_id
  AND NOT EXISTS (SELECT 1 FROM teaching_staff linked WHERE linked.linked_user_id = uu.user_id);

ALTER TABLE courses ALTER COLUMN teacher_id DROP NOT NULL;

ALTER TABLE courses DROP CONSTRAINT IF EXISTS courses_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_courses_code_ci ON courses (LOWER(code));

ALTER TABLE tuition_levels ADD COLUMN IF NOT EXISTS course_level VARCHAR(40);
UPDATE tuition_levels
SET course_level = CASE UPPER(code)
    WHEN 'BEGINNER' THEN 'BEGINNER'
    WHEN 'ELEMENTARY' THEN 'ELEMENTARY'
    WHEN 'A1' THEN 'BEGINNER'
    WHEN 'A2' THEN 'ELEMENTARY'
    ELSE course_level
END
WHERE course_level IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_tuition_course_level') THEN
        ALTER TABLE tuition_levels ADD CONSTRAINT chk_tuition_course_level CHECK (
            course_level IS NULL OR course_level IN (
                'BEGINNER', 'ELEMENTARY', 'PRE_INTERMEDIATE', 'INTERMEDIATE',
                'UPPER_INTERMEDIATE', 'ADVANCED', 'PROFICIENCY'
            )
        );
    END IF;
END $$;

ALTER TABLE tuition_level_progression DROP CONSTRAINT IF EXISTS chk_tuition_progression_rule;
ALTER TABLE tuition_level_progression ADD CONSTRAINT chk_tuition_progression_rule
    CHECK (rule IN ('PASS_PREVIOUS_LEVEL', 'ADMIN_APPROVAL'));

ALTER TABLE tuition_applications
    ADD COLUMN IF NOT EXISTS progression_rule VARCHAR(30),
    ADD COLUMN IF NOT EXISTS requires_admin_override BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE course_attendance ADD COLUMN IF NOT EXISTS course_session_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_attendance_course_session') THEN
        ALTER TABLE course_attendance
            ADD CONSTRAINT fk_attendance_course_session
            FOREIGN KEY (course_session_id) REFERENCES course_sessions(id) ON DELETE RESTRICT;
    END IF;
END $$;

-- Backfill only dates that identify exactly one session for the enrolled course.
WITH unique_sessions AS (
    SELECT course_id, session_date, MIN(id) AS session_id
    FROM course_sessions
    GROUP BY course_id, session_date
    HAVING COUNT(*) = 1
)
UPDATE course_attendance ca
SET course_session_id = us.session_id
FROM enrollments e, unique_sessions us
WHERE ca.enrollment_id = e.id
  AND us.course_id = e.course_id
  AND us.session_date = ca.attendance_date
  AND ca.course_session_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_attendance_session ON course_attendance(course_session_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_attendance_enrollment_session
    ON course_attendance(enrollment_id, course_session_id)
    WHERE course_session_id IS NOT NULL;
