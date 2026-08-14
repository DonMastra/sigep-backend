-- V29 - Audit institution-approved corrections applied after the legacy import.
-- Manual migration. Re-runnable and safe for PostgreSQL/Neon.

BEGIN;

CREATE TABLE IF NOT EXISTS legacy_reconciliation_runs (
    run_id                       varchar(64) PRIMARY KEY,
    original_import_run_id       varchar(64) NOT NULL
        REFERENCES legacy_import_runs(run_id) ON DELETE RESTRICT,
    workbook_sha256              varchar(64) NOT NULL,
    importer_version             varchar(64) NOT NULL,
    target_git_commit            varchar(64) NOT NULL,
    status                       varchar(20) NOT NULL,
    started_at                   timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at                 timestamptz,
    rolled_back_at               timestamptz,
    summary                      jsonb,
    CONSTRAINT chk_legacy_reconciliation_run_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'ROLLED_BACK'))
);

CREATE INDEX IF NOT EXISTS idx_legacy_reconciliation_original_run
    ON legacy_reconciliation_runs(original_import_run_id, started_at);

CREATE TABLE IF NOT EXISTS legacy_reconciliation_decisions (
    id                          bigserial PRIMARY KEY,
    run_id                      varchar(64) NOT NULL
        REFERENCES legacy_reconciliation_runs(run_id) ON DELETE RESTRICT,
    sheet_name                  varchar(80) NOT NULL,
    decision_type               varchar(60) NOT NULL,
    source_key_hash             varchar(64) NOT NULL,
    target_source_key_hash      varchar(64),
    outcome                     varchar(30) NOT NULL,
    created_at                  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_legacy_reconciliation_decision
        UNIQUE (run_id, sheet_name, source_key_hash),
    CONSTRAINT chk_legacy_reconciliation_outcome
        CHECK (outcome IN ('APPLIED', 'CONFIRMED_NO_CHANGE', 'RECORDED_ONLY'))
);

CREATE INDEX IF NOT EXISTS idx_legacy_reconciliation_decision_source
    ON legacy_reconciliation_decisions(sheet_name, source_key_hash);

CREATE TABLE IF NOT EXISTS legacy_reconciliation_changes (
    id                 bigserial PRIMARY KEY,
    run_id             varchar(64) NOT NULL
        REFERENCES legacy_reconciliation_runs(run_id) ON DELETE RESTRICT,
    decision_id        bigint NOT NULL
        REFERENCES legacy_reconciliation_decisions(id) ON DELETE RESTRICT,
    entity_type        varchar(60) NOT NULL,
    target_table       varchar(80) NOT NULL,
    target_id          bigint NOT NULL,
    change_type        varchar(20) NOT NULL,
    previous_state     jsonb NOT NULL DEFAULT '{}'::jsonb,
    new_state          jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at         timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_legacy_reconciliation_change
        UNIQUE (run_id, target_table, target_id, change_type),
    CONSTRAINT chk_legacy_reconciliation_change_type
        CHECK (change_type IN ('CREATED', 'UPDATED', 'RESOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_legacy_reconciliation_change_target
    ON legacy_reconciliation_changes(target_table, target_id);

COMMIT;
