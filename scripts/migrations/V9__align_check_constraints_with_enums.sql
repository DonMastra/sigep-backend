-- ==============================================================================
-- V9: Align DB CHECK constraints with backend enums
--
-- Objetivo:
-- 1) exams.status: eliminar valor legacy 'GRADED' (no existe en ExamStatus)
-- 2) non_teaching_staff.role: incluir valor 'IT' (sí existe en NonTeachingRole)
-- ==============================================================================

-- -----------------------------------------------------------------------
-- Step 1: exams.status
-- -----------------------------------------------------------------------
ALTER TABLE exams DROP CONSTRAINT IF EXISTS exams_status_check;

ALTER TABLE exams
    ADD CONSTRAINT exams_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'CANCELLED'));

-- -----------------------------------------------------------------------
-- Step 2: non_teaching_staff.role
-- -----------------------------------------------------------------------
ALTER TABLE non_teaching_staff DROP CONSTRAINT IF EXISTS non_teaching_staff_role_check;

ALTER TABLE non_teaching_staff
    ADD CONSTRAINT non_teaching_staff_role_check
    CHECK (role IN ('CLEANING', 'MAINTENANCE', 'IT_SUPPORT', 'IT', 'SECURITY', 'ADMINISTRATION', 'OTHER'));

