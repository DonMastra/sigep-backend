-- ============================================================================
-- Script de Datos de Prueba - SiGEP Backend (POST-V9)
-- ============================================================================
-- Objetivo:
-- - Poblar TODAS las tablas activas del esquema actual.
-- - Insertar valores explícitos en TODAS las columnas de cada tabla.
-- - Mantener consistencia con FKs, CHECKs y UNIQUE vigentes.
--
-- Tablas cubiertas (18):
-- users, students, courses, course_schedules, enrollments, course_sessions,
-- session_exceptions, course_attendance, course_materials, course_certificates,
-- teaching_staff, non_teaching_staff, staff_attendance, exams,
-- exam_submissions, exam_grade_history, payments, notifications
--
-- PRECONDICION RECOMENDADA:
-- Ejecutar con tablas vacías (ver comando de TRUNCATE en la guía de ejecución).

BEGIN;

-- ============================================================================
-- USERS
-- ============================================================================
-- BCrypt hash valido para "password123" (strength 12)
INSERT INTO users (
    id, username, email, password, first_name, last_name, role, active, created_at, updated_at
) VALUES
    (1, 'admin', 'admin@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Admin', 'Sistema', 'ADMIN', true, NOW() - INTERVAL '120 days', NOW() - INTERVAL '1 days'),
    (2, 'coordinator', 'coordinator@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Carla', 'Coordinadora', 'ADMIN', true, NOW() - INTERVAL '100 days', NOW() - INTERVAL '2 days'),
    (3, 'teacher.juan', 'juan.teacher@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Juan', 'Paredes', 'TEACHER', true, NOW() - INTERVAL '90 days', NOW() - INTERVAL '3 days'),
    (4, 'teacher.maria', 'maria.teacher@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Maria', 'Gomez', 'TEACHER', true, NOW() - INTERVAL '88 days', NOW() - INTERVAL '4 days'),
    (5, 'teacher.luis', 'luis.teacher@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Luis', 'Romero', 'TEACHER', true, NOW() - INTERVAL '85 days', NOW() - INTERVAL '5 days'),
    (6, 'guardian.ana', 'ana.guardian@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Ana', 'Lopez', 'GUARDIAN', true, NOW() - INTERVAL '80 days', NOW() - INTERVAL '2 days'),
    (7, 'guardian.pablo', 'pablo.guardian@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Pablo', 'Diaz', 'GUARDIAN', true, NOW() - INTERVAL '78 days', NOW() - INTERVAL '2 days'),
    (8, 'guardian.sofia', 'sofia.guardian@sigep.edu.mx', '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm', 'Sofia', 'Ruiz', 'GUARDIAN', true, NOW() - INTERVAL '75 days', NOW() - INTERVAL '1 days');

