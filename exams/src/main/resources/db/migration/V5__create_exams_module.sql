-- Migration for Exams Module - Phase 1: Offline exam management
-- Version: V5__create_exams_module.sql

-- ==============================================================================
-- TABLE: exams
-- Stores exam information for courses
-- ==============================================================================
CREATE TABLE IF NOT EXISTS exams (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    modality VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    total_points NUMERIC(10, 2) NOT NULL DEFAULT 100.00,
    weight NUMERIC(5, 2) NOT NULL DEFAULT 1.00,
    time_limit_minutes INTEGER,
    scheduled_at TIMESTAMP WITH TIME ZONE,
    visibility_start TIMESTAMP WITH TIME ZONE,
    visibility_end TIMESTAMP WITH TIME ZONE,
    assigned_teachers TEXT,
    notes TEXT,
    room_info VARCHAR(100),
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by UUID,

    CONSTRAINT chk_exam_total_points CHECK (total_points > 0),
    CONSTRAINT chk_exam_weight CHECK (weight > 0),
    CONSTRAINT chk_exam_modality CHECK (modality IN ('OFFLINE', 'ONLINE')),
    CONSTRAINT chk_exam_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'CANCELLED'))
);

-- Indexes for exams
CREATE INDEX idx_exam_course ON exams(course_id);
CREATE INDEX idx_exam_status ON exams(status);
CREATE INDEX idx_exam_scheduled_at ON exams(scheduled_at);
CREATE INDEX idx_exam_created_at ON exams(created_at);

-- ==============================================================================
-- TABLE: exam_submissions
-- Stores student exam attempts and grades
-- ==============================================================================
CREATE TABLE IF NOT EXISTS exam_submissions (
    id UUID PRIMARY KEY,
    exam_id UUID NOT NULL,
    student_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE,
    submitted_at TIMESTAMP WITH TIME ZONE,
    score NUMERIC(10, 2),
    graded_by UUID,
    graded_at TIMESTAMP WITH TIME ZONE,
    feedback TEXT,
    scanned_file_path VARCHAR(500),
    notes TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by UUID,

    CONSTRAINT fk_submission_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
    CONSTRAINT chk_submission_status CHECK (status IN ('PENDING', 'GRADED', 'CANCELLED', 'UNDER_REVIEW')),
    CONSTRAINT chk_submission_score CHECK (score IS NULL OR score >= 0),
    CONSTRAINT chk_submission_attempt CHECK (attempt_number > 0)
);

-- Indexes for exam_submissions
CREATE INDEX idx_submission_exam ON exam_submissions(exam_id);
CREATE INDEX idx_submission_student ON exam_submissions(student_id);
CREATE INDEX idx_submission_status ON exam_submissions(status);
CREATE INDEX idx_submission_created_at ON exam_submissions(created_at);

-- Unique constraint for exam-student-attempt combination
CREATE UNIQUE INDEX uq_exam_student_attempt ON exam_submissions(exam_id, student_id, attempt_number);

-- ==============================================================================
-- TABLE: exam_grade_history
-- Audit trail for grade changes
-- ==============================================================================
CREATE TABLE IF NOT EXISTS exam_grade_history (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    changed_by UUID NOT NULL,
    previous_score NUMERIC(10, 2),
    new_score NUMERIC(10, 2) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    updated_by UUID,

    CONSTRAINT fk_grade_history_submission FOREIGN KEY (submission_id) REFERENCES exam_submissions(id) ON DELETE CASCADE,
    CONSTRAINT chk_history_scores CHECK (previous_score IS NULL OR previous_score >= 0),
    CONSTRAINT chk_history_new_score CHECK (new_score >= 0)
);

-- Indexes for exam_grade_history
CREATE INDEX idx_grade_history_submission ON exam_grade_history(submission_id);
CREATE INDEX idx_grade_history_changed_at ON exam_grade_history(changed_at);

-- ==============================================================================
-- COMMENTS
-- ==============================================================================
COMMENT ON TABLE exams IS 'Exámenes de cursos - Fase 1: presencial con carga de notas';
COMMENT ON TABLE exam_submissions IS 'Intentos y calificaciones de exámenes por estudiante';
COMMENT ON TABLE exam_grade_history IS 'Historial de auditoría de cambios en calificaciones';

COMMENT ON COLUMN exams.modality IS 'OFFLINE=presencial, ONLINE=en plataforma (Fase 2)';
COMMENT ON COLUMN exams.status IS 'DRAFT=borrador, PUBLISHED=publicado, CLOSED=cerrado, CANCELLED=cancelado';
COMMENT ON COLUMN exams.assigned_teachers IS 'JSON array de UUIDs de docentes asignados';
COMMENT ON COLUMN exams.visibility_start IS 'Fecha desde la cual el examen es visible para estudiantes';
COMMENT ON COLUMN exams.visibility_end IS 'Fecha hasta la cual el examen es visible para estudiantes';

COMMENT ON COLUMN exam_submissions.status IS 'PENDING=pendiente, GRADED=calificado, CANCELLED=cancelado, UNDER_REVIEW=en revisión';
COMMENT ON COLUMN exam_submissions.scanned_file_path IS 'Ruta del archivo escaneado del examen físico';
COMMENT ON COLUMN exam_submissions.attempt_number IS 'Número de intento del estudiante en este examen';

-- ==============================================================================
-- INITIAL DATA (Optional)
-- ==============================================================================
-- Uncomment if you want to insert sample data for testing
/*
INSERT INTO exams (id, course_id, title, description, total_points, weight, status, created_by)
VALUES
    (gen_random_uuid(), 'course-uuid-here', 'Examen Parcial 1', 'Primer examen parcial del curso', 100.00, 0.30, 'DRAFT', 'admin-uuid-here'),
    (gen_random_uuid(), 'course-uuid-here', 'Examen Final', 'Examen final integrador', 100.00, 0.40, 'DRAFT', 'admin-uuid-here');
*/

