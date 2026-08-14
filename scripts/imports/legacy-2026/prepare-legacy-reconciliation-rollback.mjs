import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";

const SQL_BOUNDARY = "-- SIGEP_STATEMENT_BOUNDARY";
const args = parseArgs(process.argv.slice(2));
const outputPath = path.resolve(requiredValue("output"));
const runId = requiredValue("run-id");
const expectedDatabase = args.get("expected-database") ?? "sigep_prod";

if (!/^[A-Za-z0-9._:-]{1,64}$/.test(runId)) throw new Error("Invalid reconciliation run id");

const statements = [
  "BEGIN",
  "SET LOCAL lock_timeout = '10s'",
  "SET LOCAL statement_timeout = '15min'",
  preflightSql(),
  rollbackSql(),
  postflightSql(),
  "COMMIT",
];
const sqlText = statements.map((statement) => `${statement.trim().replace(/;\s*$/, "")};`)
  .join(`\n\n${SQL_BOUNDARY}\n\n`) + "\n";
await fs.mkdir(path.dirname(outputPath), { recursive: true });
await fs.writeFile(outputPath, sqlText, { encoding: "utf8", flag: "wx" });
console.log(JSON.stringify({
  status: "prepared",
  operation: "rollback",
  runId,
  expectedDatabase,
  outputPath,
  sqlSha256: crypto.createHash("sha256").update(sqlText).digest("hex"),
}, null, 2));

function preflightSql() {
  return `DO $$ BEGIN
    IF current_database() <> ${sql(expectedDatabase)} THEN RAISE EXCEPTION 'Target database mismatch'; END IF;
    IF NOT EXISTS (SELECT 1 FROM legacy_reconciliation_runs WHERE run_id=${sql(runId)} AND status='COMPLETED') THEN
      RAISE EXCEPTION 'Reconciliation run is not completed or does not exist';
    END IF;
    IF EXISTS (
      SELECT 1
      FROM legacy_reconciliation_decisions original_decision
      JOIN legacy_reconciliation_runs original_run ON original_run.run_id=original_decision.run_id
      JOIN legacy_reconciliation_decisions later_decision
        ON later_decision.sheet_name=original_decision.sheet_name
       AND later_decision.source_key_hash=original_decision.source_key_hash
      JOIN legacy_reconciliation_runs later_run ON later_run.run_id=later_decision.run_id
      WHERE original_decision.run_id=${sql(runId)}
        AND later_run.original_import_run_id=original_run.original_import_run_id
        AND later_run.status='COMPLETED'
        AND later_run.started_at>original_run.started_at
    ) THEN RAISE EXCEPTION 'A later reconciliation depends on this run'; END IF;
    IF EXISTS (
      SELECT 1 FROM legacy_reconciliation_changes change
      JOIN billing_charges charge ON charge.id=change.target_id
      WHERE change.run_id=${sql(runId)} AND change.target_table='billing_charges'
        AND (
          charge.status<>'OPEN' OR charge.paid_amount<>0 OR charge.late_fee_applied_at IS NOT NULL
          OR EXISTS (SELECT 1 FROM payment_allocations allocation WHERE allocation.charge_id=charge.id)
          OR EXISTS (SELECT 1 FROM fiscal_invoices invoice WHERE invoice.charge_id=charge.id)
          OR EXISTS (SELECT 1 FROM billing_charge_adjustments adjustment WHERE adjustment.charge_id=charge.id)
          OR EXISTS (SELECT 1 FROM automatic_debit_instructions instruction WHERE instruction.charge_id=charge.id)
        )
    ) THEN RAISE EXCEPTION 'Rollback blocked by financial downstream state'; END IF;
    IF EXISTS (
      SELECT 1 FROM legacy_reconciliation_changes change
      JOIN course_sessions session ON session.id=change.target_id
      WHERE change.run_id=${sql(runId)} AND change.target_table='course_sessions' AND change.change_type='CREATED'
        AND (
          session.status<>'SCHEDULED'
          OR
          EXISTS (SELECT 1 FROM course_attendance attendance WHERE attendance.course_session_id=session.id)
          OR EXISTS (SELECT 1 FROM session_exceptions exception WHERE exception.session_id=session.id)
        )
    ) THEN RAISE EXCEPTION 'Rollback blocked by attendance or session exceptions'; END IF;
    IF EXISTS (
      SELECT 1 FROM legacy_reconciliation_changes account_change
      JOIN billing_accounts account ON account.id=account_change.target_id
      WHERE account_change.run_id=${sql(runId)} AND account_change.target_table='billing_accounts' AND account_change.change_type='CREATED'
        AND (
          EXISTS (
            SELECT 1 FROM billing_charges charge
            WHERE charge.account_id=account.id
              AND NOT EXISTS (
                SELECT 1 FROM legacy_reconciliation_changes charge_change
                WHERE charge_change.run_id=${sql(runId)} AND charge_change.target_table='billing_charges' AND charge_change.target_id=charge.id
              )
          )
          OR EXISTS (SELECT 1 FROM automatic_debit_mandates mandate WHERE mandate.account_id=account.id)
        )
    ) THEN RAISE EXCEPTION 'Rollback blocked because a created billing account is now in use'; END IF;
    IF EXISTS (
      SELECT 1 FROM legacy_reconciliation_changes change
      LEFT JOIN students student ON change.target_table='students' AND student.id=change.target_id
      LEFT JOIN tuition_applications application ON change.target_table='tuition_applications' AND application.id=change.target_id
      LEFT JOIN billing_charges charge ON change.target_table='billing_charges' AND charge.id=change.target_id
      LEFT JOIN courses course ON change.target_table='courses' AND course.id=change.target_id
      LEFT JOIN legacy_import_issues issue ON change.target_table='legacy_import_issues' AND issue.id=change.target_id
      WHERE change.run_id=${sql(runId)} AND change.change_type IN ('UPDATED','RESOLVED') AND (
        (change.target_table='students' AND student.guardian_id IS DISTINCT FROM NULLIF(change.new_state->>'guardian_id','')::bigint)
        OR (change.target_table='tuition_applications' AND application.guardian_user_id IS DISTINCT FROM NULLIF(change.new_state->>'guardian_id','')::bigint)
        OR (change.target_table='billing_charges' AND charge.account_id IS DISTINCT FROM NULLIF(change.new_state->>'account_id','')::bigint)
        OR (change.target_table='courses' AND (course.duration IS DISTINCT FROM (change.new_state->>'duration')::int OR course.max_students IS DISTINCT FROM (change.new_state->>'max_students')::int))
        OR (change.target_table='legacy_import_issues' AND (issue.resolved_at IS NULL OR issue.resolution NOT LIKE '%'||${sql(runId)}||'%'))
      )
    ) THEN RAISE EXCEPTION 'Rollback blocked because reconciled state changed after the run'; END IF;
    IF EXISTS (
      SELECT 1 FROM legacy_reconciliation_changes change
      LEFT JOIN tuition_applications application ON change.target_table='tuition_applications' AND application.id=change.target_id
      LEFT JOIN tuition_ledger_entries ledger ON change.target_table='tuition_ledger_entries' AND ledger.id=change.target_id
      LEFT JOIN billing_profiles profile ON change.target_table='billing_profiles' AND profile.id=change.target_id
      LEFT JOIN billing_accounts account ON change.target_table='billing_accounts' AND account.id=change.target_id
      WHERE change.run_id=${sql(runId)} AND change.change_type='CREATED' AND (
        (change.target_table='tuition_applications' AND application.status<>'APPROVED')
        OR (change.target_table='tuition_ledger_entries' AND (ledger.status<>'PENDING' OR ledger.paid_amount<>0 OR ledger.late_fee_amount<>0))
        OR (change.target_table='billing_profiles' AND (profile.status<>'INCOMPLETE' OR profile.version<>0))
        OR (change.target_table='billing_accounts' AND account.version<>0)
      )
    ) THEN RAISE EXCEPTION 'Rollback blocked because a created row was modified after the run'; END IF;
  END $$`;
}

