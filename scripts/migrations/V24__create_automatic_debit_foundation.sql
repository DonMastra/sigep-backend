-- =============================================================================
-- V24__create_automatic_debit_foundation.sql
-- Invoice-first, provider-neutral automatic debit workflow.
-- V24 was never promoted, so this file intentionally replaces its first draft.
-- =============================================================================

BEGIN;

SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE billing_charges
    ADD COLUMN IF NOT EXISTS collection_channel VARCHAR(30) NOT NULL DEFAULT 'REGULAR';

ALTER TABLE billing_charges DROP CONSTRAINT IF EXISTS chk_billing_charge_collection_channel;
ALTER TABLE billing_charges
    ADD CONSTRAINT chk_billing_charge_collection_channel
        CHECK (collection_channel IN ('REGULAR', 'AUTOMATIC_DEBIT'));

CREATE INDEX IF NOT EXISTS idx_billing_charge_collection_channel
    ON billing_charges(collection_channel, status, due_date, id);

-- In development, Hibernate may have created enum checks before the manual
-- migrations were applied. Remove the legacy checks that reject V22 values.
ALTER TABLE billing_charges
    DROP CONSTRAINT IF EXISTS billing_charges_status_check;

ALTER TABLE payments
    DROP CONSTRAINT IF EXISTS payments_status_check,
    DROP CONSTRAINT IF EXISTS payments_payment_method_check;

-- Reconcile V22/V23 audit tables when ddl-auto created them before the manual
-- migrations. CREATE TABLE IF NOT EXISTS alone cannot add these constraints.
ALTER TABLE billing_charge_fiscal_decisions
    ALTER COLUMN created_at SET DEFAULT NOW(),
    DROP CONSTRAINT IF EXISTS billing_charge_fiscal_decisions_decision_check,
    DROP CONSTRAINT IF EXISTS chk_billing_charge_fiscal_decision,
    DROP CONSTRAINT IF EXISTS chk_billing_charge_fiscal_reason,
    DROP CONSTRAINT IF EXISTS billing_charge_fiscal_decisions_decided_by_fkey,
    DROP CONSTRAINT IF EXISTS fk_billing_charge_fiscal_decision_decided_by;

ALTER TABLE billing_charge_fiscal_decisions
    ADD CONSTRAINT chk_billing_charge_fiscal_decision
        CHECK (decision IN ('KEEP_PENDING', 'EXCLUDE_CHARGE')),
    ADD CONSTRAINT chk_billing_charge_fiscal_reason
        CHECK (decision <> 'EXCLUDE_CHARGE' OR (reason IS NOT NULL AND LENGTH(TRIM(reason)) > 0)),
    ADD CONSTRAINT fk_billing_charge_fiscal_decision_decided_by
        FOREIGN KEY (decided_by) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE billing_charge_adjustments
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
    ALTER COLUMN created_at SET DEFAULT NOW(),
    DROP CONSTRAINT IF EXISTS billing_charge_adjustments_type_check,
    DROP CONSTRAINT IF EXISTS billing_charge_adjustments_status_check,
    DROP CONSTRAINT IF EXISTS billing_charge_adjustments_application_source_check,
    DROP CONSTRAINT IF EXISTS chk_billing_charge_adjustment_type,
    DROP CONSTRAINT IF EXISTS chk_billing_charge_adjustment_status,
    DROP CONSTRAINT IF EXISTS chk_billing_charge_adjustment_source,
    DROP CONSTRAINT IF EXISTS chk_billing_charge_adjustment_amounts,
    DROP CONSTRAINT IF EXISTS billing_charge_adjustments_applied_by_fkey,
    DROP CONSTRAINT IF EXISTS billing_charge_adjustments_reversed_by_fkey,
    DROP CONSTRAINT IF EXISTS fk_billing_charge_adjustment_applied_by,
    DROP CONSTRAINT IF EXISTS fk_billing_charge_adjustment_reversed_by;

