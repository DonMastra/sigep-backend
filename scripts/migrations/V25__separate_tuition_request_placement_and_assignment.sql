-- =============================================================================
-- V25__separate_tuition_request_placement_and_assignment.sql
-- Separates the guardian request, enrollment fee, placement and final academic
-- assignment and removes the superseded seat-reservation workflow.
-- =============================================================================

BEGIN;

SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '120s';

CREATE TABLE IF NOT EXISTS tuition_enrollment_fee_policies (
    id                         BIGSERIAL PRIMARY KEY,
    name                       VARCHAR(120) NOT NULL,
    amount                     NUMERIC(12, 2) NOT NULL,
    currency                   VARCHAR(3) NOT NULL DEFAULT 'ARS',
    payment_due_days           INT NOT NULL DEFAULT 3,
    automatic_debit_eligible   BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from                 DATE NOT NULL,
    valid_to                   DATE,
    status                     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    default_policy             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_enrollment_policy_amount CHECK (amount > 0),
    CONSTRAINT chk_tuition_enrollment_policy_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_tuition_enrollment_policy_due_days CHECK (payment_due_days BETWEEN 0 AND 90),
    CONSTRAINT chk_tuition_enrollment_policy_validity CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT chk_tuition_enrollment_policy_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_tuition_enrollment_policy_status
    ON tuition_enrollment_fee_policies(status);
CREATE INDEX IF NOT EXISTS idx_tuition_enrollment_policy_validity
    ON tuition_enrollment_fee_policies(valid_from, valid_to);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tuition_enrollment_policy_default
    ON tuition_enrollment_fee_policies(default_policy)
    WHERE default_policy = TRUE;

-- Preserve the amount snapshot implied by every previously selected fee plan.
INSERT INTO tuition_enrollment_fee_policies (
    name, amount, currency, payment_due_days, automatic_debit_eligible,
    valid_from, valid_to, status, default_policy, created_at, updated_at
)
SELECT
    'Migrada desde plan #' || p.id,
    p.enrollment_fee,
    p.currency,
    3,
    p.automatic_debit_enrollment,
    p.valid_from,
    p.valid_to,
    p.status,
    FALSE,
    NOW(),
    NOW()
FROM tuition_fee_plans p
WHERE p.enrollment_fee > 0
  AND NOT EXISTS (
      SELECT 1 FROM tuition_enrollment_fee_policies ep
      WHERE ep.name = 'Migrada desde plan #' || p.id
  );

UPDATE tuition_enrollment_fee_policies
SET default_policy = TRUE, updated_at = NOW()
WHERE id = (
    SELECT id
    FROM tuition_enrollment_fee_policies
    WHERE status = 'ACTIVE'
    ORDER BY valid_from DESC, id DESC
    LIMIT 1
)
AND NOT EXISTS (
    SELECT 1 FROM tuition_enrollment_fee_policies WHERE default_policy = TRUE
);

ALTER TABLE tuition_applications
    ADD COLUMN IF NOT EXISTS enrollment_fee_policy_id BIGINT;

UPDATE tuition_applications a
SET enrollment_fee_policy_id = ep.id
FROM tuition_enrollment_fee_policies ep
WHERE a.enrollment_fee_policy_id IS NULL
  AND ep.name = 'Migrada desde plan #' || a.fee_plan_id;

ALTER TABLE tuition_applications
    DROP CONSTRAINT IF EXISTS fk_tuition_application_enrollment_policy;
ALTER TABLE tuition_applications
    ADD CONSTRAINT fk_tuition_application_enrollment_policy
        FOREIGN KEY (enrollment_fee_policy_id)
        REFERENCES tuition_enrollment_fee_policies(id)
        ON DELETE RESTRICT;

ALTER TABLE tuition_applications
    ALTER COLUMN academic_year_id DROP NOT NULL,
    ALTER COLUMN fee_plan_id DROP NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'tuition_applications' AND column_name = 'requested_level_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'tuition_applications' AND column_name = 'assigned_level_id'
    ) THEN
        ALTER TABLE tuition_applications RENAME COLUMN requested_level_id TO assigned_level_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'tuition_applications' AND column_name = 'requested_course_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'tuition_applications' AND column_name = 'assigned_course_id'
    ) THEN
        ALTER TABLE tuition_applications RENAME COLUMN requested_course_id TO assigned_course_id;
    END IF;
