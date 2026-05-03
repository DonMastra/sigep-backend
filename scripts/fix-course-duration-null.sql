-- ============================================================================
-- Script: Fix NULL values in database
-- Descripción: Actualiza todos los registros con valores NULL en campos
--              que el modelo de datos espera como NOT NULL
-- Fecha: 2025-11-14
-- Autor: SiGEP Backend Team
-- ============================================================================

-- ============================================================================
-- TABLA: COURSES
-- Problema: Campos con NULL que deberían tener valores por defecto sensatos
-- ============================================================================

-- 1. Actualizar duration (duración del curso en horas)
UPDATE courses
SET duration = CASE
    WHEN level = 'BEGINNER' THEN 60          -- 60 horas para principiantes
    WHEN level = 'ELEMENTARY' THEN 80        -- 80 horas para elementary
    WHEN level = 'PRE_INTERMEDIATE' THEN 90  -- 90 horas para pre-intermediate
    WHEN level = 'INTERMEDIATE' THEN 100     -- 100 horas para intermediate
    WHEN level = 'UPPER_INTERMEDIATE' THEN 110  -- 110 horas para upper-intermediate
    WHEN level = 'ADVANCED' THEN 120         -- 120 horas para advanced
    WHEN level = 'PROFICIENCY' THEN 130      -- 130 horas para proficiency
    ELSE 60                                  -- Default: 60 horas
END
WHERE duration IS NULL;

-- 2. Actualizar maxStudents (capacidad máxima del curso)
UPDATE courses
SET max_students = CASE
    WHEN level = 'BEGINNER' THEN 15         -- Grupos más grandes para principiantes
    WHEN level = 'INTERMEDIATE' THEN 12     -- Grupos medianos para intermedios
    WHEN level = 'ADVANCED' THEN 10         -- Grupos más pequeños para avanzados
    ELSE 12                                 -- Default: 12 estudiantes
END
WHERE max_students IS NULL;

-- 3. Actualizar minStudents (mínimo de estudiantes para abrir el curso)
UPDATE courses
SET min_students = CASE
    WHEN level = 'BEGINNER' THEN 5          -- Mínimo más alto para principiantes
    WHEN level = 'INTERMEDIATE' THEN 4
    WHEN level = 'ADVANCED' THEN 3          -- Puede abrirse con menos estudiantes
    ELSE 4                                  -- Default: 4 estudiantes
END
WHERE min_students IS NULL;

-- 4. Actualizar teacherId (asignar a un docente por defecto si existe)
-- Primero verificamos si hay docentes en la tabla teaching_staff
DO $$
DECLARE
    default_teacher_id BIGINT;
BEGIN
    -- Obtener el primer docente activo
    SELECT id INTO default_teacher_id
    FROM teaching_staff
    WHERE is_active = true
    LIMIT 1;

    -- Si existe un docente, actualizar los cursos sin teacher
    IF default_teacher_id IS NOT NULL THEN
        UPDATE courses
        SET teacher_id = default_teacher_id
        WHERE teacher_id IS NULL;
    ELSE
        -- Si no hay docentes, dejar un comentario en logs
        RAISE NOTICE 'No hay docentes activos para asignar a cursos sin teacher_id';
    END IF;
END $$;

-- 5. Actualizar price (precio del curso)
UPDATE courses
SET price = CASE
    WHEN level = 'BEGINNER' THEN 15000.00        -- Precio base para principiantes
    WHEN level = 'ELEMENTARY' THEN 16000.00
    WHEN level = 'PRE_INTERMEDIATE' THEN 17000.00
    WHEN level = 'INTERMEDIATE' THEN 18000.00
    WHEN level = 'UPPER_INTERMEDIATE' THEN 19000.00
    WHEN level = 'ADVANCED' THEN 20000.00
    WHEN level = 'PROFICIENCY' THEN 22000.00
    ELSE 15000.00                                -- Default: $15,000
END
WHERE price IS NULL;

-- 6. Actualizar status (estado del curso)
UPDATE courses
SET status = 'ACTIVE'
WHERE status IS NULL;

-- 7. Actualizar isPublished (si el curso está publicado)
UPDATE courses
SET is_published = false  -- Por defecto, los cursos no están publicados
WHERE is_published IS NULL;

-- ============================================================================
-- TABLA: STUDENTS
-- Problema: Campos con NULL que deberían tener valores
-- ============================================================================

-- 8. Actualizar currentLevel para estudiantes sin nivel asignado
UPDATE students
SET current_level = 'BEGINNER'
WHERE current_level IS NULL;

-- 9. Actualizar status para estudiantes
UPDATE students
SET status = 'ACTIVE'
WHERE status IS NULL;

-- 10. Actualizar guardianId (responsable) - asignar un valor temporal si no existe
-- Nota: Esto debe revisarse manualmente y asignar el guardian correcto
UPDATE students
SET guardian_id = 0  -- 0 indica "sin responsable asignado" - REVISAR MANUALMENTE
WHERE guardian_id IS NULL;

-- ============================================================================
-- TABLA: ENROLLMENTS
-- Problema: Campos con NULL
-- ============================================================================

