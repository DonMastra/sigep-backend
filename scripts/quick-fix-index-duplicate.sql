-- ============================================================================
-- Script de Solución Rápida para Errores de Índices Duplicados
-- ============================================================================
--
-- PROBLEMA COMÚN:
-- Hibernate con ddl-auto=update intenta crear índices que ya existen
--
-- SÍNTOMAS:
-- ERROR: relation "idx_xxx" already exists
--
-- SOLUCIÓN:
-- Eliminar índices o tablas conflictivas y dejar que Hibernate las recree
--
-- ============================================================================

-- Opción 1: Eliminar solo el índice problemático
-- (Reemplazar 'idx_attendance_date' con el nombre del índice del error)
DROP INDEX IF EXISTS idx_attendance_date CASCADE;

-- Opción 2: Eliminar tabla completa (si hay múltiples problemas)
-- (Reemplazar 'course_attendance' con el nombre de la tabla del error)
DROP TABLE IF EXISTS course_attendance CASCADE;

-- ============================================================================
-- CÓMO EJECUTAR:
--
-- Desde Docker:
-- docker exec -i sigep-postgres psql -U sigep_user -d sigep_db < quick-fix-index.sql
--
-- Desde psql:
-- psql -U sigep_user -d sigep_db -f quick-fix-index.sql
--
-- Desde pgAdmin:
-- Copiar y pegar el comando específico
-- ============================================================================

-- Listar todos los índices (para debug)
SELECT
    schemaname,
    tablename,
    indexname
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;

-- Verificar tablas
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;