-- ============================================================================
-- STUDENTS
-- ============================================================================
INSERT INTO students (
    id, first_name, last_name, email, document_number, date_of_birth, address,
    phone_number, emergency_contact, guardian_id, enrollment_date, active,
    medical_notes, current_level, created_at, updated_at
) VALUES
    (1, 'Lara', 'Mendez', 'lara.mendez@student.sigep.edu.mx', 'STU-1001', '2011-03-10', 'Av. Rivadavia 1234', '+54-11-4000-1001', 'Ana Lopez +54-11-5000-1001', 6, '2026-01-20', true, 'Alergia estacional', 'BEGINNER', NOW() - INTERVAL '70 days', NOW() - INTERVAL '1 days'),
    (2, 'Tomas', 'Rios', 'tomas.rios@student.sigep.edu.mx', 'STU-1002', '2010-11-25', 'Av. Cabildo 2200', '+54-11-4000-1002', 'Pablo Diaz +54-11-5000-1002', 7, '2026-01-22', true, 'Sin observaciones', 'ELEMENTARY', NOW() - INTERVAL '68 days', NOW() - INTERVAL '1 days'),
    (3, 'Camila', 'Suarez', 'camila.suarez@student.sigep.edu.mx', 'STU-1003', '2011-07-14', 'Av. Santa Fe 3300', '+54-11-4000-1003', 'Sofia Ruiz +54-11-5000-1003', 8, '2026-01-25', true, 'Usa lentes', 'PRE_INTERMEDIATE', NOW() - INTERVAL '65 days', NOW() - INTERVAL '2 days'),
    (4, 'Nicolas', 'Acosta', 'nicolas.acosta@student.sigep.edu.mx', 'STU-1004', '2010-02-18', 'Av. Corrientes 1500', '+54-11-4000-1004', 'Ana Lopez +54-11-5000-1004', 6, '2026-01-28', true, 'Sin observaciones', 'INTERMEDIATE', NOW() - INTERVAL '62 days', NOW() - INTERVAL '2 days'),
    (5, 'Valentina', 'Paz', 'valentina.paz@student.sigep.edu.mx', 'STU-1005', '2011-09-02', 'Av. Belgrano 980', '+54-11-4000-1005', 'Pablo Diaz +54-11-5000-1005', 7, '2026-02-01', true, 'Apto fisico presentado', 'UPPER_INTERMEDIATE', NOW() - INTERVAL '59 days', NOW() - INTERVAL '3 days'),
    (6, 'Bruno', 'Farias', 'bruno.farias@student.sigep.edu.mx', 'STU-1006', '2010-05-30', 'Av. Callao 245', '+54-11-4000-1006', 'Sofia Ruiz +54-11-5000-1006', 8, '2026-02-04', true, 'Sin observaciones', 'ADVANCED', NOW() - INTERVAL '56 days', NOW() - INTERVAL '3 days');

-- ============================================================================
-- COURSES
-- ============================================================================
INSERT INTO courses (
    id, code, name, description, level, duration, max_students, min_students,
    teacher_id, price, start_date, end_date, status, is_published, created_at, updated_at
) VALUES
    (1, 'ENG-A1-2026', 'English Beginner A1', 'Curso inicial para nivel A1 con foco en speaking y bases gramaticales.', 'BEGINNER', 120, 16, 6, 3, 45000.00, '2026-02-01', '2026-07-01', 'ACTIVE', true, NOW() - INTERVAL '80 days', NOW() - INTERVAL '2 days'),
    (2, 'ENG-B1-2026', 'English Intermediate B1', 'Curso intermedio para consolidar tiempos verbales y writing.', 'INTERMEDIATE', 140, 14, 5, 4, 52000.00, '2026-02-05', '2026-07-10', 'ACTIVE', true, NOW() - INTERVAL '78 days', NOW() - INTERVAL '2 days'),
    (3, 'ENG-C1-2026', 'English Advanced C1', 'Curso avanzado orientado a certificaciones internacionales.', 'ADVANCED', 160, 12, 5, 5, 61000.00, '2026-02-10', '2026-07-15', 'ACTIVE', true, NOW() - INTERVAL '75 days', NOW() - INTERVAL '2 days'),
    (4, 'ENG-CONV-2026', 'Conversation Lab', 'Taller intensivo de conversacion para fluidez oral.', 'UPPER_INTERMEDIATE', 80, 10, 4, 4, 39000.00, '2026-02-12', '2026-06-20', 'INACTIVE', false, NOW() - INTERVAL '72 days', NOW() - INTERVAL '3 days');

-- ============================================================================
-- COURSE_SCHEDULES
-- ============================================================================
INSERT INTO course_schedules (
    id, course_id, day_of_week, start_time, end_time
) VALUES
    (1, 1, 'MONDAY', '09:00', '11:00'),
    (2, 1, 'WEDNESDAY', '09:00', '11:00'),
    (3, 2, 'TUESDAY', '14:00', '16:00'),
    (4, 2, 'THURSDAY', '14:00', '16:00'),
    (5, 3, 'MONDAY', '17:00', '19:00'),
    (6, 3, 'FRIDAY', '17:00', '19:00'),
    (7, 4, 'SATURDAY', '10:00', '12:00'),
    (8, 4, 'SUNDAY', '10:00', '12:00');

