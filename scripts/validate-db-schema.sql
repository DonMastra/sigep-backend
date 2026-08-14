-- ============================================================================
-- Database Schema Validation Script for SiGEP Backend
-- Purpose: Verify all User profile columns exist and are correctly configured
-- Compatible with: PostgreSQL 15+, DBeaver, psql
-- ============================================================================

-- ============================================================================
-- 1. COLUMNAS DE TABLA 'users'
-- ============================================================================
-- Esperadas: phone_number, address, date_of_birth, document_number, emergency_contact

SELECT
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'users'
AND table_catalog = current_database()
AND table_schema = 'public'
ORDER BY ordinal_position;

-- ============================================================================
-- 2. RESTRICCIONES DE TABLA 'users'
-- ============================================================================
-- Esperadas: users_pkey, users_username_key, users_email_key, users_document_number_unique_not_null, users_status_check

SELECT
    constraint_name,
    constraint_type,
    table_name
FROM information_schema.table_constraints
WHERE table_name = 'users'
AND table_catalog = current_database()
AND table_schema = 'public'
ORDER BY constraint_name;

-- ============================================================================
-- 3. ÍNDICES DE TABLA 'users'
-- ============================================================================
-- Esperados: users_pkey, idx_users_document_number

SELECT
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename = 'users'
AND schemaname = 'public'
ORDER BY indexname;

-- ============================================================================
-- 4. VALIDACIÓN DE NUEVAS COLUMNAS (existencia)
-- ============================================================================

SELECT
    CASE WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='users' AND column_name='phone_number'
    ) THEN '✓ phone_number EXISTS' ELSE '✗ phone_number MISSING' END as phone_number_status,

    CASE WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='users' AND column_name='address'
    ) THEN '✓ address EXISTS' ELSE '✗ address MISSING' END as address_status,

    CASE WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='users' AND column_name='date_of_birth'
    ) THEN '✓ date_of_birth EXISTS' ELSE '✗ date_of_birth MISSING' END as date_of_birth_status,

    CASE WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='users' AND column_name='document_number'
    ) THEN '✓ document_number EXISTS' ELSE '✗ document_number MISSING' END as document_number_status,

    CASE WHEN EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='users' AND column_name='emergency_contact'
    ) THEN '✓ emergency_contact EXISTS' ELSE '✗ emergency_contact MISSING' END as emergency_contact_status;

-- ============================================================================
-- RESUMEN DE VALIDACIÓN COMPLETADO
-- ============================================================================
-- Si todas las columnas aparecen como "EXISTS" y todos los índices se muestran,
-- la validación ha sido EXITOSA.
--
-- Campos esperados (nuevos):
--   ✓ phone_number VARCHAR(20) NULL
--   ✓ address VARCHAR(500) NULL
--   ✓ date_of_birth DATE NULL
--   ✓ document_number VARCHAR(50) NULL
--   ✓ emergency_contact VARCHAR(255) NULL
--
-- Restricciones esperadas:
--   ✓ users_pkey (PRIMARY KEY)
--   ✓ users_username_key (UNIQUE)
--   ✓ users_email_key (UNIQUE)
--   ✓ users_document_number_unique_not_null (UNIQUE, NULLS DISTINCT)
--   ✓ users_status_check (CHECK)
--
-- Índices esperados:
--   ✓ users_pkey
--   ✓ idx_users_document_number
-- ============================================================================


