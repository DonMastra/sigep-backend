-- =============================================================================
-- V16__create_billing_persistence.sql
-- Persistent payment receipt and fiscal invoice workflow.
-- =============================================================================

CREATE TABLE IF NOT EXISTS payments (
    id                       BIGSERIAL PRIMARY KEY,
    student_id               BIGINT NOT NULL,
    amount                   NUMERIC(12, 2) NOT NULL,
    currency                 VARCHAR(3) NOT NULL DEFAULT 'ARS',
    concept                  VARCHAR(500) NOT NULL,
    payment_date             DATE,
    due_date                 DATE NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method           VARCHAR(30),
    receipt_number           VARCHAR(40),
    external_reference       VARCHAR(150),
    creation_key             VARCHAR(128),
    creation_fingerprint     VARCHAR(64),
    confirmation_key         VARCHAR(128),
    confirmation_fingerprint VARCHAR(64),
    confirmed_at             TIMESTAMP,
    confirmed_by             BIGINT,
    notes                    VARCHAR(1000),
    created_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP NOT NULL DEFAULT NOW(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING', 'PAID', 'OVERDUE', 'CANCELLED')),
    CONSTRAINT chk_payment_method CHECK (
        payment_method IS NULL OR payment_method IN ('CASH', 'CREDIT_CARD', 'DEBIT_CARD', 'BANK_TRANSFER', 'CHECK')
    )
);

ALTER TABLE payments ALTER COLUMN payment_date DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN amount TYPE NUMERIC(12, 2);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'ARS';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS external_reference VARCHAR(150);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS creation_key VARCHAR(128);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS creation_fingerprint VARCHAR(64);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS confirmation_key VARCHAR(128);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS confirmation_fingerprint VARCHAR(64);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS confirmed_by BIGINT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_receipt_number ON payments(receipt_number) WHERE receipt_number IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_external_reference ON payments(external_reference) WHERE external_reference IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_creation_key ON payments(creation_key) WHERE creation_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_confirmation_key ON payments(confirmation_key) WHERE confirmation_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payments_student ON payments(student_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status);
CREATE INDEX IF NOT EXISTS idx_payments_due_date ON payments(due_date);

CREATE TABLE IF NOT EXISTS payment_receipts (
    id                BIGSERIAL PRIMARY KEY,
    payment_id        BIGINT NOT NULL UNIQUE REFERENCES payments(id) ON DELETE RESTRICT,
    receipt_number    VARCHAR(40) NOT NULL UNIQUE,
    payer_name        VARCHAR(200) NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL,
    currency          VARCHAR(3) NOT NULL,
    concept           VARCHAR(500) NOT NULL,
    issued_at         TIMESTAMP NOT NULL,
    issued_by         BIGINT NOT NULL,
    document_type     VARCHAR(100) NOT NULL DEFAULT 'X',
    fiscal_disclaimer VARCHAR(120) NOT NULL DEFAULT 'DOCUMENTO NO VALIDO COMO FACTURA',
    created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fiscal_invoices (
    id                           BIGSERIAL PRIMARY KEY,
    payment_id                   BIGINT NOT NULL UNIQUE REFERENCES payments(id) ON DELETE RESTRICT,
    creation_key                 VARCHAR(128) NOT NULL UNIQUE,
    request_fingerprint          VARCHAR(64) NOT NULL,
    authorization_key            VARCHAR(128) UNIQUE,
    status                       VARCHAR(40) NOT NULL,
    issuer_cuit                  VARCHAR(11),
    point_of_sale                INT,
    voucher_type                 INT NOT NULL,
    voucher_number               BIGINT,
    concept                      INT NOT NULL,
    receiver_name                VARCHAR(200) NOT NULL,
    receiver_document_type       INT NOT NULL,
    receiver_document_number     VARCHAR(20) NOT NULL,
    receiver_vat_condition_id    INT NOT NULL,
    issue_date                   DATE NOT NULL,
    service_from                 DATE,
    service_to                   DATE,
    payment_due_date             DATE,
    currency                     VARCHAR(3) NOT NULL,
    exchange_rate                NUMERIC(18, 6) NOT NULL,
    total_amount                 NUMERIC(12, 2) NOT NULL,
    non_taxed_amount             NUMERIC(12, 2) NOT NULL DEFAULT 0,
    net_amount                   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    exempt_amount                NUMERIC(12, 2) NOT NULL DEFAULT 0,
    vat_amount                   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    other_taxes_amount           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    authorization_code           VARCHAR(14),
    authorization_expires_on     DATE,
    authorized_at                TIMESTAMP,
    provider_request_id          VARCHAR(200),
    preflight_errors             VARCHAR(4000),
    last_observations            VARCHAR(4000),
    last_errors                  VARCHAR(4000),
    created_at                   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP NOT NULL DEFAULT NOW(),
    version                      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fiscal_invoice_status CHECK (
        status IN (
            'DRAFT', 'READY', 'QUEUED', 'AUTHORIZING', 'AUTHORIZED',
            'AUTHORIZED_WITH_OBSERVATIONS', 'REJECTED', 'UNKNOWN'
        )
    ),
    CONSTRAINT chk_fiscal_invoice_concept CHECK (concept IN (1, 2, 3))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fiscal_invoice_voucher
    ON fiscal_invoices(issuer_cuit, point_of_sale, voucher_type, voucher_number)
    WHERE voucher_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_fiscal_invoice_status ON fiscal_invoices(status);
CREATE INDEX IF NOT EXISTS idx_fiscal_invoice_created_at ON fiscal_invoices(created_at DESC);

CREATE TABLE IF NOT EXISTS fiscal_invoice_attempts (
    id                  BIGSERIAL PRIMARY KEY,
    invoice_id          BIGINT NOT NULL REFERENCES fiscal_invoices(id) ON DELETE RESTRICT,
    attempt_number      INT NOT NULL,
    type                VARCHAR(30) NOT NULL,
    provider            VARCHAR(30) NOT NULL,
    environment         VARCHAR(30) NOT NULL,
    outcome             VARCHAR(30) NOT NULL,
    provider_request_id VARCHAR(200),
    observations        VARCHAR(4000),
    errors              VARCHAR(4000),
    requested_at        TIMESTAMP NOT NULL,
    responded_at        TIMESTAMP,
    CONSTRAINT uk_fiscal_attempt_number UNIQUE (invoice_id, attempt_number),
    CONSTRAINT chk_fiscal_attempt_type CHECK (type IN ('AUTHORIZATION', 'RECONCILIATION')),
    CONSTRAINT chk_fiscal_attempt_outcome CHECK (outcome IN ('PROCESSING', 'APPROVED', 'REJECTED', 'UNKNOWN', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_fiscal_attempt_invoice ON fiscal_invoice_attempts(invoice_id);

CREATE TABLE IF NOT EXISTS billing_outbox (
    id              BIGSERIAL PRIMARY KEY,
    invoice_id      BIGINT NOT NULL REFERENCES fiscal_invoices(id) ON DELETE RESTRICT,
    event_type      VARCHAR(40) NOT NULL,
    status          VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    attempts        INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_error      VARCHAR(1000),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMP,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_billing_outbox_invoice_type UNIQUE (invoice_id, event_type),
    CONSTRAINT chk_billing_outbox_type CHECK (event_type IN ('AUTHORIZE_INVOICE')),
    CONSTRAINT chk_billing_outbox_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'WAITING_RECONCILIATION', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_billing_outbox_poll
    ON billing_outbox(status, next_attempt_at, created_at);

CREATE TABLE IF NOT EXISTS voucher_sequences (
    id                    BIGSERIAL PRIMARY KEY,
    issuer_cuit           VARCHAR(11) NOT NULL,
    point_of_sale         INT NOT NULL,
    voucher_type          INT NOT NULL,
    last_confirmed_number BIGINT NOT NULL DEFAULT 0,
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_voucher_sequence UNIQUE (issuer_cuit, point_of_sale, voucher_type)
);