function rollbackSql() {
  return `DO $$ DECLARE student_change record; actor_id_value bigint; original_import_run_id_value varchar(64); BEGIN
    SELECT original_import_run_id INTO original_import_run_id_value FROM legacy_reconciliation_runs WHERE run_id=${sql(runId)} FOR UPDATE;
    SELECT target_id INTO actor_id_value FROM legacy_import_entity_map WHERE run_id=original_import_run_id_value AND entity_type='SYSTEM_ACTOR' AND target_table='users';

    DELETE FROM billing_charges WHERE id IN (
      SELECT target_id FROM legacy_reconciliation_changes WHERE run_id=${sql(runId)} AND target_table='billing_charges' AND change_type='CREATED'
    );
    DELETE FROM tuition_ledger_entries WHERE id IN (
      SELECT target_id FROM legacy_reconciliation_changes WHERE run_id=${sql(runId)} AND target_table='tuition_ledger_entries' AND change_type='CREATED'
    );
    DELETE FROM tuition_applications WHERE id IN (
      SELECT target_id FROM legacy_reconciliation_changes WHERE run_id=${sql(runId)} AND target_table='tuition_applications' AND change_type='CREATED'
    );

    UPDATE billing_charges charge
    SET account_id=(change.previous_state->>'account_id')::bigint,updated_at=now(),version=charge.version+1
    FROM legacy_reconciliation_changes change
    WHERE change.run_id=${sql(runId)} AND change.target_table='billing_charges' AND change.change_type='UPDATED' AND charge.id=change.target_id;
    UPDATE tuition_applications application
    SET guardian_user_id=(change.previous_state->>'guardian_id')::bigint,updated_at=now(),version=application.version+1
    FROM legacy_reconciliation_changes change
    WHERE change.run_id=${sql(runId)} AND change.target_table='tuition_applications' AND change.change_type='UPDATED' AND application.id=change.target_id;

    FOR student_change IN
      SELECT * FROM legacy_reconciliation_changes WHERE run_id=${sql(runId)} AND target_table='students' AND change_type='UPDATED' ORDER BY id DESC
    LOOP
      UPDATE students SET guardian_id=NULLIF(student_change.previous_state->>'guardian_id','')::bigint,updated_at=now() WHERE id=student_change.target_id;
      INSERT INTO student_guardian_link_events(student_id,previous_guardian_user_id,guardian_user_id,action,origin,actor_user_id,reason,created_at)
      VALUES(
        student_change.target_id,
        NULLIF(student_change.new_state->>'guardian_id','')::bigint,
        NULLIF(student_change.previous_state->>'guardian_id','')::bigint,
        CASE WHEN student_change.previous_state->>'guardian_id' IS NULL THEN 'UNLINKED' ELSE 'REASSIGNED' END,
        'ADMIN',actor_id_value,'Rollback reconciliation '||${sql(runId)},now()
      );
    END LOOP;

    DELETE FROM course_sessions WHERE id IN (
      SELECT target_id FROM legacy_reconciliation_changes WHERE run_id=${sql(runId)} AND target_table='course_sessions' AND change_type='CREATED'
    );
    UPDATE courses course
    SET duration=(change.previous_state->>'duration')::int,max_students=(change.previous_state->>'max_students')::int,updated_at=now()
    FROM legacy_reconciliation_changes change
    WHERE change.run_id=${sql(runId)} AND change.target_table='courses' AND change.change_type='UPDATED' AND course.id=change.target_id;

    DELETE FROM billing_profiles WHERE id IN (
      SELECT target_id FROM legacy_reconciliation_changes WHERE run_id=${sql(runId)} AND target_table='billing_profiles' AND change_type='CREATED'
    );
    DELETE FROM billing_accounts WHERE id IN (
      SELECT target_id FROM legacy_reconciliation_changes WHERE run_id=${sql(runId)} AND target_table='billing_accounts' AND change_type='CREATED'
    );

    UPDATE legacy_import_issues issue SET resolved_at=NULL,resolution=NULL
    FROM legacy_reconciliation_changes change
    WHERE change.run_id=${sql(runId)} AND change.target_table='legacy_import_issues' AND change.change_type='RESOLVED' AND issue.id=change.target_id
      AND issue.resolution LIKE '%'||${sql(runId)}||'%';
    UPDATE legacy_reconciliation_runs SET status='ROLLED_BACK',rolled_back_at=now() WHERE run_id=${sql(runId)};
  END $$`;
}

