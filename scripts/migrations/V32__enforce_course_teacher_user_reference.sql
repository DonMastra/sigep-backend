-- V32 - courses.teacher_id references the account used to authenticate the teacher.
-- Manual PostgreSQL/Neon migration. Apply after the teacher linkage repair succeeds.

BEGIN;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM courses course
        LEFT JOIN users account ON account.id = course.teacher_id
        WHERE course.teacher_id IS NOT NULL AND account.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot add course teacher FK: orphan teacher_id values exist';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_courses_teacher_user'
          AND conrelid = 'courses'::regclass
    ) THEN
        ALTER TABLE courses
            ADD CONSTRAINT fk_courses_teacher_user
            FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_courses_teacher_id
    ON courses(teacher_id) WHERE teacher_id IS NOT NULL;

COMMIT;
