-- Script para corregir el problema de tipo de ID en tablas del módulo exams
-- El módulo exams usa UUID pero las tablas fueron creadas con BIGINT IDENTITY
--
-- IMPORTANTE: Este script eliminará todas las tablas del módulo exams y sus datos
-- Solo ejecutar si no hay datos importantes o si se ha hecho un backup
--
-- Fecha: 2025-11-04
-- Autor: Sistema SiGEP

-- Conectarse a la base de datos sigep_db
\c sigep_db

-- Eliminar tablas en orden inverso de dependencias
DROP TABLE IF EXISTS exam_grade_history CASCADE;
DROP TABLE IF EXISTS exam_submissions CASCADE;
DROP TABLE IF EXISTS exams CASCADE;

-- Las tablas serán recreadas automáticamente por Hibernate con los tipos correctos (UUID)
-- cuando se inicie la aplicación con ddl-auto=update

-- Verificar que las tablas fueron eliminadas
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('exams', 'exam_submissions', 'exam_grade_history');

-- Debería retornar 0 filas si las tablas fueron eliminadas correctamente

