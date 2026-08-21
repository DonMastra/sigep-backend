BEGIN;

ALTER TABLE students
    ADD COLUMN IF NOT EXISTS student_number VARCHAR(32);

UPDATE students
SET student_number = 'SIGEP-' || LPAD(id::text, 12, '0')
WHERE student_number IS NULL OR BTRIM(student_number) = '';

ALTER TABLE students
    ALTER COLUMN student_number SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_students_student_number
    ON students(student_number);

COMMIT;