-- ============================================================================
-- ENROLLMENTS
-- ============================================================================
INSERT INTO enrollments (
    id, student_id, course_id, enrollment_date, status, final_grade, completion_date,
    notes, created_at, updated_at
) VALUES
    (1, 1, 1, '2026-02-01', 'COMPLETED', 88.50, '2026-04-15', 'Completado con buen desempeno', NOW() - INTERVAL '70 days', NOW() - INTERVAL '10 days'),
    (2, 2, 1, '2026-02-02', 'COMPLETED', 91.00, '2026-04-15', 'Excelente participacion', NOW() - INTERVAL '69 days', NOW() - INTERVAL '10 days'),
    (3, 3, 2, '2026-02-06', 'ACTIVE', NULL, NULL, 'Asistencia regular', NOW() - INTERVAL '65 days', NOW() - INTERVAL '2 days'),
    (4, 4, 2, '2026-02-06', 'ACTIVE', NULL, NULL, 'Buen rendimiento', NOW() - INTERVAL '65 days', NOW() - INTERVAL '2 days'),
    (5, 5, 3, '2026-02-11', 'ACTIVE', NULL, NULL, 'Enfocada en certificacion', NOW() - INTERVAL '60 days', NOW() - INTERVAL '2 days'),
    (6, 6, 3, '2026-02-11', 'SUSPENDED', 55.00, '2026-03-20', 'Suspension temporal por inasistencias', NOW() - INTERVAL '60 days', NOW() - INTERVAL '20 days');

-- ============================================================================
-- COURSE_SESSIONS
-- ============================================================================
INSERT INTO course_sessions (
    id, course_id, session_date, start_time, end_time, classroom_id, classroom_name,
    status, topic, notes, is_recurring, recurrence_rule, parent_session_id,
    created_at, updated_at
) VALUES
    (1, 1, '2026-03-01', '09:00', '11:00', 101, 'Aula 101', 'COMPLETED', 'Greetings and introductions', 'Sesion dictada sin novedades', true, 'FREQ=WEEKLY;BYDAY=MO,WE', NULL, NOW() - INTERVAL '40 days', NOW() - INTERVAL '39 days'),
    (2, 1, '2026-03-03', '09:00', '11:00', 101, 'Aula 101', 'COMPLETED', 'Present simple basics', 'Participacion alta', true, 'FREQ=WEEKLY;BYDAY=MO,WE', 1, NOW() - INTERVAL '38 days', NOW() - INTERVAL '37 days'),
    (3, 2, '2026-03-04', '14:00', '16:00', 102, 'Aula 102', 'COMPLETED', 'Modal verbs review', 'Sesion con evaluacion corta', true, 'FREQ=WEEKLY;BYDAY=TU,TH', NULL, NOW() - INTERVAL '37 days', NOW() - INTERVAL '36 days'),
    (4, 2, '2026-03-06', '14:00', '16:00', 102, 'Aula 102', 'RESCHEDULED', 'Conditionals practice', 'Reprogramada por feriado local', true, 'FREQ=WEEKLY;BYDAY=TU,TH', 3, NOW() - INTERVAL '35 days', NOW() - INTERVAL '34 days'),
    (5, 3, '2026-03-08', '17:00', '19:00', 201, 'Aula 201', 'IN_PROGRESS', 'Academic writing', 'Trabajo en ensayo', true, 'FREQ=WEEKLY;BYDAY=MO,FR', NULL, NOW() - INTERVAL '33 days', NOW() - INTERVAL '32 days'),
    (6, 4, '2026-03-09', '10:00', '12:00', 301, 'Sala Conversacion', 'CANCELLED', 'Conversation drills', 'Cancelada por mantenimiento', false, 'FREQ=DAILY;COUNT=1', 5, NOW() - INTERVAL '32 days', NOW() - INTERVAL '31 days');