END $$;

ALTER TABLE tuition_applications
    ALTER COLUMN assigned_level_id DROP NOT NULL,
    ALTER COLUMN assigned_course_id DROP NOT NULL;

-- Hibernate may already have created the target index from the current entity
-- while the legacy requested-course index still exists. Rename only when the
-- target is absent; otherwise remove the superseded legacy index.
DO $$
BEGIN
    IF to_regclass('public.idx_tuition_application_course') IS NOT NULL
       AND to_regclass('public.idx_tuition_application_assigned_course') IS NULL THEN
        ALTER INDEX idx_tuition_application_course
            RENAME TO idx_tuition_application_assigned_course;
    ELSIF to_regclass('public.idx_tuition_application_course') IS NOT NULL
       AND to_regclass('public.idx_tuition_application_assigned_course') IS NOT NULL THEN
        DROP INDEX idx_tuition_application_course;
    END IF;
END $$;

ALTER TABLE tuition_fee_plans
    DROP COLUMN IF EXISTS enrollment_fee,
    DROP COLUMN IF EXISTS automatic_debit_enrollment;

-- Normalize installations that previously exercised the reservation-based
-- workflow before replacing the status constraint. No production data depends
-- on these transitional states.
UPDATE tuition_applications
SET status = CASE status
    WHEN 'DRAFT' THEN 'SUBMITTED'
    WHEN 'SEAT_RESERVED' THEN 'PAYMENT_PENDING'
    WHEN 'READY_FOR_ADMIN_APPROVAL' THEN 'ENROLLED_PENDING_PLACEMENT'
    WHEN 'EXPIRED' THEN 'CANCELLED'
    ELSE status
END,
updated_at = NOW()
WHERE status IN ('DRAFT', 'SEAT_RESERVED', 'READY_FOR_ADMIN_APPROVAL', 'EXPIRED');

ALTER TABLE tuition_applications
    DROP CONSTRAINT IF EXISTS tuition_applications_status_check,
    DROP CONSTRAINT IF EXISTS chk_tuition_application_status;
ALTER TABLE tuition_applications
    ADD CONSTRAINT chk_tuition_application_status CHECK (
        status IN (
            'SUBMITTED', 'PAYMENT_PENDING', 'ENROLLED_PENDING_PLACEMENT',
            'READY_FOR_ACADEMIC_ASSIGNMENT', 'WAITLISTED', 'APPROVED',
            'REJECTED', 'CANCELLED'
        )
    ) NOT VALID;
ALTER TABLE tuition_applications VALIDATE CONSTRAINT chk_tuition_application_status;

CREATE TABLE IF NOT EXISTS tuition_placement_assessments (
    id                    BIGSERIAL PRIMARY KEY,
    application_id        BIGINT NOT NULL UNIQUE REFERENCES tuition_applications(id) ON DELETE CASCADE,
    status                VARCHAR(20) NOT NULL,
    recommended_level_id  BIGINT REFERENCES tuition_levels(id) ON DELETE RESTRICT,
    evaluator_user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    notes                 VARCHAR(2000),
    assessed_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_tuition_placement_status CHECK (status IN ('PENDING', 'COMPLETED', 'WAIVED')),
    CONSTRAINT chk_tuition_placement_level CHECK (status <> 'COMPLETED' OR recommended_level_id IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_tuition_placement_status
    ON tuition_placement_assessments(status);
CREATE INDEX IF NOT EXISTS idx_tuition_placement_level
    ON tuition_placement_assessments(recommended_level_id);

DROP TABLE IF EXISTS tuition_seat_reservations;

COMMIT;
