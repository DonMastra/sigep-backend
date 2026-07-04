-- Script de inicializacion de usuarios de prueba para desarrollo/QA
-- Password de todos los usuarios: "password123" (BCrypt strength 12)
-- BCrypt hash de "password123": $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia

-- Limpiar datos existentes solo si se necesita reiniciar el ambiente:
-- TRUNCATE TABLE users CASCADE;

-- Usuario ADMIN
INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('admin', 'admin@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Admin', 'Sistema', 'ADMIN', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('admin2', 'admin2@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Maria', 'Administradora', 'ADMIN', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Usuario TEACHER
INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('teacher', 'teacher@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Juan', 'Profesor', 'TEACHER', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('teacher2', 'teacher2@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Laura', 'Docente', 'TEACHER', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Usuario GUARDIAN
INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('guardian', 'guardian@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Pedro', 'Responsable', 'GUARDIAN', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('guardian2', 'guardian2@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Ana', 'Tutora', 'GUARDIAN', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Verificar que los usuarios se crearon correctamente
SELECT id, username, email, first_name, last_name, role, status, active
FROM users
ORDER BY role, id;