-- ============================================================================
-- SESSION_EXCEPTIONS
-- ============================================================================
INSERT INTO session_exceptions (
    id, session_id, exception_date, exception_type, new_start_time, new_end_time,
    new_classroom_id, reason, created_at
) VALUES
    (1, 4, '2026-03-10', 'RESCHEDULED', '15:00', '17:00', 103, 'Ajuste por feriado', NOW() - INTERVAL '34 days'),
    (2, 6, '2026-03-09', 'CANCELLED', '10:30', '12:30', 302, 'Mantenimiento edilicio', NOW() - INTERVAL '31 days');

-- ============================================================================
-- COURSE_ATTENDANCE
-- ============================================================================
INSERT INTO course_attendance (
    id, enrollment_id, attendance_date, status, notes, recorded_by, created_at, updated_at
) VALUES
    (1, 1, '2026-03-01', 'PRESENT', 'Puntual y participativa', 3, NOW() - INTERVAL '40 days', NOW() - INTERVAL '40 days'),
    (2, 2, '2026-03-01', 'LATE', 'Ingreso 10 minutos tarde', 3, NOW() - INTERVAL '40 days', NOW() - INTERVAL '40 days'),
    (3, 3, '2026-03-04', 'ABSENT', 'Ausente sin aviso', 4, NOW() - INTERVAL '37 days', NOW() - INTERVAL '37 days'),
    (4, 4, '2026-03-04', 'EXCUSED_ABSENCE', 'Certificado medico presentado', 4, NOW() - INTERVAL '37 days', NOW() - INTERVAL '37 days'),
    (5, 5, '2026-03-08', 'SICK_LEAVE', 'Reposo por gripe', 5, NOW() - INTERVAL '33 days', NOW() - INTERVAL '33 days'),
    (6, 6, '2026-03-08', 'PRESENT', 'Asistencia normal', 5, NOW() - INTERVAL '33 days', NOW() - INTERVAL '33 days');

-- ============================================================================
-- COURSE_MATERIALS
-- ============================================================================
INSERT INTO course_materials (
    id, course_id, title, description, type, file_url, file_name, file_size,
    mime_type, uploaded_by, is_visible, order_index, created_at, updated_at
) VALUES
    (1, 1, 'A1 Starter Pack', 'Guia inicial de vocabulario y gramatica', 'PDF', '/materials/a1/starter-pack.pdf', 'starter-pack.pdf', 245760, 'application/pdf', 3, true, 1, NOW() - INTERVAL '50 days', NOW() - INTERVAL '48 days'),
    (2, 2, 'Modal Verbs Audio', 'Practica auditiva de modal verbs', 'AUDIO', '/materials/b1/modal-verbs.mp3', 'modal-verbs.mp3', 5242880, 'audio/mpeg', 4, true, 2, NOW() - INTERVAL '45 days', NOW() - INTERVAL '43 days'),
    (3, 3, 'Academic Writing Slides', 'Presentacion de writing academico', 'PRESENTATION', '/materials/c1/academic-writing.pptx', 'academic-writing.pptx', 7340032, 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 5, true, 3, NOW() - INTERVAL '40 days', NOW() - INTERVAL '38 days'),
    (4, 4, 'Conversation Video', 'Video de role-plays para speaking', 'VIDEO', '/materials/conv/roleplays.mp4', 'roleplays.mp4', 15728640, 'video/mp4', 4, false, 4, NOW() - INTERVAL '35 days', NOW() - INTERVAL '34 days');

