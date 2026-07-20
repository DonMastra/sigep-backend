-- Script de inicializacion de usuarios de prueba para desarrollo/QA
-- Password de todos los usuarios: "password123" (BCrypt strength 12)
-- BCrypt hash valido de "password123" (strength 12): $2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm

-- Limpiar datos existentes solo si se necesita reiniciar el ambiente:
-- TRUNCATE TABLE users CASCADE;

-- Usuario ADMIN
INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('admin', 'admin@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Admin', 'Sistema', 'ADMIN', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('admin2', 'admin2@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Maria', 'Administradora', 'ADMIN', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Usuario TEACHER
INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('teacher', 'teacher@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Juan', 'Profesor', 'TEACHER', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('teacher2', 'teacher2@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Laura', 'Docente', 'TEACHER', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Usuario GUARDIAN
INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('guardian', 'guardian@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Pedro', 'Responsable', 'GUARDIAN', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, email, password, first_name, last_name, role, status, active, created_at, updated_at)
VALUES ('guardian2', 'guardian2@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Ana', 'Tutora', 'GUARDIAN', 'ACTIVE', true, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- Verificar que los usuarios se crearon correctamente
SELECT id, username, email, first_name, last_name, role, status, active
FROM users
ORDER BY role, id;
