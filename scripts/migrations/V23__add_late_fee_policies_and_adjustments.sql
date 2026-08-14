-- =============================================================================
-- V23__add_late_fee_policies_and_adjustments.sql
-- Snapshot-based, one-time late fees for monthly tuition charges.
-- =============================================================================

BEGIN;

SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE tuition_fee_plans
    ADD COLUMN IF NOT EXISTS monthly_due_day INT NOT NULL DEFAULT 20,
    ADD COLUMN IF NOT EXISTS late_fee_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS automatic_debit_monthly BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS automatic_debit_enrollment BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tuition_fee_plans
    DROP CONSTRAINT IF EXISTS chk_tuition_fee_plan_due_day,
    DROP CONSTRAINT IF EXISTS chk_tuition_fee_plan_late_fee;

ALTER TABLE tuition_fee_plans
    ADD CONSTRAINT chk_tuition_fee_plan_due_day CHECK (monthly_due_day BETWEEN 1 AND 28),
    ADD CONSTRAINT chk_tuition_fee_plan_late_fee CHECK (late_fee_percentage BETWEEN 0 AND 100);

ALTER TABLE billing_charges
    ADD COLUMN IF NOT EXISTS late_fee_percentage NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS late_fee_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS automatic_debit_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS late_fee_applied_at TIMESTAMP;

ALTER TABLE billing_charges
    DROP CONSTRAINT IF EXISTS chk_billing_charge_late_fee;

ALTER TABLE billing_charges
    ADD CONSTRAINT chk_billing_charge_late_fee CHECK (late_fee_percentage BETWEEN 0 AND 100);

CREATE TABLE IF NOT EXISTS billing_charge_adjustments (
    id                   BIGSERIAL PRIMARY KEY,
    charge_id            BIGINT NOT NULL REFERENCES billing_charges(id) ON DELETE RESTRICT,
    type                 VARCHAR(30) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    base_amount_snapshot NUMERIC(12, 2) NOT NULL,
    rate_percentage      NUMERIC(5, 2) NOT NULL,
    amount               NUMERIC(12, 2) NOT NULL,
    effective_date       DATE NOT NULL,
    applied_by           BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    application_source   VARCHAR(30) NOT NULL,
    reversal_reason      VARCHAR(500),
    reversed_at          TIMESTAMP,
    reversed_by          BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_billing_charge_adjustment_type CHECK (type IN ('LATE_FEE')),
    CONSTRAINT chk_billing_charge_adjustment_status CHECK (status IN ('ACTIVE', 'REVERSED')),
    CONSTRAINT chk_billing_charge_adjustment_amounts CHECK (
        base_amount_snapshot >= 0 AND rate_percentage BETWEEN 0 AND 100 AND amount >= 0
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_charge_active_late_fee
    ON billing_charge_adjustments(charge_id, type)
    WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_billing_charge_adjustment_charge
    ON billing_charge_adjustments(charge_id, created_at);
CREATE INDEX IF NOT EXISTS idx_billing_charge_late_fee_scan
    ON billing_charges(status, due_date)
    WHERE late_fee_eligible = TRUE AND late_fee_applied_at IS NULL;

COMMIT;
