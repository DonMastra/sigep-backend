-- =============================================================================
-- V17__add_fiscal_tax_breakdown.sql
-- Detailed VAT aliquots and other taxes required by WSFE FECAESolicitar.
-- =============================================================================

ALTER TABLE fiscal_invoices
    ADD COLUMN IF NOT EXISTS receiver_address VARCHAR(300);

UPDATE fiscal_invoices
SET receiver_address = 'NO INFORMADO - REGISTRO ANTERIOR A V17'
WHERE receiver_address IS NULL;

ALTER TABLE fiscal_invoices
    ALTER COLUMN receiver_address SET NOT NULL;

CREATE TABLE IF NOT EXISTS fiscal_invoice_vat_subtotals (
    invoice_id  BIGINT NOT NULL REFERENCES fiscal_invoices(id) ON DELETE RESTRICT,
    line_order  INT NOT NULL,
    vat_id      INT NOT NULL,
    base_amount NUMERIC(12, 2) NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    CONSTRAINT pk_fiscal_invoice_vat_subtotals PRIMARY KEY (invoice_id, line_order),
    CONSTRAINT uk_fiscal_invoice_vat_id UNIQUE (invoice_id, vat_id),
    CONSTRAINT chk_fiscal_vat_id CHECK (vat_id > 0),
    CONSTRAINT chk_fiscal_vat_base CHECK (base_amount > 0),
    CONSTRAINT chk_fiscal_vat_amount CHECK (amount >= 0)
);

CREATE TABLE IF NOT EXISTS fiscal_invoice_taxes (
    invoice_id  BIGINT NOT NULL REFERENCES fiscal_invoices(id) ON DELETE RESTRICT,
    line_order  INT NOT NULL,
    tax_id      INT NOT NULL,
    description VARCHAR(200) NOT NULL,
    base_amount NUMERIC(12, 2) NOT NULL,
    rate        NUMERIC(12, 6) NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    CONSTRAINT pk_fiscal_invoice_taxes PRIMARY KEY (invoice_id, line_order),
    CONSTRAINT uk_fiscal_invoice_tax UNIQUE (invoice_id, tax_id, description),
    CONSTRAINT chk_fiscal_tax_id CHECK (tax_id > 0),
    CONSTRAINT chk_fiscal_tax_description CHECK (BTRIM(description) <> ''),
    CONSTRAINT chk_fiscal_tax_base CHECK (base_amount >= 0),
    CONSTRAINT chk_fiscal_tax_rate CHECK (rate >= 0),
    CONSTRAINT chk_fiscal_tax_amount CHECK (amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_fiscal_vat_invoice ON fiscal_invoice_vat_subtotals(invoice_id);
CREATE INDEX IF NOT EXISTS idx_fiscal_tax_invoice ON fiscal_invoice_taxes(invoice_id);
