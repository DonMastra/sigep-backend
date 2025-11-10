-- ============================================================================
-- Script para corregir el problema de UUID en tablas del módulo exams
-- ============================================================================
--
-- PROBLEMA:
-- Las tablas del módulo exams fueron creadas con columna id de tipo BIGINT IDENTITY
-- pero el código Kotlin usa UUID. Hibernate no puede migrar automáticamente porque
-- PostgreSQL no permite columnas IDENTITY de tipo UUID.
--
-- SOLUCIÓN:
-- Eliminar las tablas y dejar que Hibernate las recree con el tipo correcto.
--
-- IMPACTO:
-- - Se perderán todos los datos de exámenes, calificaciones e historial
-- - Las tablas se recrearán automáticamente al iniciar la aplicación
--
-- CÓMO EJECUTAR:
--
-- Opción 1: Desde pgAdmin o DBeaver
--   1. Conectarse a la base de datos sigep_db
--   2. Copiar y pegar este script
--   3. Ejecutar
--
-- Opción 2: Desde línea de comandos (si tienes psql en PATH)
--   psql -U sigep_user -d sigep_db -f fix-exams-uuid-simple.sql
--
-- Opción 3: Desde Docker (si usas Docker Compose)
--   docker exec -i sigep-postgres psql -U sigep_user -d sigep_db < fix-exams-uuid-simple.sql
--
-- ============================================================================

-- Paso 1: Eliminar tablas en orden de dependencias (CASCADE elimina dependencias automáticamente)
DROP TABLE IF EXISTS exam_grade_history CASCADE;
DROP TABLE IF EXISTS exam_submissions CASCADE;
DROP TABLE IF EXISTS exams CASCADE;

-- Paso 2: Verificar que las tablas fueron eliminadas
SELECT
    CASE
        WHEN COUNT(*) = 0 THEN '✓ Todas las tablas fueron eliminadas correctamente'
        ELSE '✗ ERROR: Algunas tablas no fueron eliminadas'
    END as resultado,
    COUNT(*) as tablas_restantes
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('exams', 'exam_submissions', 'exam_grade_history');

-- ============================================================================
-- PRÓXIMOS PASOS:
-- 1. Iniciar la aplicación Spring Boot
-- 2. Hibernate recreará automáticamente las tablas con UUID
-- 3. Verificar en logs que las tablas se crearon correctamente
-- ============================================================================

