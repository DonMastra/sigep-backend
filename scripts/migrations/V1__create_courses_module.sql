-- Migration Script for Courses Module
-- Version: 1.0
-- Date: 2025-01-24
-- Description: Creates all tables for the courses module including courses, enrollments, attendance, materials, and certificates

-- =====================================================
-- COURSES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS courses (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    level VARCHAR(50) NOT NULL,
    duration INTEGER NOT NULL,
    max_students INTEGER NOT NULL,
    min_students INTEGER NOT NULL DEFAULT 1,
    teacher_id BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_published BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_students CHECK (min_students <= max_students),
    CONSTRAINT chk_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_courses_teacher ON courses(teacher_id);
CREATE INDEX idx_courses_level ON courses(level);
CREATE INDEX idx_courses_status ON courses(status);
CREATE INDEX idx_courses_published ON courses(is_published);

-- =====================================================
-- COURSE SCHEDULES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS course_schedules (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    day_of_week VARCHAR(20) NOT NULL,
    start_time VARCHAR(5) NOT NULL,
    end_time VARCHAR(5) NOT NULL,
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);

CREATE INDEX idx_schedules_course ON course_schedules(course_id);

-- =====================================================
-- ENROLLMENTS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS enrollments (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    enrollment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    final_grade DECIMAL(5,2),
    completion_date DATE,
    notes VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT chk_grade CHECK (final_grade IS NULL OR (final_grade >= 0 AND final_grade <= 100))
);

CREATE INDEX idx_enrollments_student ON enrollments(student_id);
CREATE INDEX idx_enrollments_course ON enrollments(course_id);
CREATE INDEX idx_enrollments_status ON enrollments(status);
CREATE UNIQUE INDEX idx_enrollments_student_course ON enrollments(student_id, course_id);

-- =====================================================
-- ATTENDANCE TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS course_attendance (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    notes VARCHAR(500),
    recorded_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE
);

CREATE INDEX idx_attendance_enrollment ON course_attendance(enrollment_id);
CREATE INDEX idx_attendance_date ON course_attendance(attendance_date);
CREATE UNIQUE INDEX idx_attendance_enrollment_date ON course_attendance(enrollment_id, attendance_date);

-- =====================================================
-- COURSE MATERIALS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS course_materials (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    type VARCHAR(20) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    file_name VARCHAR(255),
    file_size BIGINT,
    mime_type VARCHAR(100),
    uploaded_by BIGINT NOT NULL,
    is_visible BOOLEAN NOT NULL DEFAULT true,
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);

CREATE INDEX idx_materials_course ON course_materials(course_id);
CREATE INDEX idx_materials_type ON course_materials(type);
CREATE INDEX idx_materials_visible ON course_materials(is_visible);
CREATE INDEX idx_materials_order ON course_materials(course_id, order_index);

-- =====================================================
-- CERTIFICATES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS course_certificates (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGINT NOT NULL UNIQUE,
    certificate_code VARCHAR(50) NOT NULL UNIQUE,
    issue_date DATE NOT NULL,
    expiry_date DATE,
    final_grade DECIMAL(5,2) NOT NULL,
    honors VARCHAR(500),
    notes VARCHAR(1000),
    pdf_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issued_by BIGINT NOT NULL,
    revoked_by BIGINT,
    revoked_at TIMESTAMP,
    revocation_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE,
    CONSTRAINT chk_cert_grade CHECK (final_grade >= 0 AND final_grade <= 100)
);

CREATE INDEX idx_certificate_enrollment ON course_certificates(enrollment_id);
CREATE INDEX idx_certificate_code ON course_certificates(certificate_code);
CREATE INDEX idx_certificate_status ON course_certificates(status);
CREATE INDEX idx_certificate_issue_date ON course_certificates(issue_date);

-- =====================================================
-- COMMENTS
-- =====================================================
COMMENT ON TABLE courses IS 'Stores course information including schedules, pricing, and capacity';
COMMENT ON TABLE course_schedules IS 'Defines weekly schedules for courses';
COMMENT ON TABLE enrollments IS 'Tracks student enrollments in courses';
COMMENT ON TABLE course_attendance IS 'Records student attendance for each class session';
COMMENT ON TABLE course_materials IS 'Stores course materials and resources';
COMMENT ON TABLE course_certificates IS 'Manages course completion certificates';