-- ============================================================================
-- COURSE_CERTIFICATES
-- ============================================================================
INSERT INTO course_certificates (
    id, enrollment_id, certificate_code, issue_date, expiry_date, final_grade, honors,
    notes, pdf_url, status, issued_by, revoked_by, revoked_at, revocation_reason,
    created_at, updated_at
) VALUES
    (1, 1, 'CERT-2026-A1-0001', '2026-04-20', '2029-04-20', 88.50, 'MERIT', 'Certificado por aprobacion del nivel A1', '/certs/CERT-2026-A1-0001.pdf', 'ACTIVE', 1, NULL, NULL, NULL, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (2, 2, 'CERT-2026-A1-0002', '2026-04-20', '2029-04-20', 91.00, 'DISTINCTION', 'Certificado revocado por correccion administrativa', '/certs/CERT-2026-A1-0002.pdf', 'REVOKED', 2, 1, NOW() - INTERVAL '5 days', 'Error de datos en documento original', NOW() - INTERVAL '10 days', NOW() - INTERVAL '4 days');

-- ============================================================================
-- TEACHING_STAFF
-- ============================================================================
INSERT INTO teaching_staff (
    id, first_name, last_name, email, phone_number, document_number, birth_date,
    address, hire_date, monthly_salary, payment_status, assigned_students_count,
    specialization, qualifications, observations, notes,
    emergency_contact_name, emergency_contact_phone,
    created_at, created_by, updated_at, updated_by, is_active
) VALUES
    (1, 'Maria', 'Gomez', 'maria.staff@sigep.edu.mx', '+54-11-6111-1001', 'TS-2001', '1988-05-10', 'Lavalle 500', '2024-01-15', 980000.00, 'UP_TO_DATE', 22, 'General English', 'CELTA, TKT', 'Excelente feedback de alumnos', 'Disponible para clases extra', 'Laura Mendez', '+54-11-7000-1001', NOW() - INTERVAL '400 days', 'system', NOW() - INTERVAL '2 days', 'system', true),
    (2, 'Juan', 'Paredes', 'juan.staff@sigep.edu.mx', '+54-11-6111-1002', 'TS-2002', '1985-09-22', 'Chile 850', '2023-08-10', 920000.00, 'PENDING', 18, 'Conversation', 'TEFL Advanced', 'Buen manejo de grupo', 'Requiere apoyo en admin', 'Cecilia Paredes', '+54-11-7000-1002', NOW() - INTERVAL '500 days', 'system', NOW() - INTERVAL '3 days', 'system', true),
    (3, 'Luis', 'Romero', 'luis.staff@sigep.edu.mx', '+54-11-6111-1003', 'TS-2003', '1990-02-02', 'Viamonte 1200', '2024-06-01', 870000.00, 'OVERDUE', 14, 'Exam Prep', 'IELTS Trainer', 'Alta exigencia academica', 'Coordinar reemplazos', 'Diego Romero', '+54-11-7000-1003', NOW() - INTERVAL '300 days', 'system', NOW() - INTERVAL '5 days', 'system', true);

-- ============================================================================
-- NON_TEACHING_STAFF
-- ============================================================================
INSERT INTO non_teaching_staff (
    id, first_name, last_name, email, phone_number, document_number, birth_date,
    address, hire_date, hourly_rate, role, company_name, assigned_tasks, observations,
    emergency_contact_name, emergency_contact_phone,
    created_at, created_by, updated_at, updated_by, is_active
) VALUES
    (1, 'Roberto', 'Sanchez', 'roberto.nstaff@sigep.edu.mx', '+54-11-6222-2001', 'NTS-3001', '1982-01-12', 'Mexico 300', '2024-03-01', 5500.00, 'CLEANING', 'CleanPro SA', 'Limpieza de aulas y pasillos', 'Cumple cronograma diario', 'Elena Sanchez', '+54-11-7100-2001', NOW() - INTERVAL '200 days', 'system', NOW() - INTERVAL '2 days', 'system', true),
    (2, 'Carla', 'Molina', 'carla.nstaff@sigep.edu.mx', '+54-11-6222-2002', 'NTS-3002', '1989-04-18', 'Peru 700', '2024-05-15', 7200.00, 'IT_SUPPORT', 'TechCare SRL', 'Soporte de red y equipos', 'Atencion rapida de incidencias', 'Nestor Molina', '+54-11-7100-2002', NOW() - INTERVAL '180 days', 'system', NOW() - INTERVAL '2 days', 'system', true),
    (3, 'Martin', 'Quiroga', 'martin.nstaff@sigep.edu.mx', '+54-11-6222-2003', 'NTS-3003', '1991-11-30', 'Moreno 940', '2024-07-10', 6800.00, 'IT', 'InfraOps SA', 'Automatizacion de inventario', 'Rol IT alias valido en V9', 'Silvia Quiroga', '+54-11-7100-2003', NOW() - INTERVAL '150 days', 'system', NOW() - INTERVAL '1 days', 'system', true);

-- ============================================================================
-- STAFF_ATTENDANCE
-- ============================================================================
INSERT INTO staff_attendance (
    id, attendance_date, check_in_time, check_out_time, status, notes, hours_worked,
    teaching_staff_id, non_teaching_staff_id, created_at, updated_at
) VALUES
    (1, '2026-04-01', '08:55', '15:05', 'PRESENT', 'Jornada completa docente', 6.17, 1, NULL, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (2, '2026-04-01', '09:10', '14:00', 'LATE', 'Ingreso tarde por trafico', 4.83, 2, NULL, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (3, '2026-04-01', '07:45', '16:00', 'PRESENT', 'Cobertura limpieza total', 8.25, NULL, 1, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (4, '2026-04-02', '08:00', '12:30', 'SICK_LEAVE', 'Retiro anticipado por malestar', 4.50, NULL, 2, NOW() - INTERVAL '9 days', NOW() - INTERVAL '9 days');

-- ============================================================================
-- EXAMS
-- ============================================================================
INSERT INTO exams (
    id, course_id, title, description, modality, status, total_points, weight,
    time_limit_minutes, scheduled_at, visibility_start, visibility_end,
    assigned_teachers, notes, room_info, version,
    created_at, created_by, updated_at, updated_by
) VALUES
    ('11111111-1111-1111-1111-111111111101'::UUID, 1, 'A1 Unit Test', 'Evaluacion de unidades 1 y 2', 'OFFLINE', 'PUBLISHED', 100.00, 0.30, 60, NOW() + INTERVAL '7 days', NOW() - INTERVAL '1 days', NOW() + INTERVAL '10 days', '[3,4]', 'Examen presencial con hoja impresa', 'Aula 101', 1, NOW() - INTERVAL '12 days', 3, NOW() - INTERVAL '1 days', 3),
    ('22222222-2222-2222-2222-222222222202'::UUID, 2, 'B1 Midterm', 'Midterm de gramatica y writing', 'OFFLINE', 'CLOSED', 100.00, 0.40, 90, NOW() - INTERVAL '20 days', NOW() - INTERVAL '30 days', NOW() - INTERVAL '19 days', '[4]', 'Examen ya cerrado', 'Aula 102', 2, NOW() - INTERVAL '35 days', 4, NOW() - INTERVAL '18 days', 4),
    ('33333333-3333-3333-3333-333333333303'::UUID, 3, 'C1 Draft Evaluation', 'Borrador de examen oral', 'ONLINE', 'DRAFT', 50.00, 0.20, 30, NOW() + INTERVAL '14 days', NOW() + INTERVAL '10 days', NOW() + INTERVAL '20 days', '[5]', 'Pendiente de publicacion', 'Sala C1', 1, NOW() - INTERVAL '8 days', 5, NOW() - INTERVAL '1 days', 5);

-- ============================================================================
-- EXAM_SUBMISSIONS
-- ============================================================================
INSERT INTO exam_submissions (
    id, exam_id, student_id, attempt_number, status, started_at, submitted_at,
    score, graded_by, graded_at, feedback, scanned_file_path, notes, version,
    created_at, created_by, updated_at, updated_by
) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa1001'::UUID, '22222222-2222-2222-2222-222222222202'::UUID, 3, 1, 'GRADED', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days' + INTERVAL '85 minutes', 78.50, 4, NOW() - INTERVAL '18 days', 'Buen dominio general, mejorar cohesion.', '/scans/submission-1001.pdf', 'Primera correccion', 2, NOW() - INTERVAL '20 days', 4, NOW() - INTERVAL '18 days', 4),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa1002'::UUID, '22222222-2222-2222-2222-222222222202'::UUID, 4, 1, 'UNDER_REVIEW', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days' + INTERVAL '80 minutes', 72.00, 4, NOW() - INTERVAL '18 days', 'Solicita revision de seccion 3.', '/scans/submission-1002.pdf', 'Apelacion abierta', 2, NOW() - INTERVAL '20 days', 4, NOW() - INTERVAL '17 days', 4),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa1003'::UUID, '11111111-1111-1111-1111-111111111101'::UUID, 1, 1, 'PENDING', NOW() - INTERVAL '1 days', NOW() - INTERVAL '1 days' + INTERVAL '58 minutes', 0.00, NULL, NULL, 'Pendiente de calificacion', '/scans/submission-1003.pdf', 'Subida automatica', 1, NOW() - INTERVAL '1 days', 3, NOW() - INTERVAL '1 days', 3),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa1004'::UUID, '11111111-1111-1111-1111-111111111101'::UUID, 2, 1, 'CANCELLED', NOW() - INTERVAL '1 days', NOW() - INTERVAL '1 days' + INTERVAL '10 minutes', 15.00, 3, NOW() - INTERVAL '1 days', 'Intento cancelado por incidencia tecnica.', '/scans/submission-1004.pdf', 'Reagendar examen', 1, NOW() - INTERVAL '1 days', 3, NOW() - INTERVAL '1 days', 3);

-- ============================================================================
-- EXAM_GRADE_HISTORY
-- ============================================================================
INSERT INTO exam_grade_history (
    id, submission_id, changed_at, changed_by, previous_score, new_score,
    reason, created_at, created_by, updated_at, updated_by
) VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb2001'::UUID, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa1001'::UUID, NOW() - INTERVAL '17 days', 4, 74.00, 78.50, 'Se agregaron puntos por respuesta parcialmente correcta', NOW() - INTERVAL '17 days', 4, NOW() - INTERVAL '17 days', 4),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbb2002'::UUID, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa1002'::UUID, NOW() - INTERVAL '16 days', 4, 69.00, 72.00, 'Reevaluacion tras consulta del estudiante', NOW() - INTERVAL '16 days', 4, NOW() - INTERVAL '16 days', 4);

