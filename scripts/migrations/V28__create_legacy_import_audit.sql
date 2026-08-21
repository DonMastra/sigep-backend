-- V28 - Trace reproducible legacy imports without persisting source payloads.
-- Manual migration. Re-runnable and safe for PostgreSQL/Neon.

BEGIN;

CREATE TABLE IF NOT EXISTS legacy_import_runs (
    run_id                  varchar(64) PRIMARY KEY,
    source_system           varchar(100) NOT NULL,
    source_manifest_sha256  varchar(64) NOT NULL,
    source_manifest         jsonb NOT NULL,
    importer_version        varchar(64) NOT NULL,
    target_git_commit       varchar(64) NOT NULL,
    status                  varchar(20) NOT NULL,
    started_at              timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at            timestamptz,
    summary                 jsonb,
    CONSTRAINT chk_legacy_import_run_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'ROLLED_BACK'))
);

CREATE TABLE IF NOT EXISTS legacy_import_entity_map (
    id               bigserial PRIMARY KEY,
    run_id           varchar(64) NOT NULL REFERENCES legacy_import_runs(run_id) ON DELETE RESTRICT,
    entity_type      varchar(40) NOT NULL,
    source_key_hash  varchar(64) NOT NULL,
    source_row       integer,
    target_table     varchar(80) NOT NULL,
    target_id        bigint NOT NULL,
    mapping_status   varchar(30) NOT NULL DEFAULT 'IMPORTED',
    notes            varchar(500),
    created_at       timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_legacy_import_entity_map
        UNIQUE (run_id, entity_type, source_key_hash, target_table),
    CONSTRAINT chk_legacy_import_mapping_status
        CHECK (mapping_status IN ('IMPORTED', 'PRIMARY_LINK', 'ALTERNATE_LINK', 'PLACEHOLDER_FIELDS'))
);

CREATE INDEX IF NOT EXISTS idx_legacy_import_entity_target
    ON legacy_import_entity_map(target_table, target_id);

CREATE INDEX IF NOT EXISTS idx_legacy_import_entity_source
    ON legacy_import_entity_map(entity_type, source_key_hash);

CREATE TABLE IF NOT EXISTS legacy_import_relationships (
    id                     bigserial PRIMARY KEY,
    run_id                 varchar(64) NOT NULL REFERENCES legacy_import_runs(run_id) ON DELETE RESTRICT,
    relationship_type      varchar(50) NOT NULL,
    left_source_key_hash   varchar(64) NOT NULL,
    right_source_key_hash  varchar(64) NOT NULL,
    selected               boolean NOT NULL DEFAULT false,
    selection_reason       varchar(200),
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_legacy_import_relationship
        UNIQUE (run_id, relationship_type, left_source_key_hash, right_source_key_hash)
);

CREATE INDEX IF NOT EXISTS idx_legacy_import_relationship_left
    ON legacy_import_relationships(relationship_type, left_source_key_hash);

CREATE TABLE IF NOT EXISTS legacy_import_issues (
    id               bigserial PRIMARY KEY,
    run_id           varchar(64) NOT NULL REFERENCES legacy_import_runs(run_id) ON DELETE RESTRICT,
    entity_type      varchar(40) NOT NULL,
    source_key_hash  varchar(64),
    source_row       integer,
    issue_code       varchar(80) NOT NULL,
    severity         varchar(20) NOT NULL,
    details          jsonb NOT NULL DEFAULT '{}'::jsonb,
    resolved_at      timestamptz,
    resolution       varchar(500),
    created_at       timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_legacy_import_issue_severity
        CHECK (severity IN ('INFO', 'WARNING', 'BLOCKER'))
);

CREATE INDEX IF NOT EXISTS idx_legacy_import_issue_open
    ON legacy_import_issues(run_id, severity, issue_code)
    WHERE resolved_at IS NULL;

COMMIT;