ALTER TABLE billing_charge_adjustments
    ADD CONSTRAINT chk_billing_charge_adjustment_type CHECK (type IN ('LATE_FEE')),
    ADD CONSTRAINT chk_billing_charge_adjustment_status CHECK (status IN ('ACTIVE', 'REVERSED')),
    ADD CONSTRAINT chk_billing_charge_adjustment_source
        CHECK (application_source IN ('SCHEDULER', 'PAYMENT', 'BILLING_RUN', 'AUTOMATIC_DEBIT', 'ADMIN')),
    ADD CONSTRAINT chk_billing_charge_adjustment_amounts CHECK (
        base_amount_snapshot >= 0 AND rate_percentage BETWEEN 0 AND 100 AND amount >= 0
    ),
    ADD CONSTRAINT fk_billing_charge_adjustment_applied_by
        FOREIGN KEY (applied_by) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_billing_charge_adjustment_reversed_by
        FOREIGN KEY (reversed_by) REFERENCES users(id) ON DELETE RESTRICT;

CREATE TABLE IF NOT EXISTS automatic_debit_mandates (
    id                    BIGSERIAL PRIMARY KEY,
    account_id            BIGINT NOT NULL REFERENCES billing_accounts(id) ON DELETE RESTRICT,
    provider              VARCHAR(30) NOT NULL,
    provider_reference    VARCHAR(200) NOT NULL,
    masked_label          VARCHAR(100) NOT NULL,
    processor_name        VARCHAR(80) NOT NULL,
    instrument_type       VARCHAR(30) NOT NULL,
    scope                  VARCHAR(40) NOT NULL,
    effective_from        DATE NOT NULL,
    status                VARCHAR(30) NOT NULL,
    is_default            BOOLEAN NOT NULL DEFAULT TRUE,
    consent_version       VARCHAR(40) NOT NULL,
    consented_at          TIMESTAMP NOT NULL,
    consented_by          BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    cancelled_at          TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_automatic_debit_mandate_provider_reference UNIQUE (provider, provider_reference),
    CONSTRAINT chk_automatic_debit_mandate_provider CHECK (provider IN ('MANUAL', 'MOCK')),
    CONSTRAINT chk_automatic_debit_mandate_instrument CHECK (instrument_type IN ('CARD', 'BANK_ACCOUNT')),
    CONSTRAINT chk_automatic_debit_mandate_scope CHECK (scope IN ('INSTALLMENTS', 'INSTALLMENTS_AND_ENROLLMENT')),
    CONSTRAINT chk_automatic_debit_mandate_status CHECK (
        status IN ('PENDING_AUTHORIZATION', 'ACTIVE', 'PAUSED', 'CANCELLED', 'EXPIRED')
    )
);

ALTER TABLE automatic_debit_mandates
    ALTER COLUMN is_default SET DEFAULT TRUE,
    ALTER COLUMN created_at SET DEFAULT NOW(),
    ALTER COLUMN updated_at SET DEFAULT NOW(),
    ALTER COLUMN version SET DEFAULT 0,
    DROP CONSTRAINT IF EXISTS automatic_debit_mandates_provider_check,
    DROP CONSTRAINT IF EXISTS automatic_debit_mandates_instrument_type_check,
    DROP CONSTRAINT IF EXISTS automatic_debit_mandates_scope_check,
    DROP CONSTRAINT IF EXISTS automatic_debit_mandates_status_check,
    DROP CONSTRAINT IF EXISTS chk_automatic_debit_mandate_provider,
    DROP CONSTRAINT IF EXISTS chk_automatic_debit_mandate_instrument,
    DROP CONSTRAINT IF EXISTS chk_automatic_debit_mandate_scope,
    DROP CONSTRAINT IF EXISTS chk_automatic_debit_mandate_status,
    DROP CONSTRAINT IF EXISTS automatic_debit_mandates_consented_by_fkey,
    DROP CONSTRAINT IF EXISTS fk_automatic_debit_mandate_consented_by;