-- ============================================================================
-- PAYMENTS
-- ============================================================================
INSERT INTO payments (
    id, student_id, amount, concept, payment_date, due_date, status,
    payment_method, receipt_number, notes, created_at, updated_at
) VALUES
    (1, 1, 45000.00, 'Cuota Marzo A1', '2026-03-05', '2026-03-10', 'PAID', 'BANK_TRANSFER', 'RCPT-2026-0001', 'Pago en termino', NOW() - INTERVAL '40 days', NOW() - INTERVAL '40 days'),
    (2, 2, 45000.00, 'Cuota Marzo A1', '2026-03-15', '2026-03-10', 'OVERDUE', 'CREDIT_CARD', 'RCPT-2026-0002', 'Pago fuera de termino', NOW() - INTERVAL '35 days', NOW() - INTERVAL '35 days'),
    (3, 3, 52000.00, 'Cuota Marzo B1', '2026-03-08', '2026-03-12', 'PAID', 'DEBIT_CARD', 'RCPT-2026-0003', 'Pago normal', NOW() - INTERVAL '33 days', NOW() - INTERVAL '33 days'),
    (4, 4, 52000.00, 'Cuota Marzo B1', '2026-03-11', '2026-03-12', 'PENDING', 'CASH', 'RCPT-2026-0004', 'Pendiente acreditacion final', NOW() - INTERVAL '32 days', NOW() - INTERVAL '32 days');

