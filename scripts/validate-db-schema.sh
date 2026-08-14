#!/bin/bash
# Database Schema Validation Script for SiGEP Backend
# Purpose: Verify that all new User profile columns exist and are correctly configured
# Usage: ./validate-db-schema.sh (requires psql installed)

set -e

DB_HOST="${DATABASE_HOST:-localhost}"
DB_PORT="${DATABASE_PORT:-5432}"
DB_NAME="${DATABASE_NAME:-sigep_db}"
DB_USER="${DATABASE_USERNAME:-sigep_user}"

echo "🔍 Validando esquema de BD SiGEP..."
echo "   Host: $DB_HOST"
echo "   Puerto: $DB_PORT"
echo "   BD: $DB_NAME"
echo ""

# Conectar a BD y verificar columnas
QUERY="
SELECT
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'users'
ORDER BY ordinal_position;
"

echo "📋 Columnas de tabla 'users':"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$QUERY"

echo ""
echo "🔑 Verificando restricciones UNIQUE y CHECK..."
CONSTRAINTS_QUERY="
SELECT
    constraint_name,
    constraint_type,
    table_name
FROM information_schema.table_constraints
WHERE table_name = 'users'
ORDER BY constraint_name;
"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$CONSTRAINTS_QUERY"

echo ""
echo "📑 Verificando índices en tabla 'users'..."
INDEXES_QUERY="
SELECT
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename = 'users'
ORDER BY indexname;
"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$INDEXES_QUERY"

echo ""
echo "✅ Validación completada!"
echo ""
echo "📌 Campos esperados (nuevos):"
echo "   - phone_number VARCHAR(20) NULL"
echo "   - address VARCHAR(500) NULL"
echo "   - date_of_birth DATE NULL"
echo "   - document_number VARCHAR(50) NULL"
echo "   - emergency_contact VARCHAR(255) NULL"

