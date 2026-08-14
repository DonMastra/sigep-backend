-- =============================================================================
-- V19__repair_tuition_ledger_statuses.sql
-- Repairs environments where the billing tables from V18 exist but the tuition
-- ledger still uses the legacy MOCK_PENDING/MOCK_PAID values and constraint.
-- Safe to re-run after V18: the UPDATE is idempotent and the constraint is
-- recreated with the current application values.
-- =============================================================================

BEGIN;

SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE tuition_ledger_entries
    DROP CONSTRAINT IF EXISTS chk_tuition_ledger_status;

UPDATE tuition_ledger_entries
SET status = CASE status
    WHEN 'MOCK_PENDING' THEN 'PENDING'
    WHEN 'MOCK_PAID' THEN 'PAID'
    ELSE status
END
WHERE status IN ('MOCK_PENDING', 'MOCK_PAID');

ALTER TABLE tuition_ledger_entries
    ADD CONSTRAINT chk_tuition_ledger_status
        CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')) NOT VALID;

ALTER TABLE tuition_ledger_entries
    VALIDATE CONSTRAINT chk_tuition_ledger_status;

COMMIT;
