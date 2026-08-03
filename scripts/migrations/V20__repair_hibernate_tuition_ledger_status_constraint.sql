-- =============================================================================
-- V20__repair_hibernate_tuition_ledger_status_constraint.sql
-- Repairs QA databases where Hibernate originally created the tuition ledger
-- status check with its generated name. V19 only replaced the explicit
-- chk_tuition_ledger_status constraint, so the legacy generated constraint
-- could continue rejecting the current PENDING and PAID enum values.
-- =============================================================================

BEGIN;

SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE tuition_ledger_entries
    DROP CONSTRAINT IF EXISTS tuition_ledger_entries_status_check;

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
    ALTER COLUMN status SET DEFAULT 'PENDING';

ALTER TABLE tuition_ledger_entries
    ADD CONSTRAINT chk_tuition_ledger_status
        CHECK (status IN ('PENDING', 'PAID', 'CANCELLED')) NOT VALID;

ALTER TABLE tuition_ledger_entries
    VALIDATE CONSTRAINT chk_tuition_ledger_status;

COMMIT;
