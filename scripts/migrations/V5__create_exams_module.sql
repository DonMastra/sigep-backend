-- =============================================================================
-- V5__create_exams_module.sql
-- Documents the current exams, submissions and grade history schema.
-- =============================================================================

CREATE TABLE IF NOT EXISTS exams (
    id                 UUID PRIMARY KEY,
    course_id          BIGINT NOT NULL,
    title              VARCHAR(200) NOT NULL,
    description        TEXT,
    modality           VARCHAR(16) NOT NULL,
    status             VARCHAR(16) NOT NULL,
    scheduled_at       TIMESTAMP,
    time_limit_minutes INT,
    total_points       NUMERIC(10, 2) NOT NULL,
    weight             NUMERIC(5, 2) NOT NULL,
    room_info          VARCHAR(100),
    assigned_teachers  TEXT,
    visibility_start   TIMESTAMP,
    visibility_end     TIMESTAMP,
    notes              TEXT,
    version            INT NOT NULL,
    created_at         TIMESTAMP NOT NULL,
    created_by         BIGINT NOT NULL,
    updated_at         TIMESTAMP,
    updated_by         BIGINT,
    CONSTRAINT exams_modality_check CHECK (modality IN ('OFFLINE', 'ONLINE')),
    CONSTRAINT exams_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_exam_course ON exams(course_id);
CREATE INDEX IF NOT EXISTS idx_exam_status ON exams(status);
CREATE INDEX IF NOT EXISTS idx_exam_scheduled_at ON exams(scheduled_at);

CREATE TABLE IF NOT EXISTS exam_submissions (
    id                UUID PRIMARY KEY,
    exam_id           UUID NOT NULL,
    student_id        BIGINT NOT NULL,
    attempt_number    INT NOT NULL,
    status            VARCHAR(16) NOT NULL,
    started_at        TIMESTAMP,
    submitted_at      TIMESTAMP,
    graded_at         TIMESTAMP,
    graded_by         BIGINT,
    score             NUMERIC(10, 2),
    feedback          TEXT,
    scanned_file_path VARCHAR(500),
    notes             TEXT,
    version           INT NOT NULL,
    created_at        TIMESTAMP NOT NULL,
    created_by        BIGINT NOT NULL,
    updated_at        TIMESTAMP,
    updated_by        BIGINT,
    CONSTRAINT exam_submissions_status_check CHECK (
        status IN ('PENDING', 'GRADED', 'CANCELLED', 'UNDER_REVIEW')
    ),
    CONSTRAINT uq_exam_student_attempt UNIQUE (exam_id, student_id, attempt_number)
);

CREATE INDEX IF NOT EXISTS idx_submission_exam ON exam_submissions(exam_id);
CREATE INDEX IF NOT EXISTS idx_submission_student ON exam_submissions(student_id);
CREATE INDEX IF NOT EXISTS idx_submission_status ON exam_submissions(status);

CREATE TABLE IF NOT EXISTS exam_grade_history (
    id             UUID PRIMARY KEY,
    submission_id  UUID NOT NULL,
    previous_score NUMERIC(10, 2),
    new_score      NUMERIC(10, 2) NOT NULL,
    reason         TEXT,
    changed_at     TIMESTAMP NOT NULL,
    changed_by     BIGINT NOT NULL,
    created_at     TIMESTAMP NOT NULL,
    created_by     BIGINT NOT NULL,
    updated_at     TIMESTAMP,
    updated_by     BIGINT
);

CREATE INDEX IF NOT EXISTS idx_grade_history_submission ON exam_grade_history(submission_id);
CREATE INDEX IF NOT EXISTS idx_grade_history_changed_at ON exam_grade_history(changed_at);
