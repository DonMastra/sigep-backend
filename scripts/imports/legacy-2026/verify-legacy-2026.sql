-- Read-only reconciliation for the latest completed Quinttos legacy import.
-- The result intentionally avoids names, documents, emails and phone numbers.

WITH latest_run AS (
    SELECT run_id, source_manifest_sha256, importer_version, target_git_commit, status, started_at, completed_at, summary
    FROM legacy_import_runs
    WHERE source_system = 'Quinttos legacy'
    ORDER BY started_at DESC
    LIMIT 1
)
SELECT * FROM latest_run;

SELECT entity_type, target_table, mapping_status, count(*) AS mapped_rows
FROM legacy_import_entity_map
WHERE run_id = (SELECT run_id FROM legacy_import_runs WHERE source_system = 'Quinttos legacy' ORDER BY started_at DESC LIMIT 1)
GROUP BY entity_type, target_table, mapping_status
ORDER BY entity_type, target_table, mapping_status;

SELECT issue_code, severity, count(*) AS issue_count
FROM legacy_import_issues
WHERE run_id = (SELECT run_id FROM legacy_import_runs WHERE source_system = 'Quinttos legacy' ORDER BY started_at DESC LIMIT 1)
  AND resolved_at IS NULL
GROUP BY issue_code, severity
ORDER BY severity, issue_code;

SELECT status, count(*) AS enrollments
FROM enrollments
GROUP BY status
ORDER BY status;

SELECT
    count(*) AS students,
    count(DISTINCT student_number) AS distinct_student_numbers,
    count(*) FILTER (WHERE student_number IS NULL OR btrim(student_number) = '') AS missing_student_numbers
FROM students;

SELECT duration, max_students, count(*) AS courses
FROM courses
GROUP BY duration, max_students
ORDER BY duration, max_students;

SELECT due_date, count(*) AS charges, sum(amount) AS nominal_amount
FROM billing_charges
WHERE source_type = 'TUITION_LEDGER'
GROUP BY due_date
ORDER BY due_date;

SELECT
    (SELECT count(*) FROM users) AS users,
    (SELECT count(*) FROM students) AS students,
    (SELECT count(*) FROM teaching_staff) AS teaching_staff,
    (SELECT count(*) FROM tuition_levels) AS tuition_levels,
    (SELECT count(*) FROM courses) AS courses,
    (SELECT count(*) FROM enrollments) AS enrollments,
    (SELECT count(*) FROM tuition_applications) AS tuition_applications,
    (SELECT count(*) FROM tuition_ledger_entries) AS tuition_ledger_entries,
    (SELECT count(*) FROM billing_charges) AS billing_charges;

SELECT
    NOT EXISTS (
        SELECT 1
        FROM enrollments enrollment
        LEFT JOIN students student ON student.id = enrollment.student_id
        LEFT JOIN courses course ON course.id = enrollment.course_id
        WHERE student.id IS NULL OR course.id IS NULL
    ) AS enrollment_references_ok,
    NOT EXISTS (
        SELECT 1
        FROM tuition_ledger_entries ledger
        LEFT JOIN billing_charges charge
          ON charge.source_type = 'TUITION_LEDGER'
         AND charge.source_id = ledger.id
        WHERE charge.id IS NULL
    ) AS ledger_charge_pairs_ok,
    NOT EXISTS (
        SELECT 1
        FROM students student
        LEFT JOIN users guardian ON guardian.id = student.guardian_id
        WHERE student.guardian_id IS NOT NULL
          AND (guardian.id IS NULL OR guardian.role <> 'GUARDIAN')
    ) AS guardian_links_ok;