function postflightSql() {
  return `DO $$ BEGIN
    IF EXISTS (
      SELECT 1 FROM legacy_reconciliation_changes change
      WHERE change.run_id=${sql(runId)} AND change.change_type='CREATED' AND (
        (change.target_table='billing_charges' AND EXISTS (SELECT 1 FROM billing_charges row WHERE row.id=change.target_id))
        OR (change.target_table='tuition_ledger_entries' AND EXISTS (SELECT 1 FROM tuition_ledger_entries row WHERE row.id=change.target_id))
        OR (change.target_table='tuition_applications' AND EXISTS (SELECT 1 FROM tuition_applications row WHERE row.id=change.target_id))
        OR (change.target_table='course_sessions' AND EXISTS (SELECT 1 FROM course_sessions row WHERE row.id=change.target_id))
        OR (change.target_table='billing_profiles' AND EXISTS (SELECT 1 FROM billing_profiles row WHERE row.id=change.target_id))
        OR (change.target_table='billing_accounts' AND EXISTS (SELECT 1 FROM billing_accounts row WHERE row.id=change.target_id))
      )
    ) THEN RAISE EXCEPTION 'Created reconciliation rows remain after rollback'; END IF;
    IF EXISTS (
      SELECT 1 FROM legacy_reconciliation_changes change JOIN students student ON student.id=change.target_id
      WHERE change.run_id=${sql(runId)} AND change.target_table='students' AND student.guardian_id IS DISTINCT FROM NULLIF(change.previous_state->>'guardian_id','')::bigint
    ) THEN RAISE EXCEPTION 'Student guardian rollback mismatch'; END IF;
    IF EXISTS (
      SELECT 1 FROM legacy_reconciliation_changes change JOIN legacy_import_issues issue ON issue.id=change.target_id
      WHERE change.run_id=${sql(runId)} AND change.target_table='legacy_import_issues' AND issue.resolved_at IS NOT NULL
    ) THEN RAISE EXCEPTION 'Legacy issue rollback mismatch'; END IF;
    IF NOT EXISTS (SELECT 1 FROM legacy_reconciliation_runs WHERE run_id=${sql(runId)} AND status='ROLLED_BACK') THEN
      RAISE EXCEPTION 'Reconciliation run was not marked rolled back';
    END IF;
  END $$`;
}

function parseArgs(values) {
  const parsedArgs = new Map();
  for (let index = 0; index < values.length; index += 2) {
    const key = values[index];
    const value = values[index + 1];
    if (!key?.startsWith("--") || value === undefined) throw new Error(`Invalid argument near ${key ?? "<end>"}`);
    parsedArgs.set(key.slice(2), value);
  }
  return parsedArgs;
}

function requiredValue(name) {
  const value = args.get(name);
  if (!value) throw new Error(`Missing --${name}`);
  return value;
}

function sql(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}
