-- =============================================================================
-- V22__support_partial_payments_and_fiscal_decisions.sql
-- Partial allocations, auditable fiscal closure and reversible collections.
-- =============================================================================

BEGIN;

SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE billing_charges
    ADD COLUMN IF NOT EXISTS base_amount NUMERIC(12, 2),
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fiscal_disposition VARCHAR(30) NOT NULL DEFAULT 'PENDING';

UPDATE billing_charges
SET base_amount = amount
WHERE base_amount IS NULL;

UPDATE billing_charges charge
SET paid_amount = COALESCE((
    SELECT SUM(allocation.amount)
    FROM payment_allocations allocation
    JOIN payments payment ON payment.id = allocation.payment_id
    WHERE allocation.charge_id = charge.id
      AND payment.status = 'PAID'
), 0);

ALTER TABLE billing_charges
    ALTER COLUMN base_amount SET NOT NULL;

ALTER TABLE billing_charges
    DROP CONSTRAINT IF EXISTS chk_billing_charge_status,
    DROP CONSTRAINT IF EXISTS chk_billing_charge_amount,
    DROP CONSTRAINT IF EXISTS chk_billing_charge_paid_amount,
    DROP CONSTRAINT IF EXISTS chk_billing_charge_fiscal_disposition;

UPDATE billing_charges
SET status = CASE
    WHEN status = 'CANCELLED' THEN 'CANCELLED'
    WHEN paid_amount >= amount THEN 'PAID'
    WHEN paid_amount > 0 THEN 'PARTIALLY_PAID'
    ELSE 'OPEN'
END;

ALTER TABLE billing_charges
    ADD CONSTRAINT chk_billing_charge_amount CHECK (base_amount >= 0 AND amount >= base_amount),
    ADD CONSTRAINT chk_billing_charge_paid_amount CHECK (paid_amount >= 0 AND paid_amount <= amount),
    ADD CONSTRAINT chk_billing_charge_status CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    ADD CONSTRAINT chk_billing_charge_fiscal_disposition CHECK (fiscal_disposition IN ('PENDING', 'EXCLUDED'));

ALTER TABLE payment_allocations
    DROP CONSTRAINT IF EXISTS uk_payment_allocation_charge;

CREATE UNIQUE INDEX IF NOT EXISTS uk_payment_allocation_payment_charge
    ON payment_allocations(payment_id, charge_id);
CREATE INDEX IF NOT EXISTS idx_payment_allocation_charge
    ON payment_allocations(charge_id);

ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS chk_payment_status,
    DROP CONSTRAINT IF EXISTS chk_payment_method;

ALTER TABLE payments
    ADD CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'PAID', 'OVERDUE', 'CANCELLED', 'REVERSED')),
    ADD CONSTRAINT chk_payment_method CHECK (
        payment_method IS NULL OR payment_method IN (
            'CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'CHECK', 'AUTOMATIC_DEBIT'
        )
    );

CREATE TABLE IF NOT EXISTS billing_charge_fiscal_decisions (
    id              BIGSERIAL PRIMARY KEY,
    charge_id       BIGINT NOT NULL REFERENCES billing_charges(id) ON DELETE RESTRICT,
    decision        VARCHAR(30) NOT NULL,
    reason          VARCHAR(500),
    decided_by      BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_billing_charge_fiscal_decision_key UNIQUE (idempotency_key),
    CONSTRAINT chk_billing_charge_fiscal_decision CHECK (decision IN ('KEEP_PENDING', 'EXCLUDE_CHARGE')),
    CONSTRAINT chk_billing_charge_fiscal_reason CHECK (
        decision <> 'EXCLUDE_CHARGE' OR (reason IS NOT NULL AND LENGTH(TRIM(reason)) > 0)
    )
);

CREATE INDEX IF NOT EXISTS idx_billing_charge_fiscal_decision_charge
    ON billing_charge_fiscal_decisions(charge_id, created_at DESC);

ALTER TABLE tuition_ledger_entries
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS late_fee_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;

ALTER TABLE tuition_ledger_entries
    DROP CONSTRAINT IF EXISTS tuition_ledger_entries_status_check,
    DROP CONSTRAINT IF EXISTS chk_tuition_ledger_status,
    DROP CONSTRAINT IF EXISTS chk_tuition_ledger_settlement_amounts;

ALTER TABLE tuition_ledger_entries
    ADD CONSTRAINT chk_tuition_ledger_status CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'CANCELLED')),
    ADD CONSTRAINT chk_tuition_ledger_settlement_amounts CHECK (paid_amount >= 0 AND late_fee_amount >= 0);

COMMIT;
