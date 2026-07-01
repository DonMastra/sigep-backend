-- =============================================================================
-- V13__create_tuition_module.sql
-- Creates the tuition bounded context for matriculation workflow.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tuition_academic_years (
    id                     BIGSERIAL PRIMARY KEY,
    name                   VARCHAR(100) NOT NULL UNIQUE,
    start_date             DATE NOT NULL,
    first_term_start_date  DATE NOT NULL,
    first_term_end_date    DATE NOT NULL,
    second_term_start_date DATE NOT NULL,
    second_term_end_date   DATE NOT NULL,
    end_date               DATE NOT NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_academic_year_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED')),
    CONSTRAINT chk_tuition_academic_year_dates CHECK (
        start_date <= first_term_start_date
        AND first_term_start_date <= first_term_end_date
        AND first_term_end_date <= second_term_start_date
        AND second_term_start_date <= second_term_end_date
        AND second_term_end_date <= end_date
    )
);

CREATE INDEX IF NOT EXISTS idx_tuition_academic_year_status
    ON tuition_academic_years(status);

CREATE TABLE IF NOT EXISTS tuition_levels (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(150) NOT NULL,
    segment     VARCHAR(20) NOT NULL,
    level_order INT NOT NULL CHECK (level_order >= 1),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_level_segment CHECK (segment IN ('CHILDREN', 'TEENS', 'ADULTS'))
);

CREATE INDEX IF NOT EXISTS idx_tuition_level_segment ON tuition_levels(segment);
CREATE INDEX IF NOT EXISTS idx_tuition_level_active ON tuition_levels(active);

CREATE TABLE IF NOT EXISTS tuition_level_progression (
    id            BIGSERIAL PRIMARY KEY,
    from_level_id BIGINT NOT NULL REFERENCES tuition_levels(id) ON DELETE RESTRICT,
    to_level_id   BIGINT NOT NULL REFERENCES tuition_levels(id) ON DELETE RESTRICT,
    rule          VARCHAR(30) NOT NULL DEFAULT 'PASS_PREVIOUS_LEVEL',
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_progression_rule CHECK (rule IN ('PASS_PREVIOUS_LEVEL')),
    CONSTRAINT chk_tuition_progression_distinct CHECK (from_level_id <> to_level_id)
);

