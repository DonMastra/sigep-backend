-- ============================================================================
-- Script de Datos de Prueba - SiGEP Backend
-- ============================================================================
--
-- PROPÓSITO:
-- Insertar datos de prueba en todas las tablas del sistema para desarrollo y testing
--
-- ALCANCE:
-- - Módulo Security: users
-- - Módulo Students: students
-- - Módulo Courses: courses, enrollments, course_sessions, course_materials, course_certificates
-- - Módulo Exams: exams, exam_submissions, exam_grade_history
-- - Módulo Staff: teaching_staff, non_teaching_staff, staff_attendance
--
-- IMPORTANTE:
-- - Solo para ambientes de desarrollo/testing
-- - Las contraseñas están hasheadas con BCrypt (strength 12)
-- - Contraseña para todos los usuarios: "password123"

-- Iniciar transacción
BEGIN;
-- ============================================================================
-- MÓDULO SECURITY: USERS
-- ============================================================================
-- Hash BCrypt para "password123": $2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i

INSERT INTO users (id, username, email, password, first_name, last_name, role, active, created_at, updated_at) VALUES
-- Administradores
(1, 'admin', 'admin@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'Admin', 'Sistema', 'ADMIN', true, NOW() - INTERVAL '60 days', NOW()),
(2, 'carlos.admin', 'carlos.admin@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'Carlos', 'Administrador', 'ADMIN', true, NOW() - INTERVAL '45 days', NOW()),

-- Profesores
(3, 'teacher', 'teacher@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'Juan', 'Profesor', 'TEACHER', true, NOW() - INTERVAL '50 days', NOW()),
(4, 'mgarcia', 'maria.garcia@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'María', 'García', 'TEACHER', true, NOW() - INTERVAL '40 days', NOW()),
(5, 'jperez', 'jose.perez@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'José', 'Pérez', 'TEACHER', true, NOW() - INTERVAL '35 days', NOW()),
(6, 'lmartinez', 'laura.martinez@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'Laura', 'Martínez', 'TEACHER', true, NOW() - INTERVAL '30 days', NOW()),

-- Tutores/Responsables
(7, 'guardian', 'guardian@sigep.edu.mx', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'Pedro', 'Responsable', 'GUARDIAN', true, NOW() - INTERVAL '55 days', NOW()),
(8, 'pgomez', 'patricia.gomez@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'Patricia', 'Gómez', 'GUARDIAN', true, NOW() - INTERVAL '48 days', NOW()),
(9, 'arodriguez', 'alberto.rodriguez@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'Alberto', 'Rodríguez', 'GUARDIAN', true, NOW() - INTERVAL '42 days', NOW()),
(10, 'rlopez', 'rosa.lopez@example.com', '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i', 'Rosa', 'López', 'GUARDIAN', true, NOW() - INTERVAL '38 days', NOW());

-- ============================================================================
-- MÓDULO STUDENTS: STUDENTS
-- ============================================================================

INSERT INTO students (id, first_name, last_name, email, document_number, date_of_birth, address, phone_number, emergency_contact, guardian_id, enrollment_date, active, created_at, updated_at) VALUES
(1, 'Ana', 'Martínez', 'ana.martinez@example.com', 'DNI-12345678', '2010-03-15', 'Calle Principal 123, CABA', '+54-11-1234-5678', 'Patricia Gómez: +54-11-9876-5432', 8, NOW() - INTERVAL '45 days', true, NOW() - INTERVAL '45 days', NOW()),
(2, 'Carlos', 'González', 'carlos.gonzalez@example.com', 'DNI-23456789', '2011-07-22', 'Av. Libertador 456, CABA', '+54-11-2345-6789', 'Alberto Rodríguez: +54-11-8765-4321', 9, NOW() - INTERVAL '40 days', true, NOW() - INTERVAL '40 days', NOW()),
(3, 'Lucía', 'Fernández', 'lucia.fernandez@example.com', 'DNI-34567890', '2010-11-08', 'Belgrano 789, CABA', '+54-11-3456-7890', 'Rosa López: +54-11-7654-3210', 10, NOW() - INTERVAL '38 days', true, NOW() - INTERVAL '38 days', NOW()),
(4, 'Diego', 'Ramírez', 'diego.ramirez@example.com', 'DNI-45678901', '2011-02-14', 'San Martin 234, CABA', '+54-11-4567-8901', 'Patricia Gómez: +54-11-9876-5432', 8, NOW() - INTERVAL '35 days', true, NOW() - INTERVAL '35 days', NOW()),
(5, 'Sofía', 'Torres', 'sofia.torres@example.com', 'DNI-56789012', '2010-09-30', 'Corrientes 567, CABA', '+54-11-5678-9012', 'Alberto Rodríguez: +54-11-8765-4321', 9, NOW() - INTERVAL '32 days', true, NOW() - INTERVAL '32 days', NOW()),
(6, 'Mateo', 'Silva', 'mateo.silva@example.com', 'DNI-67890123', '2011-05-18', 'Santa Fe 890, CABA', '+54-11-6789-0123', 'Rosa López: +54-11-7654-3210', 10, NOW() - INTERVAL '30 days', true, NOW() - INTERVAL '30 days', NOW()),
(7, 'Valentina', 'Morales', 'valentina.morales@example.com', 'DNI-78901234', '2010-12-25', 'Rivadavia 345, CABA', '+54-11-7890-1234', 'Pedro Responsable: +54-11-6543-2109', 7, NOW() - INTERVAL '28 days', true, NOW() - INTERVAL '28 days', NOW()),
(8, 'Benjamín', 'Castro', 'benjamin.castro@example.com', 'DNI-89012345', '2011-04-10', 'Callao 678, CABA', '+54-11-8901-2345', 'Pedro Responsable: +54-11-6543-2109', 7, NOW() - INTERVAL '25 days', true, NOW() - INTERVAL '25 days', NOW());

-- ============================================================================
-- MÓDULO COURSES: COURSES
-- ============================================================================

INSERT INTO courses (id, name, description, level, status, start_date, end_date, max_capacity, schedule, classroom, created_at, updated_at) VALUES
(1, 'English Beginner A1', 'Curso introductorio de inglés nivel básico. Enfocado en gramática fundamental y vocabulario esencial.', 'BEGINNER', 'ACTIVE', NOW() - INTERVAL '30 days', NOW() + INTERVAL '60 days', 15, 'Lunes y Miércoles 10:00-12:00', 'Aula 101', NOW() - INTERVAL '35 days', NOW()),
(2, 'English Intermediate B1', 'Curso de inglés nivel intermedio. Desarrollo de habilidades conversacionales y escritura.', 'INTERMEDIATE', 'ACTIVE', NOW() - INTERVAL '25 days', NOW() + INTERVAL '65 days', 12, 'Martes y Jueves 14:00-16:00', 'Aula 102', NOW() - INTERVAL '30 days', NOW()),
(3, 'English Advanced C1', 'Curso avanzado de inglés. Preparación para certificaciones internacionales.', 'ADVANCED', 'ACTIVE', NOW() - INTERVAL '20 days', NOW() + INTERVAL '70 days', 10, 'Miércoles y Viernes 16:00-18:00', 'Aula 201', NOW() - INTERVAL '25 days', NOW()),
(4, 'English Conversation B2', 'Taller de conversación en inglés nivel intermedio-avanzado. Enfoque en fluidez oral.', 'INTERMEDIATE', 'ACTIVE', NOW() - INTERVAL '15 days', NOW() + INTERVAL '75 days', 8, 'Viernes 18:00-20:00', 'Sala de Conferencias', NOW() - INTERVAL '20 days', NOW());

-- ============================================================================
-- MÓDULO COURSES: ENROLLMENTS
-- ============================================================================

INSERT INTO enrollments (id, student_id, course_id, enrollment_date, status, final_grade, completion_date, notes, created_at, updated_at) VALUES
-- Curso 1: English Beginner A1
(1, 1, 1, NOW() - INTERVAL '30 days', 'ACTIVE', NULL, NULL, 'Estudiante comprometida, asistencia regular', NOW() - INTERVAL '30 days', NOW()),
(2, 2, 1, NOW() - INTERVAL '30 days', 'ACTIVE', NULL, NULL, 'Buen progreso en gramática', NOW() - INTERVAL '30 days', NOW()),
(3, 7, 1, NOW() - INTERVAL '28 days', 'ACTIVE', NULL, NULL, NULL, NOW() - INTERVAL '28 days', NOW()),

-- Curso 2: English Intermediate B1
(4, 3, 2, NOW() - INTERVAL '25 days', 'ACTIVE', NULL, NULL, 'Excelente participación en clase', NOW() - INTERVAL '25 days', NOW()),
(5, 4, 2, NOW() - INTERVAL '25 days', 'ACTIVE', NULL, NULL, NULL, NOW() - INTERVAL '25 days', NOW()),

-- Curso 3: English Advanced C1
(6, 5, 3, NOW() - INTERVAL '20 days', 'ACTIVE', NULL, NULL, 'Preparándose para TOEFL', NOW() - INTERVAL '20 days', NOW()),
(7, 6, 3, NOW() - INTERVAL '20 days', 'ACTIVE', NULL, NULL, 'Nivel muy alto de comprensión', NOW() - INTERVAL '20 days', NOW()),

-- Curso 4: English Conversation B2
(8, 8, 4, NOW() - INTERVAL '15 days', 'ACTIVE', NULL, NULL, 'Gran habilidad conversacional', NOW() - INTERVAL '15 days', NOW());

-- ============================================================================
-- MÓDULO COURSES: COURSE_SESSIONS
-- ============================================================================

INSERT INTO course_sessions (id, course_id, session_number, scheduled_date, topic, status, classroom, notes, created_at, updated_at, created_by, updated_by) VALUES
-- Sesiones Curso 1 (Beginner A1)
(1, 1, 1, NOW() - INTERVAL '28 days', 'Introduction to English - Alphabet and Basic Greetings', 'COMPLETED', 'Aula 101', 'Primera sesión exitosa, 3 estudiantes presentes', NOW() - INTERVAL '28 days', NOW(), 1, 1),
(2, 1, 2, NOW() - INTERVAL '26 days', 'Numbers and Basic Vocabulary', 'COMPLETED', 'Aula 101', NULL, NOW() - INTERVAL '26 days', NOW(), 1, 1),
(3, 1, 3, NOW() + INTERVAL '2 days', 'Present Simple Tense', 'SCHEDULED', 'Aula 101', NULL, NOW() - INTERVAL '25 days', NOW(), 1, NULL),

-- Sesiones Curso 2 (Intermediate B1)
(4, 2, 1, NOW() - INTERVAL '23 days', 'Review of Past Tenses', 'COMPLETED', 'Aula 102', 'Repaso general muy productivo', NOW() - INTERVAL '23 days', NOW(), 1, 1),
(5, 2, 2, NOW() - INTERVAL '21 days', 'Modal Verbs and Conditionals', 'COMPLETED', 'Aula 102', NULL, NOW() - INTERVAL '21 days', NOW(), 1, 1),
(6, 2, 3, NOW() + INTERVAL '3 days', 'Writing Skills - Formal Letters', 'SCHEDULED', 'Aula 102', NULL, NOW() - INTERVAL '20 days', NOW(), 1, NULL),

-- Sesiones Curso 3 (Advanced C1)
(7, 3, 1, NOW() - INTERVAL '18 days', 'Academic Writing Techniques', 'COMPLETED', 'Aula 201', 'Nivel muy alto del grupo', NOW() - INTERVAL '18 days', NOW(), 1, 1),
(8, 3, 2, NOW() + INTERVAL '5 days', 'Advanced Grammar - Subjunctive Mood', 'SCHEDULED', 'Aula 201', NULL, NOW() - INTERVAL '17 days', NOW(), 1, NULL);

-- ============================================================================
-- MÓDULO COURSES: COURSE_MATERIALS
-- ============================================================================

INSERT INTO course_materials (id, course_id, title, description, file_url, uploaded_at, uploaded_by, created_at, updated_at) VALUES
(1, 1, 'English Alphabet Practice Sheet', 'Hoja de práctica con el alfabeto en inglés y ejercicios de pronunciación', '/materials/beginner/alphabet-practice.pdf', NOW() - INTERVAL '30 days', 3, NOW() - INTERVAL '30 days', NOW()),
(2, 1, 'Basic Greetings Audio', 'Audio con pronunciación de saludos básicos en inglés', '/materials/beginner/greetings-audio.mp3', NOW() - INTERVAL '28 days', 3, NOW() - INTERVAL '28 days', NOW()),
(3, 2, 'Modal Verbs Guide', 'Guía completa de verbos modales con ejemplos y ejercicios', '/materials/intermediate/modal-verbs-guide.pdf', NOW() - INTERVAL '22 days', 4, NOW() - INTERVAL '22 days', NOW()),
(4, 3, 'Academic Writing Samples', 'Ejemplos de escritura académica nivel avanzado', '/materials/advanced/academic-writing-samples.pdf', NOW() - INTERVAL '18 days', 5, NOW() - INTERVAL '18 days', NOW());

-- ============================================================================
-- MÓDULO STAFF: TEACHING_STAFF
-- ============================================================================

INSERT INTO teaching_staff (id, first_name, last_name, email, phone_number, document_number, specialization, hire_date, status, monthly_salary, payment_status, qualifications, notes, created_at, updated_at, created_by, updated_by, is_active) VALUES
(1, 'María', 'García', 'maria.garcia@sigep.edu.mx', '+54-11-3344-5566', 'DNI-20123456', 'English Language Teaching', NOW() - INTERVAL '400 days', 'ACTIVE', 85000.00, 'UP_TO_DATE', 'Master in TESOL, Cambridge CELTA', 'Excelente profesora con gran experiencia', NOW() - INTERVAL '400 days', NOW(), 1, 1, true),
(2, 'José', 'Pérez', 'jose.perez@sigep.edu.mx', '+54-11-4455-6677', 'DNI-20234567', 'English Conversation', NOW() - INTERVAL '300 days', 'ACTIVE', 78000.00, 'UP_TO_DATE', 'Bachelor in English Literature, TEFL Certified', 'Especialista en conversación', NOW() - INTERVAL '300 days', NOW(), 1, 1, true),
(3, 'Laura', 'Martínez', 'laura.martinez@sigep.edu.mx', '+54-11-5566-7788', 'DNI-20345678', 'Business English', NOW() - INTERVAL '200 days', 'ACTIVE', 82000.00, 'UP_TO_DATE', 'MBA, Business English Specialist', 'Enfocada en inglés de negocios', NOW() - INTERVAL '200 days', NOW(), 1, 1, true);

-- ============================================================================
-- MÓDULO STAFF: NON_TEACHING_STAFF
-- ============================================================================

INSERT INTO non_teaching_staff (id, first_name, last_name, email, phone_number, position, company, hourly_rate, assigned_tasks, status, created_at, updated_at, created_by, updated_by, is_active) VALUES
(1, 'Roberto', 'Sánchez', 'roberto.sanchez@cleaning.com', '+54-11-6677-8899', 'CLEANING', 'CleanPro Services', 850.00, 'Limpieza de aulas y oficinas, mantenimiento básico', 'ACTIVE', NOW() - INTERVAL '100 days', NOW(), 1, 1, true),
(2, 'Carmen', 'Díaz', 'carmen.diaz@cleaning.com', '+54-11-7788-9900', 'CLEANING', 'CleanPro Services', 850.00, 'Limpieza de baños y áreas comunes', 'ACTIVE', NOW() - INTERVAL '80 days', NOW(), 1, 1, true),
(3, 'Miguel', 'Ruiz', 'miguel.ruiz@techsupport.com', '+54-11-8899-0011', 'IT_SUPPORT', 'TechSupport SA', 1500.00, 'Soporte técnico, mantenimiento de equipos', 'ACTIVE', NOW() - INTERVAL '60 days', NOW(), 1, 1, true);

-- ============================================================================
-- MÓDULO STAFF: STAFF_ATTENDANCE
-- ============================================================================

INSERT INTO staff_attendance (id, teaching_staff_id, staff_id, staff_type, attendance_date, status, hours_worked, notes, created_at, updated_at, created_by, updated_by) VALUES
-- Asistencias de teaching staff
(1, 1, 1, 'TEACHING', NOW() - INTERVAL '5 days', 'PRESENT', 6.0, NULL, NOW() - INTERVAL '5 days', NOW(), 1, 1),
(2, 1, 1, 'TEACHING', NOW() - INTERVAL '3 days', 'PRESENT', 6.0, NULL, NOW() - INTERVAL '3 days', NOW(), 1, 1),
(3, 2, 2, 'TEACHING', NOW() - INTERVAL '4 days', 'PRESENT', 5.0, NULL, NOW() - INTERVAL '4 days', NOW(), 1, 1);

-- Asistencias de non-teaching staff
INSERT INTO staff_attendance (id, non_teaching_staff_id, staff_id, staff_type, attendance_date, status, hours_worked, notes, created_at, updated_at, created_by, updated_by) VALUES
(4, 1, 1, 'NON_TEACHING', NOW() - INTERVAL '2 days', 'PRESENT', 8.0, 'Limpieza completa realizada', NOW() - INTERVAL '2 days', NOW(), 1, 1),
(5, 2, 2, 'NON_TEACHING', NOW() - INTERVAL '2 days', 'PRESENT', 8.0, NULL, NOW() - INTERVAL '2 days', NOW(), 1, 1),
(6, 3, 3, 'NON_TEACHING', NOW() - INTERVAL '1 day', 'PRESENT', 4.0, 'Mantenimiento preventivo equipos', NOW() - INTERVAL '1 day', NOW(), 1, 1);

-- ============================================================================
-- MÓDULO EXAMS: EXAMS
-- ============================================================================
-- Nota: La tabla exams usa UUID para IDs (no BIGINT como otros módulos)
-- Generamos UUIDs deterministas para facilitar referencias posteriores

-- Usar UUIDs fijos para poder referenciarlos en exam_submissions
INSERT INTO exams (id, course_id, title, description, modality, status, total_points, weight, time_limit_minutes, scheduled_at, visibility_start, visibility_end, assigned_teachers, notes, room_info, version, created_at, created_by, updated_at, updated_by) VALUES
-- Exámenes Curso 1 (Beginner A1) - course_id debe convertirse de BIGINT 1 a UUID
('00000000-0000-0000-0000-000000000101'::UUID, '00000000-0000-0000-0000-000000000001'::UUID, 'Unit 1 - Alphabet and Greetings Quiz', 'Evaluación básica sobre alfabeto y saludos en inglés', 'OFFLINE', 'PUBLISHED', 50.00, 0.15, 30, NOW() + INTERVAL '5 days', NOW(), NOW() + INTERVAL '10 days', NULL, 'Primer examen del curso', 'Aula 101', 1, NOW() - INTERVAL '10 days', '00000000-0000-0000-0000-000000000003'::UUID, NOW(), '00000000-0000-0000-0000-000000000003'::UUID),
('00000000-0000-0000-0000-000000000102'::UUID, '00000000-0000-0000-0000-000000000001'::UUID, 'Mid-Term Exam', 'Examen de medio término - Temas 1-4', 'OFFLINE', 'PUBLISHED', 100.00, 0.35, 90, NOW() + INTERVAL '20 days', NOW() + INTERVAL '15 days', NOW() + INTERVAL '25 days', NULL, NULL, 'Aula 101', 1, NOW() - INTERVAL '8 days', '00000000-0000-0000-0000-000000000003'::UUID, NOW(), '00000000-0000-0000-0000-000000000003'::UUID),

-- Exámenes Curso 2 (Intermediate B1) - course_id debe convertirse de BIGINT 2 a UUID
('00000000-0000-0000-0000-000000000201'::UUID, '00000000-0000-0000-0000-000000000002'::UUID, 'Modal Verbs Test', 'Evaluación sobre verbos modales y condicionales', 'OFFLINE', 'GRADED', 75.00, 0.20, 60, NOW() - INTERVAL '3 days', NOW() - INTERVAL '8 days', NOW() + INTERVAL '2 days', NULL, 'Ya calificado', 'Aula 102', 1, NOW() - INTERVAL '15 days', '00000000-0000-0000-0000-000000000004'::UUID, NOW(), '00000000-0000-0000-0000-000000000004'::UUID),
('00000000-0000-0000-0000-000000000202'::UUID, '00000000-0000-0000-0000-000000000002'::UUID, 'Writing Assessment', 'Evaluación de escritura - Carta formal', 'OFFLINE', 'PUBLISHED', 100.00, 0.30, 120, NOW() + INTERVAL '12 days', NOW() + INTERVAL '8 days', NOW() + INTERVAL '18 days', NULL, NULL, 'Aula 102', 1, NOW() - INTERVAL '5 days', '00000000-0000-0000-0000-000000000004'::UUID, NOW(), '00000000-0000-0000-0000-000000000004'::UUID),

-- Exámenes Curso 3 (Advanced C1) - course_id debe convertirse de BIGINT 3 a UUID
('00000000-0000-0000-0000-000000000301'::UUID, '00000000-0000-0000-0000-000000000003'::UUID, 'Academic Writing Exam', 'Examen de escritura académica nivel avanzado', 'OFFLINE', 'PUBLISHED', 100.00, 0.40, 150, NOW() + INTERVAL '15 days', NOW() + INTERVAL '10 days', NOW() + INTERVAL '20 days', NULL, 'Simulación de examen TOEFL', 'Aula 201', 1, NOW() - INTERVAL '7 days', '00000000-0000-0000-0000-000000000005'::UUID, NOW(), '00000000-0000-0000-0000-000000000005'::UUID),
('00000000-0000-0000-0000-000000000302'::UUID, '00000000-0000-0000-0000-000000000003'::UUID, 'Oral Proficiency Test', 'Evaluación oral de fluidez y pronunciación', 'OFFLINE', 'DRAFT', 50.00, 0.25, 20, NULL, NULL, NULL, NULL, 'Examen individual, agendar con cada estudiante', 'Sala de Conferencias', 1, NOW() - INTERVAL '3 days', '00000000-0000-0000-0000-000000000005'::UUID, NULL, NULL);

-- ============================================================================
-- MÓDULO EXAMS: EXAM_SUBMISSIONS
-- ============================================================================
-- Nota: La tabla exam_submissions usa UUID para IDs
-- Usamos UUIDs fijos para mejor legibilidad y referencia

INSERT INTO exam_submissions (id, exam_id, student_id, attempt_number, status, started_at, submitted_at, score, graded_by, graded_at, feedback, scanned_file_path, notes, version, created_at, created_by, updated_at, updated_by) VALUES
-- Submissions para Modal Verbs Test (ID: 00000000-0000-0000-0000-000000000201)
('00000000-0000-0000-0000-000000001001'::UUID,
 '00000000-0000-0000-0000-000000000201'::UUID,  -- Modal Verbs Test
 '00000000-0000-0000-0000-000000000003'::UUID,  -- Student 3
 1, 'GRADED',
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days',
 68.50,
 '00000000-0000-0000-0000-000000000004'::UUID,  -- Teacher mgarcia (user id=4)
 NOW() - INTERVAL '2 days',
 'Buen manejo de modales básicos. Revisar would/could en contexto formal.',
 '/scans/exam_modal_verbs_student3.pdf', NULL, 1,
 NOW() - INTERVAL '3 days', '00000000-0000-0000-0000-000000000003'::UUID,
 NOW() - INTERVAL '2 days', '00000000-0000-0000-0000-000000000004'::UUID),

('00000000-0000-0000-0000-000000001002'::UUID,
 '00000000-0000-0000-0000-000000000201'::UUID,  -- Modal Verbs Test
 '00000000-0000-0000-0000-000000000004'::UUID,  -- Student 4
 1, 'GRADED',
 NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days',
 71.00,
 '00000000-0000-0000-0000-000000000004'::UUID,  -- Teacher mgarcia (user id=4)
 NOW() - INTERVAL '2 days',
 'Excelente comprensión general. Algunos errores menores en might/may.',
 '/scans/exam_modal_verbs_student4.pdf', NULL, 1,
 NOW() - INTERVAL '3 days', '00000000-0000-0000-0000-000000000004'::UUID,
 NOW() - INTERVAL '2 days', '00000000-0000-0000-0000-000000000004'::UUID),

-- Submissions pendientes para Unit 1 Quiz (ID: 00000000-0000-0000-0000-000000000101)
('00000000-0000-0000-0000-000000001003'::UUID,
 '00000000-0000-0000-0000-000000000101'::UUID,  -- Unit 1 Quiz
 '00000000-0000-0000-0000-000000000001'::UUID,  -- Student 1
 1, 'PENDING',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1,
 NOW() - INTERVAL '5 days', '00000000-0000-0000-0000-000000000001'::UUID, NULL, NULL),

('00000000-0000-0000-0000-000000001004'::UUID,
 '00000000-0000-0000-0000-000000000101'::UUID,  -- Unit 1 Quiz
 '00000000-0000-0000-0000-000000000002'::UUID,  -- Student 2
 1, 'PENDING',
 NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1,
 NOW() - INTERVAL '5 days', '00000000-0000-0000-0000-000000000002'::UUID, NULL, NULL);

-- ============================================================================
-- MÓDULO EXAMS: EXAM_GRADE_HISTORY
-- ============================================================================

INSERT INTO exam_grade_history (id, submission_id, previous_score, new_score, change_reason, changed_by, changed_at, version, created_at, created_by, updated_at, updated_by) VALUES
('00000000-0000-0000-0000-000000002001'::UUID,
 '00000000-0000-0000-0000-000000001001'::UUID,  -- Submission de student 3 para Modal Verbs Test
 65.00, 68.50,
 'Recalificación: se otorgaron puntos adicionales por respuesta parcialmente correcta en pregunta 5',
 '00000000-0000-0000-0000-000000000004'::UUID,  -- Teacher mgarcia
 NOW() - INTERVAL '1 day',
 1, NOW() - INTERVAL '1 day',
 '00000000-0000-0000-0000-000000000004'::UUID,
 NOW() - INTERVAL '1 day',
 '00000000-0000-0000-0000-000000000004'::UUID);

-- ============================================================================
-- MÓDULO COURSES: COURSE_CERTIFICATES
-- ============================================================================

INSERT INTO course_certificates (id, enrollment_id, certificate_code, issue_date, expiration_date, certificate_url, issued_by, notes, created_at, updated_at) VALUES
-- Nota: Solo se emiten certificados para enrollments completados
-- Como todos están ACTIVE, este es un ejemplo de cómo se verían
(1, 1, 'CERT-2025-BEG-001', NOW() - INTERVAL '10 days', NOW() + INTERVAL '3 years', '/certificates/2025/CERT-2025-BEG-001.pdf', 1, 'Certificado emitido por completar nivel Beginner A1', NOW() - INTERVAL '10 days', NOW());

-- ============================================================================
-- COMMIT TRANSACTION
-- ============================================================================

COMMIT;

-- ============================================================================
-- VERIFICACIÓN DE DATOS INSERTADOS
-- ============================================================================

SELECT 'Datos insertados exitosamente:' as mensaje;
SELECT '';
SELECT 'users' as tabla, COUNT(*) as registros FROM users
UNION ALL SELECT 'students', COUNT(*) FROM students
UNION ALL SELECT 'courses', COUNT(*) FROM courses
UNION ALL SELECT 'enrollments', COUNT(*) FROM enrollments
UNION ALL SELECT 'course_sessions', COUNT(*) FROM course_sessions
UNION ALL SELECT 'course_materials', COUNT(*) FROM course_materials
UNION ALL SELECT 'teaching_staff', COUNT(*) FROM teaching_staff
UNION ALL SELECT 'non_teaching_staff', COUNT(*) FROM non_teaching_staff
UNION ALL SELECT 'staff_attendance', COUNT(*) FROM staff_attendance
UNION ALL SELECT 'exams', COUNT(*) FROM exams
UNION ALL SELECT 'exam_submissions', COUNT(*) FROM exam_submissions
UNION ALL SELECT 'exam_grade_history', COUNT(*) FROM exam_grade_history
UNION ALL SELECT 'course_certificates', COUNT(*) FROM course_certificates
ORDER BY tabla;

-- ============================================================================
-- INFORMACIÓN DE ACCESO
-- ============================================================================

SELECT '';
SELECT '============================================================================' as info;
SELECT 'USUARIOS DE PRUEBA - Contraseña para todos: password123' as info;
SELECT '============================================================================' as info;
SELECT '';
SELECT 'ADMIN:' as tipo, username, email, role FROM users WHERE role = 'ADMIN'
UNION ALL SELECT 'TEACHER:', username, email, role FROM users WHERE role = 'TEACHER'
UNION ALL SELECT 'GUARDIAN:', username, email, role FROM users WHERE role = 'GUARDIAN'
ORDER BY tipo, username;

-- ============================================================================
-- FIN DEL SCRIPT
-- ============================================================================