ALTER TABLE automatic_debit_mandates
    ADD CONSTRAINT chk_automatic_debit_mandate_provider CHECK (provider IN ('MANUAL', 'MOCK')),
    ADD CONSTRAINT chk_automatic_debit_mandate_instrument CHECK (instrument_type IN ('CARD', 'BANK_ACCOUNT')),
    ADD CONSTRAINT chk_automatic_debit_mandate_scope
        CHECK (scope IN ('INSTALLMENTS', 'INSTALLMENTS_AND_ENROLLMENT')),
    ADD CONSTRAINT chk_automatic_debit_mandate_status
        CHECK (status IN ('PENDING_AUTHORIZATION', 'ACTIVE', 'PAUSED', 'CANCELLED', 'EXPIRED')),
    ADD CONSTRAINT fk_automatic_debit_mandate_consented_by
        FOREIGN KEY (consented_by) REFERENCES users(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_automatic_debit_default_mandate
    ON automatic_debit_mandates(account_id)
    WHERE is_default = TRUE AND status IN ('PENDING_AUTHORIZATION', 'ACTIVE', 'PAUSED');
CREATE INDEX IF NOT EXISTS idx_automatic_debit_mandate_status
    ON automatic_debit_mandates(status, account_id, effective_from);

CREATE TABLE IF NOT EXISTS automatic_debit_instructions (
    id                    BIGSERIAL PRIMARY KEY,
    mandate_id            BIGINT NOT NULL REFERENCES automatic_debit_mandates(id) ON DELETE RESTRICT,
    charge_id             BIGINT NOT NULL REFERENCES billing_charges(id) ON DELETE RESTRICT,
    invoice_id            BIGINT NOT NULL REFERENCES fiscal_invoices(id) ON DELETE RESTRICT,
    payment_id            BIGINT REFERENCES payments(id) ON DELETE RESTRICT,
    idempotency_key       VARCHAR(128) NOT NULL,
    provider_reference    VARCHAR(200),
    submission_reference  VARCHAR(150),
    amount                NUMERIC(12, 2) NOT NULL,
    currency              VARCHAR(3) NOT NULL,
    processing_date       DATE NOT NULL,
    status                VARCHAR(40) NOT NULL,
    failure_code          VARCHAR(80),
    failure_message       VARCHAR(500),
    resolution            VARCHAR(40),
    resolution_reason     VARCHAR(500),
    resolved_by           BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    created_by            BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    submitted_at          TIMESTAMP,
    resolved_at           TIMESTAMP,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_automatic_debit_instruction_key UNIQUE (idempotency_key),
    CONSTRAINT uk_automatic_debit_instruction_provider_reference UNIQUE (provider_reference),
    CONSTRAINT chk_automatic_debit_instruction_amount CHECK (amount > 0),
    CONSTRAINT chk_automatic_debit_instruction_status CHECK (
        status IN (
            'READY_FOR_PROCESSING', 'SUBMITTED', 'APPROVED', 'REJECTED', 'UNKNOWN',
            'ACCOUNTING_RESOLUTION_REQUIRED', 'CREDIT_NOTE_REQUIRED', 'REVERSED', 'CANCELLED'
        )
    ),
    CONSTRAINT chk_automatic_debit_instruction_resolution CHECK (
        (resolution IS NULL AND resolution_reason IS NULL AND resolved_by IS NULL)
        OR
        (resolution IN ('KEEP_INVOICE', 'REQUEST_CREDIT_NOTE') AND resolution_reason IS NOT NULL AND resolved_by IS NOT NULL)
    ),
    CONSTRAINT chk_automatic_debit_resolution_status CHECK (
        (status = 'REJECTED' AND resolution = 'KEEP_INVOICE')
        OR (status = 'CREDIT_NOTE_REQUIRED' AND resolution = 'REQUEST_CREDIT_NOTE')
        OR (status NOT IN ('REJECTED', 'CREDIT_NOTE_REQUIRED') AND resolution IS NULL)
    )
);

ALTER TABLE automatic_debit_instructions
    ALTER COLUMN created_at SET DEFAULT NOW(),
    ALTER COLUMN updated_at SET DEFAULT NOW(),
    ALTER COLUMN version SET DEFAULT 0,
    DROP CONSTRAINT IF EXISTS automatic_debit_instructions_status_check,
    DROP CONSTRAINT IF EXISTS automatic_debit_instructions_resolution_check,
    DROP CONSTRAINT IF EXISTS chk_automatic_debit_instruction_amount,
    DROP CONSTRAINT IF EXISTS chk_automatic_debit_instruction_status,
    DROP CONSTRAINT IF EXISTS chk_automatic_debit_instruction_resolution,
    DROP CONSTRAINT IF EXISTS chk_automatic_debit_resolution_status,
    DROP CONSTRAINT IF EXISTS automatic_debit_instructions_created_by_fkey,
    DROP CONSTRAINT IF EXISTS automatic_debit_instructions_resolved_by_fkey,
    DROP CONSTRAINT IF EXISTS fk_automatic_debit_instruction_created_by,
    DROP CONSTRAINT IF EXISTS fk_automatic_debit_instruction_resolved_by;

ALTER TABLE automatic_debit_instructions
    ADD CONSTRAINT chk_automatic_debit_instruction_amount CHECK (amount > 0),
    ADD CONSTRAINT chk_automatic_debit_instruction_status CHECK (
        status IN (
            'READY_FOR_PROCESSING', 'SUBMITTED', 'APPROVED', 'REJECTED', 'UNKNOWN',
            'ACCOUNTING_RESOLUTION_REQUIRED', 'CREDIT_NOTE_REQUIRED', 'REVERSED', 'CANCELLED'
        )
    ),
    ADD CONSTRAINT chk_automatic_debit_instruction_resolution CHECK (
        (resolution IS NULL AND resolution_reason IS NULL AND resolved_by IS NULL)
        OR
        (resolution IN ('KEEP_INVOICE', 'REQUEST_CREDIT_NOTE') AND resolution_reason IS NOT NULL AND resolved_by IS NOT NULL)
    ),
    ADD CONSTRAINT chk_automatic_debit_resolution_status CHECK (
        (status = 'REJECTED' AND resolution = 'KEEP_INVOICE')
        OR (status = 'CREDIT_NOTE_REQUIRED' AND resolution = 'REQUEST_CREDIT_NOTE')
        OR (status NOT IN ('REJECTED', 'CREDIT_NOTE_REQUIRED') AND resolution IS NULL)
    ),
    ADD CONSTRAINT fk_automatic_debit_instruction_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_automatic_debit_instruction_resolved_by
        FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_automatic_debit_active_charge
    ON automatic_debit_instructions(charge_id)
    WHERE status IN (
        'READY_FOR_PROCESSING', 'SUBMITTED', 'UNKNOWN',
        'ACCOUNTING_RESOLUTION_REQUIRED', 'CREDIT_NOTE_REQUIRED'
    );
CREATE INDEX IF NOT EXISTS idx_automatic_debit_instruction_schedule
    ON automatic_debit_instructions(status, processing_date, created_at);
CREATE INDEX IF NOT EXISTS idx_automatic_debit_instruction_charge
    ON automatic_debit_instructions(charge_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_automatic_debit_instruction_invoice
    ON automatic_debit_instructions(invoice_id, created_at DESC);

CREATE TABLE IF NOT EXISTS automatic_debit_events (
    id                    BIGSERIAL PRIMARY KEY,
    instruction_id        BIGINT NOT NULL REFERENCES automatic_debit_instructions(id) ON DELETE RESTRICT,
    provider_event_id     VARCHAR(200) NOT NULL UNIQUE,
    type                  VARCHAR(50) NOT NULL,
    sanitized_detail      VARCHAR(1000),
    occurred_at           TIMESTAMP NOT NULL,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_automatic_debit_event_type CHECK (
        type IN (
            'PREPARED', 'SUBMITTED', 'APPROVED', 'REJECTED', 'UNKNOWN',
            'RESOLUTION_KEEP_INVOICE', 'RESOLUTION_CREDIT_NOTE_REQUIRED', 'REVERSED', 'CANCELLED'
        )
    )
);

ALTER TABLE automatic_debit_events
    ALTER COLUMN created_at SET DEFAULT NOW(),
    DROP CONSTRAINT IF EXISTS automatic_debit_events_type_check,
    DROP CONSTRAINT IF EXISTS chk_automatic_debit_event_type;

ALTER TABLE automatic_debit_events
    ADD CONSTRAINT chk_automatic_debit_event_type CHECK (
        type IN (
            'PREPARED', 'SUBMITTED', 'APPROVED', 'REJECTED', 'UNKNOWN',
            'RESOLUTION_KEEP_INVOICE', 'RESOLUTION_CREDIT_NOTE_REQUIRED', 'REVERSED', 'CANCELLED'
        )
    );

CREATE INDEX IF NOT EXISTS idx_automatic_debit_event_instruction
    ON automatic_debit_events(instruction_id, occurred_at);

COMMIT;
