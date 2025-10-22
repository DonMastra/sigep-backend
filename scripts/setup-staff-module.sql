-- ============================================
-- Script de creación de tablas para el módulo STAFF
-- Sistema de Gestión de Personal Docente y No Docente
-- ============================================

-- Tabla: Personal Docente (Teaching Staff)
CREATE TABLE IF NOT EXISTS teaching_staff (
    id BIGSERIAL PRIMARY KEY,

    -- Datos personales
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(20) NOT NULL,
    document_number VARCHAR(50) NOT NULL UNIQUE,
    birth_date DATE NOT NULL,
    address TEXT NOT NULL,

    -- Información laboral
    hire_date DATE NOT NULL,
    monthly_salary DECIMAL(10, 2) NOT NULL CHECK (monthly_salary > 0),
    payment_status VARCHAR(50) NOT NULL DEFAULT 'UP_TO_DATE',
    assigned_students_count INTEGER NOT NULL DEFAULT 0,

    -- Información académica
    specialization TEXT,
    observations TEXT,
    notes TEXT,

    -- Contacto de emergencia
    emergency_contact_name VARCHAR(100) NOT NULL,
    emergency_contact_phone VARCHAR(20) NOT NULL,

    -- Auditoría
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Constraints
    CONSTRAINT chk_birth_before_hire CHECK (birth_date < hire_date)
);

-- Índices para teaching_staff
CREATE INDEX idx_teaching_staff_email ON teaching_staff(email);
CREATE INDEX idx_teaching_staff_document ON teaching_staff(document_number);
CREATE INDEX idx_teaching_staff_active ON teaching_staff(is_active);
CREATE INDEX idx_teaching_staff_name ON teaching_staff(last_name, first_name);

-- Tabla: Personal No Docente (Non-Teaching Staff)
CREATE TABLE IF NOT EXISTS non_teaching_staff (
    id BIGSERIAL PRIMARY KEY,

    -- Datos personales
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone_number VARCHAR(20) NOT NULL,
    document_number VARCHAR(50) NOT NULL UNIQUE,
    birth_date DATE NOT NULL,
    address TEXT NOT NULL,

    -- Información laboral
    hire_date DATE NOT NULL,
    hourly_rate DECIMAL(10, 2) NOT NULL CHECK (hourly_rate > 0),
    role VARCHAR(50) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    assigned_tasks TEXT,
    observations TEXT,

    -- Contacto de emergencia
    emergency_contact_name VARCHAR(100) NOT NULL,
    emergency_contact_phone VARCHAR(20) NOT NULL,

    -- Auditoría
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    -- Constraints
    CONSTRAINT chk_non_teaching_birth_before_hire CHECK (birth_date < hire_date)
);

-- Índices para non_teaching_staff
CREATE INDEX idx_non_teaching_staff_email ON non_teaching_staff(email);
CREATE INDEX idx_non_teaching_staff_document ON non_teaching_staff(document_number);
CREATE INDEX idx_non_teaching_staff_role ON non_teaching_staff(role);
CREATE INDEX idx_non_teaching_staff_company ON non_teaching_staff(company_name);
CREATE INDEX idx_non_teaching_staff_active ON non_teaching_staff(is_active);
CREATE INDEX idx_non_teaching_staff_name ON non_teaching_staff(last_name, first_name);

-- Tabla: Asistencia del Personal (Staff Attendance)
CREATE TABLE IF NOT EXISTS staff_attendance (
    id BIGSERIAL PRIMARY KEY,

    -- Relaciones (solo una debe estar presente)
    teaching_staff_id BIGINT REFERENCES teaching_staff(id) ON DELETE CASCADE,
    non_teaching_staff_id BIGINT REFERENCES non_teaching_staff(id) ON DELETE CASCADE,

    -- Información de asistencia
    attendance_date DATE NOT NULL,
    check_in_time TIME,
    check_out_time TIME,
    status VARCHAR(50) NOT NULL,
    notes TEXT,
    hours_worked DECIMAL(5, 2),

    -- Constraints
    CONSTRAINT chk_only_one_staff_type CHECK (
        (teaching_staff_id IS NOT NULL AND non_teaching_staff_id IS NULL) OR
        (teaching_staff_id IS NULL AND non_teaching_staff_id IS NOT NULL)
    ),
    CONSTRAINT chk_check_out_after_check_in CHECK (
        check_out_time IS NULL OR check_in_time IS NULL OR check_out_time > check_in_time
    ),
    CONSTRAINT chk_hours_positive CHECK (hours_worked IS NULL OR hours_worked >= 0)
);

-- Índices para staff_attendance
CREATE INDEX idx_attendance_date ON staff_attendance(attendance_date);
CREATE INDEX idx_attendance_teaching_staff ON staff_attendance(teaching_staff_id);
CREATE INDEX idx_attendance_non_teaching_staff ON staff_attendance(non_teaching_staff_id);
CREATE INDEX idx_attendance_status ON staff_attendance(status);
CREATE INDEX idx_attendance_teaching_date ON staff_attendance(teaching_staff_id, attendance_date);
CREATE INDEX idx_attendance_non_teaching_date ON staff_attendance(non_teaching_staff_id, attendance_date);

-- Comentarios en las tablas
COMMENT ON TABLE teaching_staff IS 'Personal docente de la institución';
COMMENT ON TABLE non_teaching_staff IS 'Personal no docente (limpieza, mantenimiento, sistemas, etc.)';
COMMENT ON TABLE staff_attendance IS 'Registro de asistencia del personal';

