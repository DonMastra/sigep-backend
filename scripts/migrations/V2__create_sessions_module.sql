-- Migration Script for Course Sessions Module
-- Version: 2.0
-- Date: 2025-01-24
-- Description: Creates tables for course sessions, scheduling, and classroom management

-- =====================================================
-- COURSE SESSIONS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS course_sessions (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL,
    session_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    classroom_id BIGINT,
    classroom_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    topic VARCHAR(1000),
    notes VARCHAR(1000),
    is_recurring BOOLEAN NOT NULL DEFAULT false,
    recurrence_rule VARCHAR(500),
    parent_session_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    CONSTRAINT chk_session_time CHECK (end_time > start_time)
);

CREATE INDEX idx_session_course ON course_sessions(course_id);
CREATE INDEX idx_session_date ON course_sessions(session_date);
CREATE INDEX idx_session_classroom ON course_sessions(classroom_id);
CREATE INDEX idx_session_status ON course_sessions(status);

-- =====================================================
-- SESSION EXCEPTIONS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS session_exceptions (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    exception_date DATE NOT NULL,
    exception_type VARCHAR(20) NOT NULL,
    new_start_time TIME,
    new_end_time TIME,
    new_classroom_id BIGINT,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (session_id) REFERENCES course_sessions(id) ON DELETE CASCADE,
    CONSTRAINT uq_session_exception UNIQUE (session_id, exception_date)
);

CREATE INDEX idx_exception_session ON session_exceptions(session_id);
CREATE INDEX idx_exception_date ON session_exceptions(exception_date);

-- =====================================================
-- COMMENTS
-- =====================================================
COMMENT ON TABLE course_sessions IS 'Stores course session schedules with classroom assignments';
COMMENT ON TABLE session_exceptions IS 'Stores exceptions to recurring sessions (cancellations, reschedules, etc.)';
COMMENT ON COLUMN course_sessions.recurrence_rule IS 'RRULE format for recurring sessions';
COMMENT ON COLUMN course_sessions.parent_session_id IS 'Reference to original session if this is part of a recurring series';

