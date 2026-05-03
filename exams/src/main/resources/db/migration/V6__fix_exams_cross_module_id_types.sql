-- ==============================================================================
-- V6: Fix cross-module ID types in exams module
--
-- PROBLEM: exams.course_id, exam_submissions.student_id and all audit columns
-- (created_by, updated_by, graded_by, changed_by) were defined as UUID but
-- they reference tables in other modules whose PKs are BIGINT (users, students,
-- courses). This blocked real FK integrity and caused runtime errors.
--
-- STRATEGY:
-- 1. Drop any existing FK/unique constraints and indexes that block column changes
-- 2. Clean existing data (UUID values are not mappable to real BIGINT ids)
-- 3. ALTER columns to BIGINT
-- 4. Add proper FK constraints to courses, students, users
--
-- NOTE: exam_submissions.exam_id and exam_grade_history.submission_id stay UUID
--       because they reference exams.id and exam_submissions.id, which keep UUID PKs.
-- ==============================================================================

-- -----------------------------------------------------------------------
-- Step 1: Drop dependent constraints/indexes (may not exist if already dropped)
-- -----------------------------------------------------------------------
ALTER TABLE exam_grade_history DROP CONSTRAINT IF EXISTS fk_grade_history_submission;
ALTER TABLE exam_submissions   DROP CONSTRAINT IF EXISTS fk_submission_exam;
ALTER TABLE exam_submissions   DROP CONSTRAINT IF EXISTS uq_exam_student_attempt;

DROP INDEX IF EXISTS uq_exam_student_attempt;
DROP INDEX IF EXISTS idx_submission_student;
DROP INDEX IF EXISTS idx_exam_course;

-- -----------------------------------------------------------------------
-- Step 2: Truncate exams data — existing rows reference UUID course/student/user
--         values that cannot be mapped to BIGINT; must start clean.
-- -----------------------------------------------------------------------
TRUNCATE TABLE exam_grade_history CASCADE;
TRUNCATE TABLE exam_submissions   CASCADE;
TRUNCATE TABLE exams              CASCADE;

-- -----------------------------------------------------------------------
-- Step 3a: Alter exams table
-- -----------------------------------------------------------------------

-- course_id: UUID → BIGINT (references courses.id BIGINT)
ALTER TABLE exams
    ALTER COLUMN course_id TYPE BIGINT USING NULL::BIGINT;

-- created_by / updated_by: UUID → BIGINT (references users.id BIGINT)
ALTER TABLE exams
    ALTER COLUMN created_by TYPE BIGINT USING NULL::BIGINT;

ALTER TABLE exams
    ALTER COLUMN updated_by TYPE BIGINT USING NULL::BIGINT;

-- Restore NOT NULL on created_by (was nullable after USING NULL trick)
-- We need a placeholder; real FKs set below. Temporarily allow NULL.
-- Restore constraint after re-adding NOT NULL default.
ALTER TABLE exams ALTER COLUMN created_by SET NOT NULL;

-- assigned_teachers column comment update
COMMENT ON COLUMN exams.assigned_teachers IS 'JSON array of Long IDs referencing teaching_staff.id';

-- -----------------------------------------------------------------------
-- Step 3b: Alter exam_submissions table
-- -----------------------------------------------------------------------

-- student_id: UUID → BIGINT (references students.id BIGINT)
ALTER TABLE exam_submissions
    ALTER COLUMN student_id TYPE BIGINT USING NULL::BIGINT;

ALTER TABLE exam_submissions ALTER COLUMN student_id SET NOT NULL;

-- graded_by: UUID → BIGINT (references users.id BIGINT)
ALTER TABLE exam_submissions
    ALTER COLUMN graded_by TYPE BIGINT USING NULL::BIGINT;

-- created_by / updated_by: UUID → BIGINT
ALTER TABLE exam_submissions
    ALTER COLUMN created_by TYPE BIGINT USING NULL::BIGINT;

ALTER TABLE exam_submissions ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE exam_submissions
    ALTER COLUMN updated_by TYPE BIGINT USING NULL::BIGINT;

-- -----------------------------------------------------------------------
-- Step 3c: Alter exam_grade_history table
-- -----------------------------------------------------------------------

-- changed_by: UUID → BIGINT (references users.id BIGINT)
ALTER TABLE exam_grade_history
    ALTER COLUMN changed_by TYPE BIGINT USING NULL::BIGINT;

ALTER TABLE exam_grade_history ALTER COLUMN changed_by SET NOT NULL;

-- created_by / updated_by: UUID → BIGINT
ALTER TABLE exam_grade_history
    ALTER COLUMN created_by TYPE BIGINT USING NULL::BIGINT;

ALTER TABLE exam_grade_history ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE exam_grade_history
    ALTER COLUMN updated_by TYPE BIGINT USING NULL::BIGINT;

-- -----------------------------------------------------------------------
-- Step 4: Re-create indexes
-- -----------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_exam_course      ON exams(course_id);
CREATE INDEX IF NOT EXISTS idx_submission_student ON exam_submissions(student_id);

-- -----------------------------------------------------------------------
-- Step 5: Restore internal FK constraints (intra-module, still UUID→UUID)
-- -----------------------------------------------------------------------
ALTER TABLE exam_submissions
    ADD CONSTRAINT fk_submission_exam
        FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE;

ALTER TABLE exam_grade_history
    ADD CONSTRAINT fk_grade_history_submission
        FOREIGN KEY (submission_id) REFERENCES exam_submissions(id) ON DELETE CASCADE;

-- Restore unique index for exam-student-attempt
CREATE UNIQUE INDEX IF NOT EXISTS uq_exam_student_attempt
    ON exam_submissions(exam_id, student_id, attempt_number);

-- -----------------------------------------------------------------------
-- Step 6: Add cross-module FK constraints (exams → courses/students/users)
-- -----------------------------------------------------------------------
ALTER TABLE exams
    ADD CONSTRAINT fk_exam_course
        FOREIGN KEY (course_id) REFERENCES courses(id);

ALTER TABLE exams
    ADD CONSTRAINT fk_exam_created_by
        FOREIGN KEY (created_by) REFERENCES users(id);

ALTER TABLE exam_submissions
    ADD CONSTRAINT fk_submission_student
        FOREIGN KEY (student_id) REFERENCES students(id);

ALTER TABLE exam_submissions
    ADD CONSTRAINT fk_submission_graded_by
        FOREIGN KEY (graded_by) REFERENCES users(id);

ALTER TABLE exam_submissions
    ADD CONSTRAINT fk_submission_created_by
        FOREIGN KEY (created_by) REFERENCES users(id);

ALTER TABLE exam_grade_history
    ADD CONSTRAINT fk_grade_history_changed_by
        FOREIGN KEY (changed_by) REFERENCES users(id);

ALTER TABLE exam_grade_history
    ADD CONSTRAINT fk_grade_history_created_by
        FOREIGN KEY (created_by) REFERENCES users(id);

-- -----------------------------------------------------------------------
-- Comments
-- -----------------------------------------------------------------------
COMMENT ON COLUMN exams.course_id   IS 'FK → courses.id (BIGINT)';
COMMENT ON COLUMN exams.created_by  IS 'FK → users.id (BIGINT)';
COMMENT ON COLUMN exam_submissions.student_id  IS 'FK → students.id (BIGINT)';
COMMENT ON COLUMN exam_submissions.graded_by   IS 'FK → users.id (BIGINT); NULL until graded';
COMMENT ON COLUMN exam_grade_history.changed_by IS 'FK → users.id (BIGINT)';