-- 11. Actualizar status de enrollments
UPDATE enrollments
SET status = 'ACTIVE'
WHERE status IS NULL;

-- ============================================================================
-- TABLA: COURSE_SESSIONS
-- Problema: Sesiones de curso sin información completa
-- ============================================================================

-- 12. Actualizar status de course_sessions
UPDATE course_sessions
SET status = 'SCHEDULED'
WHERE status IS NULL;

-- 13. Actualizar isRecurring (si es una sesión recurrente)
UPDATE course_sessions
SET is_recurring = false
WHERE is_recurring IS NULL;

-- ============================================================================
-- TABLA: COURSE_MATERIALS
-- Problema: Materiales sin información completa
-- ============================================================================

-- 14. Actualizar isVisible para materiales
UPDATE course_materials
SET is_visible = true  -- Por defecto, los materiales son visibles
WHERE is_visible IS NULL;

-- 15. Actualizar uploadedBy (quién subió el material)
DO $$
DECLARE
    default_admin_id BIGINT;
BEGIN
    -- Obtener el primer usuario administrador
    SELECT id INTO default_admin_id
    FROM users
    WHERE role = 'ADMIN'
    LIMIT 1;

    IF default_admin_id IS NOT NULL THEN
        UPDATE course_materials
        SET uploaded_by = default_admin_id
        WHERE uploaded_by IS NULL;
    END IF;
END $$;

-- ============================================================================
-- TABLA: TEACHING_STAFF
-- Problema: Staff sin datos completos
-- ============================================================================

-- 16. Actualizar isActive para staff
UPDATE teaching_staff
SET is_active = true
WHERE is_active IS NULL;

-- 17. Actualizar monthlySalary con valor base si está NULL
UPDATE teaching_staff
SET monthly_salary = 50000.00  -- Salario base por defecto
WHERE monthly_salary IS NULL;

-- 18. Actualizar paymentStatus
UPDATE teaching_staff
SET payment_status = 'UP_TO_DATE'
WHERE payment_status IS NULL;

-- 19. Actualizar assignedStudentsCount
UPDATE teaching_staff
SET assigned_students_count = 0
WHERE assigned_students_count IS NULL;

-- ============================================================================
-- TABLA: NON_TEACHING_STAFF
-- Problema: Personal no docente sin datos completos
-- ============================================================================

-- 20. Actualizar isActive para non-teaching staff
UPDATE non_teaching_staff
SET is_active = true
WHERE is_active IS NULL;

-- 21. Actualizar hourlyRate con valor base
UPDATE non_teaching_staff
SET hourly_rate = 1000.00  -- Tarifa por hora base
WHERE hourly_rate IS NULL;

-- ============================================================================
-- VERIFICACIONES
-- ============================================================================

-- Verificar courses
SELECT
    'COURSES' as tabla,
    COUNT(*) FILTER (WHERE duration IS NULL) as duration_null,
    COUNT(*) FILTER (WHERE max_students IS NULL) as max_students_null,
    COUNT(*) FILTER (WHERE min_students IS NULL) as min_students_null,
    COUNT(*) FILTER (WHERE teacher_id IS NULL) as teacher_id_null,
    COUNT(*) FILTER (WHERE price IS NULL) as price_null,
    COUNT(*) FILTER (WHERE status IS NULL) as status_null
FROM courses;

-- Verificar students
SELECT
    'STUDENTS' as tabla,
    COUNT(*) FILTER (WHERE current_level IS NULL) as level_null,
    COUNT(*) FILTER (WHERE status IS NULL) as status_null,
    COUNT(*) FILTER (WHERE guardian_id IS NULL) as guardian_null
FROM students;

-- Verificar enrollments
SELECT
    'ENROLLMENTS' as tabla,
    COUNT(*) FILTER (WHERE status IS NULL) as status_null
FROM enrollments;

-- Verificar teaching_staff
SELECT
    'TEACHING_STAFF' as tabla,
    COUNT(*) FILTER (WHERE is_active IS NULL) as active_null,
    COUNT(*) FILTER (WHERE monthly_salary IS NULL) as salary_null,
    COUNT(*) FILTER (WHERE payment_status IS NULL) as payment_null
FROM teaching_staff;

-- ============================================================================
-- RESUMEN FINAL
-- ============================================================================

SELECT
    'RESUMEN' as tipo,
    (SELECT COUNT(*) FROM courses WHERE duration IS NOT NULL) as courses_with_duration,
    (SELECT COUNT(*) FROM students WHERE current_level IS NOT NULL) as students_with_level,
    (SELECT COUNT(*) FROM teaching_staff WHERE is_active IS NOT NULL) as staff_active;

-- ============================================================================
-- NOTAS IMPORTANTES
-- ============================================================================
--
-- 1. Este script actualiza valores NULL con datos sensatos por defecto
-- 2. Algunos campos como guardianId requieren revisión manual
-- 3. Los precios y salarios son valores de ejemplo, ajustar según necesidad
-- 4. Ejecutar en ambiente de desarrollo primero
-- 5. Hacer backup de la base de datos antes de ejecutar en producción
--
-- ============================================================================


