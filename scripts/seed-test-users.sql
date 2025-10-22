-- Script de inicialización de usuarios de prueba para desarrollo
-- Contraseñas: todas son "password123" (encriptadas con BCrypt strength 12)
-- BCrypt hash de "password123": $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia

-- Limpiar datos existentes (solo en desarrollo)
-- TRUNCATE TABLE users CASCADE;

-- Usuario ADMIN
INSERT INTO users (username, email, password, first_name, last_name, role, active, created_at, updated_at)
VALUES ('admin', 'admin@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Admin', 'Sistema', 'ADMIN', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, first_name, last_name, role, active, created_at, updated_at)
VALUES ('admin2', 'admin2@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'María', 'Administradora', 'ADMIN', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Usuario TEACHER
INSERT INTO users (username, email, password, first_name, last_name, role, active, created_at, updated_at)
VALUES ('teacher', 'teacher@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Juan', 'Profesor', 'TEACHER', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, first_name, last_name, role, active, created_at, updated_at)
VALUES ('teacher2', 'teacher2@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Laura', 'Docente', 'TEACHER', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Usuario GUARDIAN
INSERT INTO users (username, email, password, first_name, last_name, role, active, created_at, updated_at)
VALUES ('guardian', 'guardian@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Pedro', 'Responsable', 'GUARDIAN', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, first_name, last_name, role, active, created_at, updated_at)
VALUES ('guardian2', 'guardian2@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TuPxIRQXLYxCcBhRc85Xo5XbN0Ia', 'Ana', 'Tutora', 'GUARDIAN', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Verificar que los usuarios se crearon correctamente
SELECT id, username, email, first_name, last_name, role, active FROM users ORDER BY role, id;

