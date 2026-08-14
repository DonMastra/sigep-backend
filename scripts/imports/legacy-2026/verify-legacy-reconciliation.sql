-- Read-only reconciliation checks. The latest completed reconciliation is used.

WITH latest AS (
    SELECT run_id, original_import_run_id, workbook_sha256, importer_version,
           target_git_commit, status, started_at, completed_at, summary
    FROM legacy_reconciliation_runs
    ORDER BY started_at DESC
    LIMIT 1
)
SELECT * FROM latest;

WITH latest AS (
    SELECT run_id FROM legacy_reconciliation_runs ORDER BY started_at DESC LIMIT 1
)
SELECT sheet_name, decision_type, outcome, count(*) AS decisions
FROM legacy_reconciliation_decisions
WHERE run_id = (SELECT run_id FROM latest)
GROUP BY sheet_name, decision_type, outcome
ORDER BY sheet_name, decision_type, outcome;

WITH latest AS (
    SELECT run_id FROM legacy_reconciliation_runs ORDER BY started_at DESC LIMIT 1
)
SELECT target_table, change_type, count(*) AS changes
FROM legacy_reconciliation_changes
WHERE run_id = (SELECT run_id FROM latest)
GROUP BY target_table, change_type
ORDER BY target_table, change_type;

WITH latest AS (
    SELECT run_id, original_import_run_id
    FROM legacy_reconciliation_runs
    ORDER BY started_at DESC
    LIMIT 1
)
SELECT severity, issue_code, count(*) AS open_issues
FROM legacy_import_issues
WHERE run_id = (SELECT original_import_run_id FROM latest)
  AND resolved_at IS NULL
GROUP BY severity, issue_code
ORDER BY severity, issue_code;

SELECT
    count(*) FILTER (WHERE session_date BETWEEN DATE '2026-08-01' AND DATE '2026-12-31') AS sessions_aug_dec,
    min(session_date) AS first_session,
    max(session_date) AS last_session
FROM course_sessions;

SELECT
    (SELECT count(*) FROM tuition_applications) AS tuition_applications,
    (SELECT count(*) FROM tuition_ledger_entries) AS tuition_ledger_entries,
    (SELECT count(*) FROM billing_charges) AS billing_charges,
    (SELECT count(*) FROM payments) AS payments;

SELECT
    NOT EXISTS (
        SELECT 1
        FROM tuition_ledger_entries ledger
        LEFT JOIN billing_charges charge
          ON charge.source_type = 'TUITION_LEDGER'
         AND charge.source_id = ledger.id
        WHERE charge.id IS NULL
    ) AS every_ledger_has_charge,
    NOT EXISTS (
        SELECT 1
        FROM students student
        LEFT JOIN users guardian ON guardian.id = student.guardian_id
        WHERE student.guardian_id IS NOT NULL
          AND (guardian.id IS NULL OR guardian.role <> 'GUARDIAN')
    ) AS guardian_links_are_valid;