-- Comentarios en columnas importantes
COMMENT ON COLUMN teaching_staff.payment_status IS 'Estado de pago: UP_TO_DATE, PENDING, OVERDUE, PARTIALLY_PAID';
COMMENT ON COLUMN teaching_staff.assigned_students_count IS 'Contador de estudiantes asignados al docente';
COMMENT ON COLUMN non_teaching_staff.role IS 'Rol: CLEANING, MAINTENANCE, IT_SUPPORT, SECURITY, ADMINISTRATION, OTHER';
COMMENT ON COLUMN staff_attendance.status IS 'Estado: PRESENT, ABSENT, LATE, EXCUSED, SICK_LEAVE, VACATION';
COMMENT ON COLUMN staff_attendance.hours_worked IS 'Horas trabajadas (principalmente para personal no docente por hora)';

-- ============================================
-- Datos de prueba (solo para desarrollo)
-- ============================================

-- Personal Docente de ejemplo
INSERT INTO teaching_staff (
    first_name, last_name, email, phone_number, document_number,
    birth_date, address, hire_date, monthly_salary, specialization,
    emergency_contact_name, emergency_contact_phone
) VALUES
(
    'María', 'González', 'maria.gonzalez@sigep.edu.mx', '+52 55 1234 5678', 'GOMX850315ABC',
    '1985-03-15', 'Calle Principal 123, CDMX', '2020-01-15', 15000.00,
    'Inglés avanzado - Certificación TOEFL',
    'Juan González', '+52 55 9876 5432'
),
(
    'Roberto', 'Martínez', 'roberto.martinez@sigep.edu.mx', '+52 55 2345 6789', 'MARR780520DEF',
    '1978-05-20', 'Av. Reforma 456, CDMX', '2019-08-01', 18000.00,
    'Business English - Certificación Cambridge',
    'Laura Martínez', '+52 55 8765 4321'
),
(
    'Ana', 'López', 'ana.lopez@sigep.edu.mx', '+52 55 3456 7890', 'LOPA901112GHI',
    '1990-11-12', 'Col. Roma 789, CDMX', '2021-03-10', 14000.00,
    'Inglés para niños - Experiencia en primaria',
    'Carlos López', '+52 55 7654 3210'
)
ON CONFLICT (email) DO NOTHING;

-- Personal No Docente de ejemplo
INSERT INTO non_teaching_staff (
    first_name, last_name, email, phone_number, document_number,
    birth_date, address, hire_date, hourly_rate, role, company_name,
    assigned_tasks, emergency_contact_name, emergency_contact_phone
) VALUES
(
    'Carlos', 'Ramírez', 'carlos.ramirez@cleaning.com', '+52 55 2222 3333', 'RAMC920712XYZ',
    '1992-07-12', 'Av. Reforma 456, CDMX', '2021-06-01', 80.00, 'CLEANING',
    'Servicios de Limpieza SA', 'Limpieza de aulas, baños y oficinas administrativas',
    'Ana Ramírez', '+52 55 4444 5555'
),
(
    'Pedro', 'Hernández', 'pedro.hernandez@maintenance.com', '+52 55 3333 4444', 'HERP881025JKL',
    '1988-10-25', 'Col. Condesa 321, CDMX', '2020-09-15', 120.00, 'MAINTENANCE',
    'Mantenimiento Profesional SC', 'Mantenimiento eléctrico, plomería y reparaciones generales',
    'María Hernández', '+52 55 5555 6666'
),
(
    'Luis', 'García', 'luis.garcia@techsupport.com', '+52 55 4444 5555', 'GARL950203MNO',
    '1995-02-03', 'Polanco 654, CDMX', '2022-01-10', 150.00, 'IT_SUPPORT',
    'IT Solutions México', 'Soporte técnico, redes y sistemas informáticos',
    'Carmen García', '+52 55 6666 7777'
)
ON CONFLICT (email) DO NOTHING;

-- Registros de asistencia de ejemplo (último mes)
INSERT INTO staff_attendance (teaching_staff_id, attendance_date, check_in_time, check_out_time, status, hours_worked)
SELECT
    ts.id,
    CURRENT_DATE - (random() * 30)::integer,
    '08:00:00'::time,
    '16:00:00'::time,
    CASE
        WHEN random() < 0.9 THEN 'PRESENT'
        WHEN random() < 0.95 THEN 'LATE'
        ELSE 'ABSENT'
    END,
    8.0
FROM teaching_staff ts
WHERE ts.is_active = true
LIMIT 30;

INSERT INTO staff_attendance (non_teaching_staff_id, attendance_date, check_in_time, check_out_time, status, hours_worked)
SELECT
    nts.id,
    CURRENT_DATE - (random() * 30)::integer,
    '06:00:00'::time,
    '14:00:00'::time,
    CASE
        WHEN random() < 0.95 THEN 'PRESENT'
        ELSE 'ABSENT'
    END,
    8.0
FROM non_teaching_staff nts
WHERE nts.is_active = true
LIMIT 30;

-- Verificación de datos insertados
SELECT 'Teaching Staff Count:' as info, COUNT(*) as total FROM teaching_staff WHERE is_active = true
UNION ALL
SELECT 'Non-Teaching Staff Count:' as info, COUNT(*) as total FROM non_teaching_staff WHERE is_active = true
UNION ALL
SELECT 'Attendance Records Count:' as info, COUNT(*) as total FROM staff_attendance;