-- ============================================================================
-- NOTIFICATIONS
-- ============================================================================
INSERT INTO notifications (
    id, title, message, type, recipient_id, recipient_type, status,
    send_date, read_date, created_at, updated_at
) VALUES
    (1, 'Bienvenida', 'Bienvenido al ciclo lectivo 2026 en SiGEP.', 'INFO', 1, 'STUDENT', 'READ', NOW() - INTERVAL '60 days', NOW() - INTERVAL '59 days', NOW() - INTERVAL '60 days', NOW() - INTERVAL '59 days'),
    (2, 'Recordatorio de Pago', 'Tu cuota vence en 48 horas.', 'REMINDER', 2, 'STUDENT', 'DELIVERED', NOW() - INTERVAL '34 days', NOW() - INTERVAL '33 days', NOW() - INTERVAL '34 days', NOW() - INTERVAL '33 days'),
    (3, 'Actualizacion Docente', 'Se actualizo la planificacion de B1.', 'SUCCESS', 4, 'TEACHER', 'SENT', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days'),
    (4, 'Alerta Operativa', 'Se detecto incidencia menor en aula 102.', 'WARNING', 1, 'ADMIN', 'FAILED', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 days');

-- ============================================================================
-- Ajuste de secuencias BIGINT para evitar colisiones futuras
-- ============================================================================
DO $$
DECLARE
    t TEXT;
    seq_name TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'users','students','courses','course_schedules','enrollments','course_sessions',
        'session_exceptions','course_attendance','course_materials','course_certificates',
        'teaching_staff','non_teaching_staff','staff_attendance','payments','notifications'
    ]
    LOOP
        SELECT pg_get_serial_sequence(t, 'id') INTO seq_name;
        IF seq_name IS NOT NULL THEN
            EXECUTE format(
                'SELECT setval(%L, (SELECT COALESCE(MAX(id), 1) FROM %I), true);',
                seq_name, t
            );
        END IF;
    END LOOP;
END $$;

COMMIT;

-- ============================================================================
-- Verificacion rapida
-- ============================================================================
SELECT 'users' AS table_name, COUNT(*) AS rows FROM users
UNION ALL SELECT 'students', COUNT(*) FROM students
UNION ALL SELECT 'courses', COUNT(*) FROM courses
UNION ALL SELECT 'course_schedules', COUNT(*) FROM course_schedules
UNION ALL SELECT 'enrollments', COUNT(*) FROM enrollments
UNION ALL SELECT 'course_sessions', COUNT(*) FROM course_sessions
UNION ALL SELECT 'session_exceptions', COUNT(*) FROM session_exceptions
UNION ALL SELECT 'course_attendance', COUNT(*) FROM course_attendance
UNION ALL SELECT 'course_materials', COUNT(*) FROM course_materials
UNION ALL SELECT 'course_certificates', COUNT(*) FROM course_certificates
UNION ALL SELECT 'teaching_staff', COUNT(*) FROM teaching_staff
UNION ALL SELECT 'non_teaching_staff', COUNT(*) FROM non_teaching_staff
UNION ALL SELECT 'staff_attendance', COUNT(*) FROM staff_attendance
UNION ALL SELECT 'exams', COUNT(*) FROM exams
UNION ALL SELECT 'exam_submissions', COUNT(*) FROM exam_submissions
UNION ALL SELECT 'exam_grade_history', COUNT(*) FROM exam_grade_history
UNION ALL SELECT 'payments', COUNT(*) FROM payments
UNION ALL SELECT 'notifications', COUNT(*) FROM notifications
ORDER BY table_name;