CREATE INDEX IF NOT EXISTS idx_tuition_progression_from ON tuition_level_progression(from_level_id);
CREATE INDEX IF NOT EXISTS idx_tuition_progression_to ON tuition_level_progression(to_level_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tuition_progression_active_from
    ON tuition_level_progression(from_level_id)
    WHERE active = TRUE;

CREATE TABLE IF NOT EXISTS tuition_fee_plans (
    id              BIGSERIAL PRIMARY KEY,
    academic_year_id BIGINT NOT NULL REFERENCES tuition_academic_years(id) ON DELETE RESTRICT,
    name            VARCHAR(120) NOT NULL,
    segment         VARCHAR(20),
    level_id        BIGINT REFERENCES tuition_levels(id) ON DELETE RESTRICT,
    enrollment_fee  NUMERIC(12, 2) NOT NULL CHECK (enrollment_fee >= 0),
    monthly_fee     NUMERIC(12, 2) NOT NULL CHECK (monthly_fee >= 0),
    installments    INT NOT NULL CHECK (installments BETWEEN 1 AND 24),
    currency        VARCHAR(3) NOT NULL DEFAULT 'ARS',
    valid_from      DATE NOT NULL,
    valid_to        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_fee_plan_segment CHECK (segment IS NULL OR segment IN ('CHILDREN', 'TEENS', 'ADULTS')),
    CONSTRAINT chk_tuition_fee_plan_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_tuition_fee_plan_validity CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX IF NOT EXISTS idx_tuition_fee_plan_year ON tuition_fee_plans(academic_year_id);
CREATE INDEX IF NOT EXISTS idx_tuition_fee_plan_status ON tuition_fee_plans(status);
CREATE INDEX IF NOT EXISTS idx_tuition_fee_plan_segment ON tuition_fee_plans(segment);

CREATE TABLE IF NOT EXISTS tuition_discounts (
    id          BIGSERIAL PRIMARY KEY,
    student_id  BIGINT REFERENCES students(id) ON DELETE RESTRICT,
    segment     VARCHAR(20),
    level_id    BIGINT REFERENCES tuition_levels(id) ON DELETE RESTRICT,
    type        VARCHAR(20) NOT NULL,
    percentage  NUMERIC(5, 2),
    amount      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    valid_from  DATE NOT NULL,
    valid_to    DATE,
    reason      VARCHAR(500) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_discount_segment CHECK (segment IS NULL OR segment IN ('CHILDREN', 'TEENS', 'ADULTS')),
    CONSTRAINT chk_tuition_discount_type CHECK (type IN ('SCHOLARSHIP', 'DISCOUNT')),
    CONSTRAINT chk_tuition_discount_percentage CHECK (percentage IS NULL OR (percentage > 0 AND percentage <= 100)),
    CONSTRAINT chk_tuition_discount_amount CHECK (amount >= 0),
    CONSTRAINT chk_tuition_discount_value CHECK (percentage IS NOT NULL OR amount > 0),
    CONSTRAINT chk_tuition_discount_validity CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX IF NOT EXISTS idx_tuition_discount_student ON tuition_discounts(student_id);
CREATE INDEX IF NOT EXISTS idx_tuition_discount_segment ON tuition_discounts(segment);
CREATE INDEX IF NOT EXISTS idx_tuition_discount_active ON tuition_discounts(active);

CREATE TABLE IF NOT EXISTS tuition_applications (
    id                         BIGSERIAL PRIMARY KEY,
    guardian_user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    student_id                 BIGINT REFERENCES students(id) ON DELETE RESTRICT,
    student_first_name         VARCHAR(100),
    student_last_name          VARCHAR(100),
    student_email              VARCHAR(255),
    student_document_number    VARCHAR(50),
    student_date_of_birth      DATE,
    student_address            VARCHAR(300),
    student_phone_number       VARCHAR(50),
    student_emergency_contact  VARCHAR(200),
    student_medical_notes      VARCHAR(1000),
    academic_year_id           BIGINT NOT NULL REFERENCES tuition_academic_years(id) ON DELETE RESTRICT,
    requested_level_id         BIGINT NOT NULL REFERENCES tuition_levels(id) ON DELETE RESTRICT,
    requested_course_id        BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    application_type           VARCHAR(30) NOT NULL,
    status                     VARCHAR(40) NOT NULL DEFAULT 'SUBMITTED',
    fee_plan_id                BIGINT NOT NULL REFERENCES tuition_fee_plans(id) ON DELETE RESTRICT,
    enrollment_id              BIGINT REFERENCES enrollments(id) ON DELETE RESTRICT,
    warning_message            VARCHAR(1000),
    admin_notes                VARCHAR(1000),
    submitted_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    approved_at                TIMESTAMP,
    approved_by                BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    created_at                 TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_application_type CHECK (application_type IN ('NEW_STUDENT', 'REGULAR_PROMOTION', 'ADDITIONAL_STUDENT')),
    CONSTRAINT chk_tuition_application_status CHECK (
        status IN (
            'DRAFT',
            'SUBMITTED',
            'SEAT_RESERVED',
            'PAYMENT_PENDING',
            'READY_FOR_ADMIN_APPROVAL',
            'APPROVED',
            'REJECTED',
            'CANCELLED',
            'EXPIRED'
        )
    )
);

CREATE INDEX IF NOT EXISTS idx_tuition_application_guardian ON tuition_applications(guardian_user_id);
CREATE INDEX IF NOT EXISTS idx_tuition_application_student ON tuition_applications(student_id);
CREATE INDEX IF NOT EXISTS idx_tuition_application_course ON tuition_applications(requested_course_id);
CREATE INDEX IF NOT EXISTS idx_tuition_application_status ON tuition_applications(status);
CREATE INDEX IF NOT EXISTS idx_tuition_application_year ON tuition_applications(academic_year_id);

CREATE TABLE IF NOT EXISTS tuition_seat_reservations (
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL UNIQUE REFERENCES tuition_applications(id) ON DELETE CASCADE,
    course_id       BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    quantity        INT NOT NULL DEFAULT 1 CHECK (quantity = 1),
    expires_at      TIMESTAMP NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_seat_status CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_tuition_seat_course ON tuition_seat_reservations(course_id);
CREATE INDEX IF NOT EXISTS idx_tuition_seat_status ON tuition_seat_reservations(status);
CREATE INDEX IF NOT EXISTS idx_tuition_seat_expires ON tuition_seat_reservations(expires_at);

CREATE TABLE IF NOT EXISTS tuition_ledger_entries (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT NOT NULL REFERENCES tuition_applications(id) ON DELETE CASCADE,
    student_id      BIGINT REFERENCES students(id) ON DELETE RESTRICT,
    discount_id     BIGINT REFERENCES tuition_discounts(id) ON DELETE SET NULL,
    concept         VARCHAR(30) NOT NULL,
    gross_amount    NUMERIC(12, 2) NOT NULL CHECK (gross_amount >= 0),
    discount_amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    net_amount      NUMERIC(12, 2) NOT NULL CHECK (net_amount >= 0),
    due_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'MOCK_PENDING',
    mock_reference  VARCHAR(100) UNIQUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_ledger_concept CHECK (concept IN ('TUITION_ENROLLMENT', 'MONTHLY_FEE')),
    CONSTRAINT chk_tuition_ledger_status CHECK (status IN ('MOCK_PENDING', 'MOCK_PAID', 'CANCELLED')),
    CONSTRAINT chk_tuition_ledger_amounts CHECK (discount_amount <= gross_amount AND net_amount = gross_amount - discount_amount)
);

CREATE INDEX IF NOT EXISTS idx_tuition_ledger_application ON tuition_ledger_entries(application_id);
CREATE INDEX IF NOT EXISTS idx_tuition_ledger_student ON tuition_ledger_entries(student_id);
CREATE INDEX IF NOT EXISTS idx_tuition_ledger_status ON tuition_ledger_entries(status);
CREATE INDEX IF NOT EXISTS idx_tuition_ledger_due_date ON tuition_ledger_entries(due_date);
