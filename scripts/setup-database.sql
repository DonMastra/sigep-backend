-- SiGEP Database Setup Script
-- PostgreSQL 15+

-- Create database
CREATE DATABASE sigep_db
    WITH
    OWNER = sigep_user
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.UTF-8'
    LC_CTYPE = 'en_US.UTF-8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- Create user
CREATE USER sigep_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    CONNECTION LIMIT -1
    PASSWORD 'sigep_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE sigep_db TO sigep_user;

-- Connect to the database
\c sigep_db

-- Grant schema privileges
GRANT ALL ON SCHEMA public TO sigep_user;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm"; -- For text search

COMMENT ON DATABASE sigep_db IS 'SiGEP - Sistema de Gestión de Enseñanza de Privada';

