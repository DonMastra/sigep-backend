-- ==============================================================================
-- V8: Cleanup legacy schema artifacts (columns + orphan tables)
--
-- Objetivo:
-- 1) Eliminar columnas legacy no usadas por el backend actual
-- 2) Eliminar tablas huérfanas sin entidad JPA activa
--
-- Notas:
-- - Se preserva primero la estructura funcional vigente (V6 + V7)
-- - `course_enrollments` era una tabla many-to-many legacy; la fuente vigente
--   para inscripciones es `enrollments`
-- - `exam_results` quedó obsoleta tras la migración UUID del módulo exams
-- ==============================================================================

-- -----------------------------------------------------------------------
-- Step 1: soltar constraints legacy dependientes
-- -----------------------------------------------------------------------
ALTER TABLE students DROP CONSTRAINT IF EXISTS students_status_check;

-- -----------------------------------------------------------------------
-- Step 2: cleanup de columnas legacy (ignorar si no existen)
-- -----------------------------------------------------------------------
ALTER TABLE students
    DROP COLUMN IF EXISTS phone,
    DROP COLUMN IF EXISTS status;

ALTER TABLE courses
    DROP COLUMN IF EXISTS max_capacity,
    DROP COLUMN IF EXISTS schedule,
    DROP COLUMN IF EXISTS classroom;

ALTER TABLE course_sessions
    DROP COLUMN IF EXISTS session_number,
    DROP COLUMN IF EXISTS scheduled_date,
    DROP COLUMN IF EXISTS classroom,
    DROP COLUMN IF EXISTS created_by,
    DROP COLUMN IF EXISTS updated_by;

ALTER TABLE course_materials
    DROP COLUMN IF EXISTS uploaded_at;

ALTER TABLE course_certificates
    DROP COLUMN IF EXISTS expiration_date,
    DROP COLUMN IF EXISTS certificate_url;

ALTER TABLE exam_grade_history
    DROP COLUMN IF EXISTS change_reason,
    DROP COLUMN IF EXISTS version;

ALTER TABLE staff_attendance
    DROP COLUMN IF EXISTS staff_id,
    DROP COLUMN IF EXISTS staff_type,
    DROP COLUMN IF EXISTS created_by,
    DROP COLUMN IF EXISTS updated_by;

ALTER TABLE teaching_staff
    DROP COLUMN IF EXISTS status;

ALTER TABLE non_teaching_staff
    DROP COLUMN IF EXISTS position,
    DROP COLUMN IF EXISTS company,
    DROP COLUMN IF EXISTS status;

-- -----------------------------------------------------------------------
-- Step 3: eliminar tablas huérfanas
-- -----------------------------------------------------------------------
DROP TABLE IF EXISTS exam_results;
DROP TABLE IF EXISTS course_enrollments;

