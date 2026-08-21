import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const IMPORTER_VERSION = "legacy-reconciliation-2026-v3";
const DISABLED_PASSWORD_HASH = "$2a$10$zMRfcjxtD9tGfPJ1wbHI/OvWpQa2gEurEaTMmX9cGGlz4o5pvh96K";
const SQL_BOUNDARY = "-- SIGEP_STATEMENT_BOUNDARY";
const COURSE_PERIOD_START = "2026-08-01";
const COURSE_PERIOD_END = "2026-12-31";
const BILLING_DUE_DATES = ["2026-08-10", "2026-09-10", "2026-10-10", "2026-11-10", "2026-12-10"];

const args = parseArgs(process.argv.slice(2));
const workbookPath = requiredArg("workbook");
const outputPath = requiredArg("output");
const artifactToolRoot = requiredArg("artifact-tool-root");
const expectedDatabase = args.get("expected-database") ?? "sigep_prod";
const originalImportRunId = requiredValue("original-import-run-id");
const expectedOriginalManifestSha256 = requiredValue("expected-original-manifest-sha256").toLowerCase();
const targetGitCommit = args.get("git-commit") ?? "UNCOMMITTED";
const runId = args.get("run-id") ?? defaultRunId();

assertIdentifier(runId, "run id");
assertIdentifier(originalImportRunId, "original import run id");
assertSha256(expectedOriginalManifestSha256, "original manifest SHA-256");

const artifactModule = path.join(artifactToolRoot, "dist", "artifact_tool.mjs");
const { FileBlob, SpreadsheetFile } = await import(pathToFileURL(artifactModule).href);
const workbookBytes = await fs.readFile(workbookPath);
const workbookSha256 = sha256(workbookBytes);
const workbook = await SpreadsheetFile.importXlsx(await FileBlob.load(workbookPath));

const parsed = parseWorkbook(workbook);
const summary = {
  missingGuardianTotal: parsed.missing.length,
  missingGuardianConfirmed: parsed.missingConfirmed.length,
  ambiguousGuardianTotal: parsed.ambiguous.length,
  ambiguousGuardianConfirmed: parsed.ambiguousConfirmed.length,
  guardianReassignments: parsed.ambiguousConfirmed.filter((row) => row.finalGuardianHash !== row.currentGuardianHash).length,
  coursesTotal: parsed.courses.length,
  coursesConfirmed: parsed.coursesConfirmed.length,
  generatedCourseSessions: parsed.courseSessions.length,
  institutionalDecisionsTotal: parsed.institutionalDecisions.length,
  institutionalDecisionsConfirmed: parsed.institutionalDecisionsConfirmed.length,
  studentIdentifiersTotal: parsed.studentIdentifiers.length,
  studentIdentifiersConfirmed: parsed.studentIdentifiersConfirmed.length,
};
summary.confirmedTotal = summary.missingGuardianConfirmed
  + summary.ambiguousGuardianConfirmed
  + summary.coursesConfirmed
  + summary.institutionalDecisionsConfirmed
  + summary.studentIdentifiersConfirmed;

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const migrationSql = await fs.readFile(
  path.resolve(scriptDir, "../../migrations/V29__create_legacy_reconciliation_audit.sql"),
  "utf8",
);
const studentIdentifierMigrationSql = await fs.readFile(
  path.resolve(scriptDir, "../../migrations/V30__add_student_business_identifier.sql"),
  "utf8",
);
const sqlText = buildSql({
  ...parsed,
  summary,
  migrationSql,
  studentIdentifierMigrationSql,
  runId,
  workbookSha256,
  expectedDatabase,
  originalImportRunId,
  expectedOriginalManifestSha256,
  targetGitCommit,
});

await fs.mkdir(path.dirname(outputPath), { recursive: true });
await fs.writeFile(outputPath, sqlText, { encoding: "utf8", flag: "wx" });
console.log(JSON.stringify({
  status: "prepared",
  runId,
  originalImportRunId,
  workbookSha256,
  expectedDatabase,
  outputPath,
  summary,
}, null, 2));

function parseWorkbook(sourceWorkbook) {
  const expectedSheetNames = [
    "Resumen",
    "Sin responsable",
    "Responsables ambiguos",
    "Cursos",
    "Decisiones",
    "Catálogo responsables",
    "Identificadores alumnos",
  ];
  const actualSheetNames = sourceWorkbook.worksheets.items.map((sheet) => sheet.name);
  if (actualSheetNames.length !== expectedSheetNames.length
    || actualSheetNames.some((name, index) => name !== expectedSheetNames[index])) {
    throw new Error(`Unexpected workbook sheets: ${actualSheetNames.join(", ")}`);
  }

  const catalog = parseCatalog(tableRows(sourceWorkbook, "Catálogo responsables", [
    "Fila fuente", "Documento", "Apellido", "Nombre", "Nombre completo", "Email", "Teléfono", "Hijo/s informado", "Completitud contacto",
  ], 591));
  const missing = parseMissing(tableRows(sourceWorkbook, "Sin responsable", [
    "Fila fuente", "Usuario legacy", "Matrícula 2026", "Alumno", "Curso 2026", "Situación legacy", "Documento responsable", "Nombre responsable", "Vínculo", "Estado", "Observaciones",
  ], 108), catalog);
  const ambiguous = parseAmbiguous(tableRows(sourceWorkbook, "Responsables ambiguos", [
    "Fila fuente", "Usuario legacy", "Matrícula 2026", "Alumno", "Curso", "Candidato 1 (actual)", "Documento 1", "Contacto 1", "Candidato 2", "Documento 2", "Contacto 2", "Selección importada", "Decisión", "Documento final", "Estado", "Observaciones",
  ], 36), catalog);
  const courses = parseCourses(tableRows(sourceWorkbook, "Cursos", [
    "Curso", "Nivel SiGEP", "Docente legacy", "Alumnos activos", "Bajas", "Capacidad importada", "Cuota mensual", "Duración real (h)", "Capacidad real", "Días", "Hora inicio", "Hora fin", "Aula / modalidad", "Estado", "Observaciones",
  ], 40));
  const institutionalDecisions = parseInstitutionalDecisions(tableRows(sourceWorkbook, "Decisiones", [
    "Tema", "Estado actual", "Respuesta institucional", "Estado", "Responsable", "Observaciones",
  ], 8));
  const studentIdentifiers = parseStudentIdentifiers(tableRows(sourceWorkbook, "Identificadores alumnos", [
    "Fila fuente", "Usuario legacy", "Matrícula alumno", "Alumno", "Ciclo de referencia", "Estado", "Observaciones",
  ], 546));

  assertUnique(missing.map((row) => row.enrollmentHash), "missing-guardian enrollment");
  assertUnique(ambiguous.map((row) => row.enrollmentHash), "ambiguous-guardian enrollment");
  assertUnique([...missing, ...ambiguous].map((row) => row.enrollmentHash), "guardian reconciliation enrollment");
  assertUnique([...missing, ...ambiguous].map((row) => row.studentHash), "guardian reconciliation student");
  assertUnique(courses.map((row) => row.courseHash), "course");
  assertUnique(studentIdentifiers.map((row) => row.studentHash), "student identifier mapping");
  assertUnique(studentIdentifiers.map((row) => row.studentNumber), "student number");

  const coursesConfirmed = courses.filter((row) => row.state === "CONFIRMADO");
  const courseSessions = coursesConfirmed.flatMap(buildCourseSessions);
  return {
    missing,
    missingConfirmed: missing.filter((row) => row.state === "CONFIRMADO"),
    ambiguous,
    ambiguousConfirmed: ambiguous.filter((row) => row.state === "CONFIRMADO"),
    courses,
    coursesConfirmed,
    courseSessions,
    institutionalDecisions,
    institutionalDecisionsConfirmed: institutionalDecisions.filter((row) => row.state === "CONFIRMADO"),
    studentIdentifiers,
    studentIdentifiersConfirmed: studentIdentifiers.filter((row) => row.state === "CONFIRMADO"),
  };
}

function tableRows(sourceWorkbook, sheetName, expectedHeaders, expectedDataRows) {
  const sheet = sourceWorkbook.worksheets.getItem(sheetName);
  const values = sheet.getUsedRange(true)?.values ?? [];
  const expectedRows = expectedDataRows + 5;
  if (values.length !== expectedRows) {
    throw new Error(`${sheetName}: unexpected row count ${values.length}; expected ${expectedRows}`);
  }
  const headers = (values[4] ?? []).slice(0, expectedHeaders.length).map(text);
  if (headers.length !== expectedHeaders.length || headers.some((value, index) => value !== expectedHeaders[index])) {
    throw new Error(`${sheetName}: headers differ from the reviewed reconciliation template`);
  }
  return values.slice(5).map((row, index) => ({
    sourceWorkbookRow: index + 6,
    values: Object.fromEntries(expectedHeaders.map((header, column) => [header, row[column]])),
  }));
}

function parseCatalog(rows) {
  const byDocument = new Map();
  for (const row of rows) {
    const document = digits(row.values.Documento);
    const sourceRow = positiveInteger(row.values["Fila fuente"], "guardian source row", row.sourceWorkbookRow);
    if (!document) throw new Error(`Catálogo responsables row ${row.sourceWorkbookRow}: missing document`);
    if (byDocument.has(document)) throw new Error(`Catálogo responsables: duplicate document ${document}`);
    byDocument.set(document, {
      document,
      sourceRow,
      guardianHash: sourceHash("guardian", document),
    });
  }
  return byDocument;
}

function parseMissing(rows, catalog) {
  return rows.map((row) => {
    const values = row.values;
    const sourceRow = positiveInteger(values["Fila fuente"], "source row", row.sourceWorkbookRow);
    const legacyUser = digits(values["Usuario legacy"]);
    const enrollmentNumber = digits(values["Matrícula 2026"]);
    const state = reconciliationState(values.Estado, row.sourceWorkbookRow);
    if (!legacyUser || !enrollmentNumber || !text(values.Alumno) || !text(values["Curso 2026"])) {
      throw new Error(`Sin responsable row ${row.sourceWorkbookRow}: immutable identity fields are incomplete`);
    }
    const guardianToken = normalize(values["Documento responsable"]);
    const selfGuardian = guardianToken === "AUTOTUTELA";
    const guardianDocument = selfGuardian ? null : digits(values["Documento responsable"]);
    if (selfGuardian && !normalize(values["Curso 2026"]).startsWith("ADULTS ")) {
      throw new Error(`Sin responsable row ${row.sourceWorkbookRow}: AUTOTUTELA is only valid for Adults`);
    }
    if (state === "CONFIRMADO" && !guardianDocument && !selfGuardian) {
      throw new Error(`Sin responsable row ${row.sourceWorkbookRow}: confirmed row has no guardian document`);
    }
    if (guardianDocument && !catalog.has(guardianDocument)) {
      throw new Error(`Sin responsable row ${row.sourceWorkbookRow}: guardian document is not in the catalog`);
    }
    // The fixed UAT source run (legacy-2026-v1) persisted enrollment mappings using
    // only the legacy Matricula value. Keep that historical key for reconciliation;
    // fresh imports use Matricula + course to support simultaneous enrollments.
    const enrollmentHash = sourceHash("enrollment-2026", enrollmentNumber);
    return {
      sheetName: "Sin responsable",
      mode: selfGuardian ? "SELF_GUARDIAN" : "MISSING",
      sourceRow,
      legacyUser,
      enrollmentNumber,
      enrollmentHash,
      applicationHash: sha256(`application:${enrollmentHash}`),
      studentHash: sourceHash("student", legacyUser),
      studentName: text(values.Alumno),
      courseName: text(values["Curso 2026"]),
      currentGuardianHash: null,
      finalGuardianHash: selfGuardian ? sourceHash("self-guardian", legacyUser) : (guardianDocument ? catalog.get(guardianDocument).guardianHash : null),
      state,
    };
  });
}

function parseAmbiguous(rows, catalog) {
  return rows.map((row) => {
    const values = row.values;
    const sourceRow = positiveInteger(values["Fila fuente"], "source row", row.sourceWorkbookRow);
    const legacyUser = digits(values["Usuario legacy"]);
    const enrollmentNumber = digits(values["Matrícula 2026"]);
    const document1 = digits(values["Documento 1"]);
    const document2 = digits(values["Documento 2"]);
    const finalDocument = digits(values["Documento final"]);
    const state = reconciliationState(values.Estado, row.sourceWorkbookRow);
    const decision = normalize(values.Decisión);
    if (!legacyUser || !enrollmentNumber || !document1 || !document2 || !text(values.Alumno) || !text(values.Curso)) {
      throw new Error(`Responsables ambiguos row ${row.sourceWorkbookRow}: immutable identity fields are incomplete`);
    }
    for (const document of [document1, document2, finalDocument].filter(Boolean)) {
      if (!catalog.has(document)) {
        throw new Error(`Responsables ambiguos row ${row.sourceWorkbookRow}: document ${document} is not in the catalog`);
      }
    }
    if (state === "CONFIRMADO") {
      if (!["CANDIDATO 1", "CANDIDATO 2", "OTRO"].includes(decision)) {
        throw new Error(`Responsables ambiguos row ${row.sourceWorkbookRow}: confirmed decision must be CANDIDATO 1, CANDIDATO 2 or OTRO`);
      }
      if (!finalDocument) throw new Error(`Responsables ambiguos row ${row.sourceWorkbookRow}: confirmed row has no final document`);
      if (decision === "CANDIDATO 1" && finalDocument !== document1) {
        throw new Error(`Responsables ambiguos row ${row.sourceWorkbookRow}: CANDIDATO 1 does not match Documento final`);
      }
      if (decision === "CANDIDATO 2" && finalDocument !== document2) {
        throw new Error(`Responsables ambiguos row ${row.sourceWorkbookRow}: CANDIDATO 2 does not match Documento final`);
      }
    }
    const enrollmentHash = sourceHash("enrollment-2026", enrollmentNumber);
    return {
      sheetName: "Responsables ambiguos",
      mode: "AMBIGUOUS",
      sourceRow,
      legacyUser,
      enrollmentNumber,
      enrollmentHash,
      applicationHash: sha256(`application:${enrollmentHash}`),
      studentHash: sourceHash("student", legacyUser),
      studentName: text(values.Alumno),
      courseName: text(values.Curso),
      currentGuardianHash: sourceHash("guardian", document1),
      finalGuardianHash: finalDocument ? sourceHash("guardian", finalDocument) : null,
      state,
    };
  });
}

function parseCourses(rows) {
  return rows.map((row) => {
    const values = row.values;
    const name = text(values.Curso);
    const state = reconciliationState(values.Estado, row.sourceWorkbookRow);
    if (!name || !text(values["Nivel SiGEP"]) || !text(values["Docente legacy"])) {
      throw new Error(`Cursos row ${row.sourceWorkbookRow}: immutable identity fields are incomplete`);
    }
    const base = {
      sheetName: "Cursos",
      sourceWorkbookRow: row.sourceWorkbookRow,
      courseHash: sourceHash("course-2026", name),
      name,
      activeStudents: nonNegativeInteger(values["Alumnos activos"], "active students", row.sourceWorkbookRow),
      state,
    };
    if (state !== "CONFIRMADO") return base;
    const durationHours = positiveInteger(values["Duración real (h)"], "duration hours", row.sourceWorkbookRow);
    const capacity = positiveInteger(values["Capacidad real"], "capacity", row.sourceWorkbookRow);
    if (capacity < base.activeStudents) {
      throw new Error(`Cursos row ${row.sourceWorkbookRow}: capacity is below active enrollment count`);
    }
    if (capacity > 1000) throw new Error(`Cursos row ${row.sourceWorkbookRow}: capacity exceeds 1000`);
    const rawDays = values.Días;
    const rawStartTime = values["Hora inicio"];
    const rawEndTime = values["Hora fin"];
    const scheduleFields = [rawDays, rawStartTime, rawEndTime].map(text);
    const hasSchedule = scheduleFields.some(Boolean);
    if (hasSchedule && scheduleFields.some((value) => !value)) {
      throw new Error(`Cursos row ${row.sourceWorkbookRow}: days, start time and end time must be provided together`);
    }
    if (!hasSchedule) {
      if (base.activeStudents > 0) {
        throw new Error(`Cursos row ${row.sourceWorkbookRow}: an active course requires a schedule`);
      }
      return { ...base, durationHours, capacity, daysOfWeek: [], startTime: null, endTime: null, classroomName: null };
    }
    const daysOfWeek = parseDays(rawDays, row.sourceWorkbookRow);
    const startTime = parseTime(rawStartTime, "start time", row.sourceWorkbookRow);
    const endTime = parseTime(rawEndTime, "end time", row.sourceWorkbookRow);
    if (timeToMinutes(endTime) <= timeToMinutes(startTime)) {
      throw new Error(`Cursos row ${row.sourceWorkbookRow}: end time must be after start time`);
    }
    const classroomName = text(values["Aula / modalidad"]) || null;
    if (classroomName && classroomName.length > 255) throw new Error(`Cursos row ${row.sourceWorkbookRow}: classroom or modality is too long`);
    return { ...base, durationHours, capacity, daysOfWeek, startTime, endTime, classroomName };
  });
}

function parseInstitutionalDecisions(rows) {
  return rows.map((row) => {
    const values = row.values;
    const topic = text(values.Tema);
    const state = reconciliationState(values.Estado, row.sourceWorkbookRow);
    const response = text(values["Respuesta institucional"]);
    const responsible = text(values.Responsable);
    const observations = text(values.Observaciones);
    if (!topic) throw new Error(`Decisiones row ${row.sourceWorkbookRow}: missing topic`);
    if (state === "CONFIRMADO" && (!response || !responsible)) {
      throw new Error(`Decisiones row ${row.sourceWorkbookRow}: confirmed decision requires response and responsible`);
    }
    const normalizedTopic = normalize(topic);
    let action = "RECORD_ONLY";
    let amount = null;
    if (normalizedTopic === "MATRICULA GENERAL 2026" && state === "CONFIRMADO") {
      action = "CREATE_GENERAL_ENROLLMENT_FEE_POLICY";
      amount = firstMoneyAmount(response);
      if (amount !== 90000) {
        throw new Error(`Decisiones row ${row.sourceWorkbookRow}: general enrollment fee must be ARS 90000`);
      }
    }
    return {
      sheetName: "Decisiones",
      topic,
      sourceKeyHash: sourceHash("institutional-decision-2026", topic),
      decisionHash: state === "CONFIRMADO" ? sha256([normalize(response), normalize(responsible), normalize(observations)].join("|")) : null,
      action,
      amount,
      state,
    };
  });
}

function parseStudentIdentifiers(rows) {
  return rows.map((row) => {
    const values = row.values;
    const sourceRow = positiveInteger(values["Fila fuente"], "student source row", row.sourceWorkbookRow);
    const legacyUser = digits(values["Usuario legacy"]);
    const studentNumber = digits(values["Matrícula alumno"]);
    const state = reconciliationState(values.Estado, row.sourceWorkbookRow);
    if (!legacyUser || !studentNumber || !text(values.Alumno) || !text(values["Ciclo de referencia"])) {
      throw new Error(`Identificadores alumnos row ${row.sourceWorkbookRow}: required fields are incomplete`);
    }
    return {
      sheetName: "Identificadores alumnos",
      sourceRow,
      legacyUser,
      studentHash: sourceHash("student", legacyUser),
      studentNumber,
      studentNumberHash: sha256(studentNumber),
      state,
    };
  });
}

function buildCourseSessions(course) {
  if (!course.daysOfWeek.length) return [];
  const allowedDays = new Set(course.daysOfWeek);
  const sessions = [];
  for (let cursor = isoDate(COURSE_PERIOD_START); cursor <= isoDate(COURSE_PERIOD_END); cursor = addUtcDays(cursor, 1)) {
    if (allowedDays.has(cursor.getUTCDay())) {
      sessions.push({
        courseHash: course.courseHash,
        sessionDate: cursor.toISOString().slice(0, 10),
        startTime: course.startTime,
        endTime: course.endTime,
        classroomName: course.classroomName,
      });
    }
  }
  if (!sessions.length) throw new Error(`Course ${course.name}: no recurring sessions were generated`);
  return sessions;
}

function buildSql(input) {
  const statements = [
    "BEGIN",
    "SET LOCAL lock_timeout = '10s'",
    "SET LOCAL statement_timeout = '15min'",
    ...splitSimpleMigration(input.migrationSql),
    ...splitSimpleMigration(input.studentIdentifierMigrationSql),
    ...stageStatements(input),
    preflightSql(input),
    `INSERT INTO legacy_reconciliation_runs (run_id,original_import_run_id,workbook_sha256,importer_version,target_git_commit,status) VALUES (${sql(input.runId)},${sql(input.originalImportRunId)},${sql(input.workbookSha256)},'${IMPORTER_VERSION}',${sql(input.targetGitCommit)},'RUNNING')`,
    applyGuardianDecisionsSql(input),
    applyCourseDecisionsSql(input),
    applyStudentIdentifierDecisionsSql(input),
    applyInstitutionalDecisionsSql(input),
    postflightSql(input),
    `UPDATE legacy_reconciliation_runs SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,summary=${sql(JSON.stringify(input.summary))}::jsonb WHERE run_id=${sql(input.runId)}`,
    `INSERT INTO schema_version(version,git_commit,description) VALUES ('V29',${sql(input.targetGitCommit)},'Legacy 2026 institutional reconciliation audit') ON CONFLICT(version) DO UPDATE SET git_commit=EXCLUDED.git_commit,applied_at=CURRENT_TIMESTAMP,description=EXCLUDED.description`,
    `INSERT INTO schema_version(version,git_commit,description) VALUES ('V30',${sql(input.targetGitCommit)},'Student business identifier') ON CONFLICT(version) DO UPDATE SET git_commit=EXCLUDED.git_commit,applied_at=CURRENT_TIMESTAMP,description=EXCLUDED.description`,
    "COMMIT",
  ];
  return statements.map((statement) => `${statement.trim().replace(/;\s*$/, "")};`)
    .join(`\n\n${SQL_BOUNDARY}\n\n`) + "\n";
}

function splitSimpleMigration(value) {
  return value.replace(/--.*$/gm, "")
    .replace(/^\s*BEGIN\s*;/i, "")
    .replace(/COMMIT\s*;\s*$/i, "")
    .split(";")
    .map((part) => part.trim())
    .filter(Boolean);
}

function stageStatements(input) {
  const expectedIssues = [
    ...input.missing.map((row) => [row.enrollmentHash, "MISSING_GUARDIAN_MATCH", row.sourceRow, row.studentHash]),
    ...input.ambiguous.map((row) => [row.enrollmentHash, "MULTIPLE_GUARDIAN_MATCHES", row.sourceRow, row.studentHash]),
  ];
  const guardianDecisions = [...input.missingConfirmed, ...input.ambiguousConfirmed];
  const billingPeriods = input.missingConfirmed.flatMap((row) => BILLING_DUE_DATES.map((dueDate) => [
    row.enrollmentHash,
    dueDate,
    sha256(`ledger:${row.enrollmentHash}:${dueDate}`),
  ]));
  return [
    "CREATE TEMP TABLE stage_expected_guardian_issues(enrollment_hash varchar(64),issue_code varchar(80),source_row int,student_hash varchar(64)) ON COMMIT DROP",
    insertValues("stage_expected_guardian_issues", ["enrollment_hash", "issue_code", "source_row", "student_hash"], expectedIssues),
    "CREATE TEMP TABLE stage_guardian_decisions(sheet_name text,mode text,source_row int,legacy_user text,enrollment_hash varchar(64),application_hash varchar(64),student_hash varchar(64),current_guardian_hash varchar(64),final_guardian_hash varchar(64)) ON COMMIT DROP",
    insertValues("stage_guardian_decisions", ["sheet_name", "mode", "source_row", "legacy_user", "enrollment_hash", "application_hash", "student_hash", "current_guardian_hash", "final_guardian_hash"], guardianDecisions.map((row) => [
      row.sheetName, row.mode, row.sourceRow, row.legacyUser, row.enrollmentHash, row.applicationHash, row.studentHash,
      row.currentGuardianHash, row.finalGuardianHash,
    ])),
    "CREATE TEMP TABLE stage_guardian_billing_periods(enrollment_hash varchar(64),due_date date,ledger_hash varchar(64)) ON COMMIT DROP",
    insertValues("stage_guardian_billing_periods", ["enrollment_hash", "due_date", "ledger_hash"], billingPeriods),
    "CREATE TEMP TABLE stage_expected_courses(course_hash varchar(64),course_name text) ON COMMIT DROP",
    insertValues("stage_expected_courses", ["course_hash", "course_name"], input.courses.map((row) => [row.courseHash, row.name])),
    "CREATE TEMP TABLE stage_course_decisions(course_hash varchar(64),course_name text,duration_hours int,max_students int,expected_sessions int) ON COMMIT DROP",
    insertValues("stage_course_decisions", ["course_hash", "course_name", "duration_hours", "max_students", "expected_sessions"], input.coursesConfirmed.map((row) => [
      row.courseHash, row.name, row.durationHours, row.capacity,
      input.courseSessions.filter((session) => session.courseHash === row.courseHash).length,
    ])),
    "CREATE TEMP TABLE stage_course_sessions(course_hash varchar(64),session_date date,start_time time,end_time time,classroom_name text) ON COMMIT DROP",
    insertValues("stage_course_sessions", ["course_hash", "session_date", "start_time", "end_time", "classroom_name"], input.courseSessions.map((row) => [
      row.courseHash, row.sessionDate, row.startTime, row.endTime, row.classroomName,
    ])),
    "CREATE TEMP TABLE stage_institutional_decisions(source_key_hash varchar(64),decision_hash varchar(64),action text,amount numeric(12,2)) ON COMMIT DROP",
    insertValues("stage_institutional_decisions", ["source_key_hash", "decision_hash", "action", "amount"], input.institutionalDecisionsConfirmed.map((row) => [row.sourceKeyHash, row.decisionHash, row.action, row.amount])),
    "CREATE TEMP TABLE stage_student_identifiers(source_row int,student_hash varchar(64),student_number varchar(32),student_number_hash varchar(64)) ON COMMIT DROP",
    insertValues("stage_student_identifiers", ["source_row", "student_hash", "student_number", "student_number_hash"], input.studentIdentifiersConfirmed.map((row) => [row.sourceRow, row.studentHash, row.studentNumber, row.studentNumberHash])),
  ];
}

function preflightSql(input) {
  return `DO $$ DECLARE mismatch_count bigint; BEGIN
    IF current_database() <> ${sql(input.expectedDatabase)} THEN RAISE EXCEPTION 'Target database mismatch'; END IF;
    IF NOT EXISTS (SELECT 1 FROM schema_version WHERE version='V28') THEN RAISE EXCEPTION 'Schema V28 is not recorded'; END IF;
    IF NOT EXISTS (
      SELECT 1 FROM legacy_import_runs
      WHERE run_id=${sql(input.originalImportRunId)} AND status='COMPLETED'
        AND source_manifest_sha256=${sql(input.expectedOriginalManifestSha256)}
    ) THEN RAISE EXCEPTION 'Original import run or manifest mismatch'; END IF;
    IF EXISTS (SELECT 1 FROM legacy_reconciliation_runs WHERE run_id=${sql(input.runId)}) THEN
      RAISE EXCEPTION 'Reconciliation run already exists';
    END IF;
    IF (SELECT count(*) FROM stage_expected_guardian_issues) <> 144 THEN
      RAISE EXCEPTION 'Guardian issue template is incomplete';
    END IF;
    IF (SELECT count(*) FROM legacy_import_issues WHERE run_id=${sql(input.originalImportRunId)} AND issue_code IN ('MISSING_GUARDIAN_MATCH','MULTIPLE_GUARDIAN_MATCHES')) <> 144 THEN
      RAISE EXCEPTION 'Original guardian issue set no longer matches the reviewed import';
    END IF;
    SELECT count(*) INTO mismatch_count
    FROM stage_expected_guardian_issues expected
    LEFT JOIN legacy_import_issues issue
      ON issue.run_id=${sql(input.originalImportRunId)}
     AND issue.source_key_hash=expected.enrollment_hash
     AND issue.issue_code=expected.issue_code
     AND issue.source_row=expected.source_row
    WHERE issue.id IS NULL;
    IF mismatch_count <> 0 THEN RAISE EXCEPTION 'Workbook guardian rows do not match original import issues'; END IF;
    IF (SELECT count(*) FROM stage_expected_courses) <> 40 THEN RAISE EXCEPTION 'Course template is incomplete'; END IF;
    IF (SELECT count(*) FROM legacy_import_entity_map WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='COURSE' AND target_table='courses') <> 40 THEN
      RAISE EXCEPTION 'Original course mapping set no longer matches the reviewed import';
    END IF;
    SELECT count(*) INTO mismatch_count
    FROM stage_expected_courses expected
    LEFT JOIN legacy_import_entity_map mapping
      ON mapping.run_id=${sql(input.originalImportRunId)}
     AND mapping.entity_type='COURSE'
     AND mapping.source_key_hash=expected.course_hash
     AND mapping.target_table='courses'
    WHERE mapping.id IS NULL;
    IF mismatch_count <> 0 THEN RAISE EXCEPTION 'Workbook courses do not match the original import'; END IF;
    IF (SELECT count(*) FROM stage_student_identifiers) <> 546 THEN RAISE EXCEPTION 'Student identifier template is incomplete'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_student_identifiers staged
      LEFT JOIN legacy_import_entity_map student_map
        ON student_map.run_id=${sql(input.originalImportRunId)}
       AND student_map.entity_type='STUDENT'
       AND student_map.source_key_hash=staged.student_hash
       AND student_map.source_row=staged.source_row
       AND student_map.target_table='students'
      WHERE student_map.id IS NULL
    ) THEN RAISE EXCEPTION 'Student identifiers do not match the original import'; END IF;
    IF EXISTS (SELECT 1 FROM stage_institutional_decisions WHERE action='CREATE_GENERAL_ENROLLMENT_FEE_POLICY' AND amount<>90000) THEN
      RAISE EXCEPTION 'General enrollment fee amount differs from the approved ARS 90000';
    END IF;
    IF EXISTS (SELECT 1 FROM stage_institutional_decisions WHERE action='CREATE_GENERAL_ENROLLMENT_FEE_POLICY')
       AND EXISTS (SELECT 1 FROM tuition_enrollment_fee_policies WHERE default_policy=true AND status='ACTIVE' AND valid_from<=DATE '2026-12-31' AND COALESCE(valid_to,DATE '9999-12-31')>=DATE '2026-04-01') THEN
      RAISE EXCEPTION 'An active default enrollment fee policy already overlaps 2026';
    END IF;
    IF EXISTS (
      SELECT 1 FROM (
        SELECT sheet_name, enrollment_hash AS source_key_hash FROM stage_guardian_decisions
        UNION ALL SELECT 'Cursos', course_hash FROM stage_course_decisions
        UNION ALL SELECT 'Decisiones', source_key_hash FROM stage_institutional_decisions
        UNION ALL SELECT 'Identificadores alumnos', student_hash FROM stage_student_identifiers
      ) staged
      JOIN legacy_reconciliation_decisions decision
        ON decision.sheet_name=staged.sheet_name AND decision.source_key_hash=staged.source_key_hash
      JOIN legacy_reconciliation_runs run ON run.run_id=decision.run_id
      WHERE run.original_import_run_id=${sql(input.originalImportRunId)} AND run.status='COMPLETED'
    ) THEN RAISE EXCEPTION 'A confirmed row was already applied by an earlier reconciliation'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_guardian_decisions staged
      LEFT JOIN legacy_import_entity_map enrollment_map
        ON enrollment_map.run_id=${sql(input.originalImportRunId)}
       AND enrollment_map.entity_type='ENROLLMENT'
       AND enrollment_map.source_key_hash=staged.enrollment_hash
       AND enrollment_map.source_row=staged.source_row
       AND enrollment_map.target_table='enrollments'
      LEFT JOIN legacy_import_entity_map student_map
        ON student_map.run_id=${sql(input.originalImportRunId)}
       AND student_map.entity_type='STUDENT'
       AND student_map.source_key_hash=staged.student_hash
       AND student_map.target_table='students'
      LEFT JOIN legacy_import_entity_map guardian_map
        ON guardian_map.run_id=${sql(input.originalImportRunId)}
       AND guardian_map.entity_type='GUARDIAN'
       AND guardian_map.source_key_hash=staged.final_guardian_hash
       AND guardian_map.target_table='users'
      LEFT JOIN users guardian ON guardian.id=guardian_map.target_id AND guardian.role='GUARDIAN'
      WHERE enrollment_map.id IS NULL OR student_map.id IS NULL OR (staged.mode<>'SELF_GUARDIAN' AND guardian.id IS NULL)
    ) THEN RAISE EXCEPTION 'Confirmed guardian decision cannot be resolved against original mappings'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_guardian_decisions staged
      JOIN legacy_import_entity_map student_map ON student_map.run_id=${sql(input.originalImportRunId)} AND student_map.entity_type='STUDENT' AND student_map.source_key_hash=staged.student_hash AND student_map.target_table='students'
      JOIN students student ON student.id=student_map.target_id
      LEFT JOIN legacy_import_entity_map current_guardian_map ON current_guardian_map.run_id=${sql(input.originalImportRunId)} AND current_guardian_map.entity_type='GUARDIAN' AND current_guardian_map.source_key_hash=staged.current_guardian_hash AND current_guardian_map.target_table='users'
      WHERE (staged.mode IN ('MISSING','SELF_GUARDIAN') AND student.guardian_id IS NOT NULL)
         OR (staged.mode='AMBIGUOUS' AND student.guardian_id IS DISTINCT FROM current_guardian_map.target_id)
    ) THEN RAISE EXCEPTION 'Student guardian state changed after the original import'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_guardian_decisions staged
      JOIN legacy_import_entity_map enrollment_map ON enrollment_map.run_id=${sql(input.originalImportRunId)} AND enrollment_map.entity_type='ENROLLMENT' AND enrollment_map.source_key_hash=staged.enrollment_hash AND enrollment_map.target_table='enrollments'
      JOIN enrollments enrollment ON enrollment.id=enrollment_map.target_id
      JOIN legacy_import_entity_map current_guardian_map ON current_guardian_map.run_id=${sql(input.originalImportRunId)} AND current_guardian_map.entity_type='GUARDIAN' AND current_guardian_map.source_key_hash=staged.current_guardian_hash AND current_guardian_map.target_table='users'
      WHERE staged.mode='AMBIGUOUS' AND (
        (SELECT count(*) FROM tuition_applications application_count WHERE application_count.enrollment_id=enrollment.id)<>1
        OR EXISTS (SELECT 1 FROM tuition_applications application WHERE application.enrollment_id=enrollment.id AND application.guardian_user_id<>current_guardian_map.target_id)
      )
    ) THEN RAISE EXCEPTION 'Ambiguous guardian row is not aligned with its original tuition application'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_guardian_decisions staged
      JOIN legacy_import_entity_map enrollment_map ON enrollment_map.run_id=${sql(input.originalImportRunId)} AND enrollment_map.entity_type='ENROLLMENT' AND enrollment_map.source_key_hash=staged.enrollment_hash AND enrollment_map.target_table='enrollments'
      JOIN enrollments enrollment ON enrollment.id=enrollment_map.target_id
      WHERE staged.mode IN ('MISSING','SELF_GUARDIAN') AND (
        EXISTS (SELECT 1 FROM tuition_applications application WHERE application.enrollment_id=enrollment.id)
        OR EXISTS (SELECT 1 FROM tuition_ledger_entries ledger WHERE ledger.student_id=enrollment.student_id)
      )
    ) THEN RAISE EXCEPTION 'Missing-guardian row already has tuition or ledger data'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_guardian_decisions staged
      JOIN legacy_import_entity_map enrollment_map ON enrollment_map.run_id=${sql(input.originalImportRunId)} AND enrollment_map.entity_type='ENROLLMENT' AND enrollment_map.source_key_hash=staged.enrollment_hash AND enrollment_map.target_table='enrollments'
      JOIN enrollments enrollment ON enrollment.id=enrollment_map.target_id
      JOIN tuition_applications application ON application.enrollment_id=enrollment.id
      JOIN legacy_import_entity_map current_guardian_map ON current_guardian_map.run_id=${sql(input.originalImportRunId)} AND current_guardian_map.entity_type='GUARDIAN' AND current_guardian_map.source_key_hash=staged.current_guardian_hash AND current_guardian_map.target_table='users'
      WHERE staged.mode='AMBIGUOUS' AND staged.current_guardian_hash<>staged.final_guardian_hash
        AND (
          application.guardian_user_id<>current_guardian_map.target_id OR application.status<>'APPROVED'
          OR (SELECT count(*) FROM tuition_applications application_count WHERE application_count.enrollment_id=enrollment.id)<>1
          OR (SELECT count(*) FROM tuition_ledger_entries ledger_count WHERE ledger_count.application_id=application.id)<>5
          OR (SELECT count(*) FROM tuition_ledger_entries ledger_count JOIN billing_charges charge_count ON charge_count.source_type='TUITION_LEDGER' AND charge_count.source_id=ledger_count.id WHERE ledger_count.application_id=application.id)<>5
          OR EXISTS (
            SELECT 1 FROM tuition_ledger_entries ledger
            JOIN billing_charges charge ON charge.source_type='TUITION_LEDGER' AND charge.source_id=ledger.id
            WHERE ledger.application_id=application.id AND (
              ledger.status<>'PENDING' OR ledger.paid_amount<>0 OR ledger.late_fee_amount<>0
              OR charge.status<>'OPEN' OR charge.paid_amount<>0 OR charge.late_fee_applied_at IS NOT NULL
              OR EXISTS (SELECT 1 FROM payment_allocations allocation WHERE allocation.charge_id=charge.id)
              OR EXISTS (SELECT 1 FROM fiscal_invoices invoice WHERE invoice.charge_id=charge.id)
              OR EXISTS (SELECT 1 FROM billing_charge_adjustments adjustment WHERE adjustment.charge_id=charge.id)
              OR EXISTS (SELECT 1 FROM automatic_debit_instructions instruction WHERE instruction.charge_id=charge.id)
            )
          )
        )
    ) THEN RAISE EXCEPTION 'Guardian reassignment is blocked by changed or financial downstream state'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_course_decisions staged
      JOIN legacy_import_entity_map course_map ON course_map.run_id=${sql(input.originalImportRunId)} AND course_map.entity_type='COURSE' AND course_map.source_key_hash=staged.course_hash AND course_map.target_table='courses'
      JOIN course_sessions session ON session.course_id=course_map.target_id
    ) THEN RAISE EXCEPTION 'Confirmed course already has sessions'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_course_sessions a
      JOIN stage_course_sessions b ON a.course_hash<b.course_hash AND a.session_date=b.session_date AND a.start_time<b.end_time AND b.start_time<a.end_time
      JOIN legacy_import_entity_map am ON am.run_id=${sql(input.originalImportRunId)} AND am.entity_type='COURSE' AND am.source_key_hash=a.course_hash AND am.target_table='courses'
      JOIN legacy_import_entity_map bm ON bm.run_id=${sql(input.originalImportRunId)} AND bm.entity_type='COURSE' AND bm.source_key_hash=b.course_hash AND bm.target_table='courses'
      JOIN courses ac ON ac.id=am.target_id JOIN courses bc ON bc.id=bm.target_id
      WHERE (ac.teacher_id IS NOT NULL AND ac.teacher_id=bc.teacher_id)
         OR (upper(trim(a.classroom_name))=upper(trim(b.classroom_name)) AND upper(a.classroom_name) !~ '(VIRTUAL|ONLINE|REMOT)')
         OR EXISTS (
           SELECT 1 FROM enrollments ae JOIN enrollments be ON be.student_id=ae.student_id
           WHERE ae.course_id=ac.id AND be.course_id=bc.id AND ae.status='ACTIVE' AND be.status='ACTIVE'
         )
    ) THEN RAISE EXCEPTION 'Confirmed course schedules conflict with each other'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_course_sessions staged
      JOIN legacy_import_entity_map course_map ON course_map.run_id=${sql(input.originalImportRunId)} AND course_map.entity_type='COURSE' AND course_map.source_key_hash=staged.course_hash AND course_map.target_table='courses'
      JOIN courses course ON course.id=course_map.target_id
      JOIN course_sessions existing ON existing.session_date=staged.session_date AND existing.start_time<staged.end_time AND staged.start_time<existing.end_time
      JOIN courses existing_course ON existing_course.id=existing.course_id
      WHERE (course.teacher_id IS NOT NULL AND course.teacher_id=existing_course.teacher_id)
         OR (upper(trim(staged.classroom_name))=upper(trim(existing.classroom_name)) AND upper(staged.classroom_name) !~ '(VIRTUAL|ONLINE|REMOT)')
         OR EXISTS (
           SELECT 1 FROM enrollments a JOIN enrollments b ON b.student_id=a.student_id
           WHERE a.course_id=course.id AND b.course_id=existing_course.id AND a.status='ACTIVE' AND b.status='ACTIVE'
         )
    ) THEN RAISE EXCEPTION 'Confirmed course schedules conflict with existing sessions'; END IF;
  END $$`;
}

function applyGuardianDecisionsSql(input) {
  const actorHash = sourceHash("system-actor", "legacy-import");
  const academicYearHash = sourceHash("academic-year", "2026");
  const generalPlanHash = sourceHash("fee-plan", "PLAN-GENERAL-2026");
  const kidsPlanHash = sourceHash("fee-plan", "PLAN-KIDS-2026");
  return `DO $$ DECLARE
    r record; period record; charge_row record;
    decision_id_value bigint; student_id_value bigint; guardian_id_value bigint; current_guardian_id_value bigint;
    actor_id_value bigint; enrollment_id_value bigint; course_id_value bigint; level_id_value bigint;
    year_id_value bigint; plan_id_value bigint; application_id_value bigint; ledger_id_value bigint;
    account_id_value bigint; profile_id_value bigint; charge_id_value bigint; issue_id_value bigint;
    account_created boolean; profile_created boolean; amount_value numeric(12,2);
  BEGIN
    SELECT target_id INTO actor_id_value FROM legacy_import_entity_map WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='SYSTEM_ACTOR' AND source_key_hash='${actorHash}' AND target_table='users';
    SELECT target_id INTO year_id_value FROM legacy_import_entity_map WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='ACADEMIC_YEAR' AND source_key_hash='${academicYearHash}' AND target_table='tuition_academic_years';
    FOR r IN SELECT * FROM stage_guardian_decisions ORDER BY source_row LOOP
      SELECT target_id INTO student_id_value FROM legacy_import_entity_map WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='STUDENT' AND source_key_hash=r.student_hash AND target_table='students';
      guardian_id_value:=NULL;
      IF r.mode<>'SELF_GUARDIAN' THEN
        SELECT target_id INTO guardian_id_value FROM legacy_import_entity_map WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='GUARDIAN' AND source_key_hash=r.final_guardian_hash AND target_table='users';
      END IF;
      SELECT target_id INTO enrollment_id_value FROM legacy_import_entity_map WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='ENROLLMENT' AND source_key_hash=r.enrollment_hash AND target_table='enrollments';
      SELECT course_id INTO course_id_value FROM enrollments WHERE id=enrollment_id_value;
      SELECT id INTO level_id_value FROM tuition_levels WHERE code=(SELECT current_level FROM students WHERE id=student_id_value);
      SELECT target_id INTO plan_id_value FROM legacy_import_entity_map WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='FEE_PLAN' AND source_key_hash=CASE WHEN (SELECT current_level FROM students WHERE id=student_id_value)='KIDS' THEN '${kidsPlanHash}' ELSE '${generalPlanHash}' END AND target_table='tuition_fee_plans';
      SELECT monthly_fee INTO amount_value FROM tuition_fee_plans WHERE id=plan_id_value;
      IF actor_id_value IS NULL OR year_id_value IS NULL OR level_id_value IS NULL OR plan_id_value IS NULL OR amount_value IS NULL THEN
        RAISE EXCEPTION 'Cannot resolve tuition catalog for enrollment %',r.enrollment_hash;
      END IF;
      IF r.mode='AMBIGUOUS' THEN
        SELECT target_id INTO current_guardian_id_value FROM legacy_import_entity_map WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='GUARDIAN' AND source_key_hash=r.current_guardian_hash AND target_table='users';
      ELSE
        current_guardian_id_value:=NULL;
      END IF;
      INSERT INTO legacy_reconciliation_decisions(run_id,sheet_name,decision_type,source_key_hash,target_source_key_hash,outcome)
      VALUES(${sql(input.runId)},r.sheet_name,CASE WHEN r.mode='SELF_GUARDIAN' THEN 'CREATE_SELF_GUARDIAN_AND_BILLING' WHEN r.mode='MISSING' THEN 'LINK_GUARDIAN_AND_CREATE_BILLING' ELSE 'CONFIRM_OR_REASSIGN_GUARDIAN' END,r.enrollment_hash,r.final_guardian_hash,CASE WHEN r.mode='AMBIGUOUS' AND r.current_guardian_hash=r.final_guardian_hash THEN 'CONFIRMED_NO_CHANGE' ELSE 'APPLIED' END)
      RETURNING id INTO decision_id_value;

      IF r.mode='SELF_GUARDIAN' THEN
        INSERT INTO users(username,email,password,first_name,last_name,phone_number,address,date_of_birth,document_number,role,status,active,created_at,updated_at)
        SELECT 'adulto-legacy-'||r.legacy_user,'adulto-'||r.legacy_user||'@invalid.sigep.local','${DISABLED_PASSWORD_HASH}',first_name,last_name,phone_number,address,date_of_birth,document_number,'GUARDIAN','PENDING_APPROVAL',false,now(),now()
        FROM students WHERE id=student_id_value
        RETURNING id INTO guardian_id_value;
        INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,new_state)
        VALUES(${sql(input.runId)},decision_id_value,'SELF_GUARDIAN_USER','users',guardian_id_value,'CREATED',jsonb_build_object('student_id',student_id_value,'login_enabled',false));
      END IF;

      IF r.mode IN ('MISSING','SELF_GUARDIAN') OR r.current_guardian_hash<>r.final_guardian_hash THEN
        INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,previous_state,new_state)
        VALUES(${sql(input.runId)},decision_id_value,'STUDENT_GUARDIAN','students',student_id_value,'UPDATED',jsonb_build_object('guardian_id',current_guardian_id_value),jsonb_build_object('guardian_id',guardian_id_value));
        UPDATE students SET guardian_id=guardian_id_value,updated_at=now() WHERE id=student_id_value;
        INSERT INTO student_guardian_link_events(student_id,previous_guardian_user_id,guardian_user_id,action,origin,actor_user_id,reason,created_at)
        VALUES(student_id_value,current_guardian_id_value,guardian_id_value,CASE WHEN current_guardian_id_value IS NULL THEN 'LINKED' ELSE 'REASSIGNED' END,'ADMIN',actor_id_value,'Legacy reconciliation '||${sql(input.runId)},now());
      END IF;

      IF r.mode IN ('MISSING','SELF_GUARDIAN') OR r.current_guardian_hash<>r.final_guardian_hash THEN
        SELECT id INTO account_id_value FROM billing_accounts WHERE guardian_user_id=guardian_id_value;
        account_created:=account_id_value IS NULL;
        IF account_created THEN
          INSERT INTO billing_accounts(guardian_user_id,display_name,status,created_at,updated_at,version)
          SELECT id,trim(first_name||' '||last_name),'ACTIVE',now(),now(),0 FROM users WHERE id=guardian_id_value
          RETURNING id INTO account_id_value;
          INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,new_state)
          VALUES(${sql(input.runId)},decision_id_value,'BILLING_ACCOUNT','billing_accounts',account_id_value,'CREATED',jsonb_build_object('guardian_id',guardian_id_value));
        END IF;
        SELECT id INTO profile_id_value FROM billing_profiles WHERE account_id=account_id_value;
        profile_created:=profile_id_value IS NULL;
        IF profile_created THEN
          INSERT INTO billing_profiles(account_id,receiver_name,receiver_address,receiver_document_number,default_fiscal_concept,fiscal_currency,rg_5866_applicable,status,created_at,updated_at,version)
          SELECT account_id_value,trim(first_name||' '||last_name),address,document_number,2,'PES',false,'INCOMPLETE',now(),now(),0 FROM users WHERE id=guardian_id_value
          RETURNING id INTO profile_id_value;
          INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,new_state)
          VALUES(${sql(input.runId)},decision_id_value,'BILLING_PROFILE','billing_profiles',profile_id_value,'CREATED',jsonb_build_object('account_id',account_id_value));
        END IF;
      END IF;

      IF r.mode IN ('MISSING','SELF_GUARDIAN') THEN
        INSERT INTO tuition_applications(admin_notes,application_type,approved_at,approved_by,created_at,enrollment_id,guardian_user_id,status,submitted_at,updated_at,academic_year_id,fee_plan_id,requires_admin_override,assigned_course_id,assigned_level_id,actor_user_id,origin,student_id,student_resolution,idempotency_key,version)
        VALUES('Importacion de continuidad ciclo 2026 conciliada', 'REGULAR_PROMOTION',now(),actor_id_value,now(),enrollment_id_value,guardian_id_value,'APPROVED',TIMESTAMP '2026-04-01 00:00:00',now(),year_id_value,plan_id_value,false,course_id_value,level_id_value,actor_id_value,'ADMIN',student_id_value,'EXISTING','legacy-2026-'||r.enrollment_hash,0)
        RETURNING id INTO application_id_value;
        INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,new_state)
        VALUES(${sql(input.runId)},decision_id_value,'TUITION_APPLICATION','tuition_applications',application_id_value,'CREATED',jsonb_build_object('source_key_hash',r.application_hash));
        FOR period IN SELECT * FROM stage_guardian_billing_periods WHERE enrollment_hash=r.enrollment_hash ORDER BY due_date LOOP
          INSERT INTO tuition_ledger_entries(concept,created_at,discount_amount,due_date,gross_amount,net_amount,status,student_id,updated_at,application_id,paid_amount,late_fee_amount,billing_reference)
          VALUES('MONTHLY_FEE',now(),0,period.due_date,amount_value,amount_value,'PENDING',student_id_value,now(),application_id_value,0,0,'LEGACY-2026-'||substr(period.ledger_hash,1,40))
          RETURNING id INTO ledger_id_value;
          INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,new_state)
          VALUES(${sql(input.runId)},decision_id_value,'TUITION_LEDGER','tuition_ledger_entries',ledger_id_value,'CREATED',jsonb_build_object('source_key_hash',period.ledger_hash));
          INSERT INTO billing_charges(account_id,student_id,student_name,source_type,source_id,concept,description,base_amount,amount,paid_amount,currency,due_date,service_from,service_to,late_fee_percentage,late_fee_eligible,automatic_debit_eligible,collection_channel,fiscal_disposition,status,created_at,updated_at,version)
          SELECT account_id_value,student_id_value,trim(first_name||' '||last_name),'TUITION_LEDGER',ledger_id_value,'MONTHLY_FEE','Cuota '||extract(month from period.due_date)::int||'/2026 - '||trim(first_name||' '||last_name),amount_value,amount_value,0,'ARS',period.due_date,date_trunc('month',period.due_date)::date,(date_trunc('month',period.due_date)+interval '1 month - 1 day')::date,0,true,false,'REGULAR','PENDING','OPEN',now(),now(),0 FROM students WHERE id=student_id_value
          RETURNING id INTO charge_id_value;
          INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,new_state)
          VALUES(${sql(input.runId)},decision_id_value,'BILLING_CHARGE','billing_charges',charge_id_value,'CREATED',jsonb_build_object('source_key_hash',period.ledger_hash));
        END LOOP;
      ELSIF r.current_guardian_hash<>r.final_guardian_hash THEN
        SELECT id INTO application_id_value FROM tuition_applications WHERE enrollment_id=enrollment_id_value;
        INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,previous_state,new_state)
        VALUES(${sql(input.runId)},decision_id_value,'TUITION_APPLICATION_GUARDIAN','tuition_applications',application_id_value,'UPDATED',jsonb_build_object('guardian_id',current_guardian_id_value),jsonb_build_object('guardian_id',guardian_id_value));
        UPDATE tuition_applications SET guardian_user_id=guardian_id_value,updated_at=now(),version=version+1 WHERE id=application_id_value;
        FOR charge_row IN
          SELECT charge.id,charge.account_id FROM tuition_ledger_entries ledger
          JOIN billing_charges charge ON charge.source_type='TUITION_LEDGER' AND charge.source_id=ledger.id
          WHERE ledger.application_id=application_id_value ORDER BY charge.id
        LOOP
          INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,previous_state,new_state)
          VALUES(${sql(input.runId)},decision_id_value,'BILLING_CHARGE_ACCOUNT','billing_charges',charge_row.id,'UPDATED',jsonb_build_object('account_id',charge_row.account_id),jsonb_build_object('account_id',account_id_value));
          UPDATE billing_charges SET account_id=account_id_value,updated_at=now(),version=version+1 WHERE id=charge_row.id;
        END LOOP;
      END IF;

      SELECT id INTO issue_id_value FROM legacy_import_issues WHERE run_id=${sql(input.originalImportRunId)} AND source_key_hash=r.enrollment_hash AND issue_code=CASE WHEN r.mode IN ('MISSING','SELF_GUARDIAN') THEN 'MISSING_GUARDIAN_MATCH' ELSE 'MULTIPLE_GUARDIAN_MATCHES' END AND resolved_at IS NULL;
      INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,previous_state,new_state)
      VALUES(${sql(input.runId)},decision_id_value,'LEGACY_IMPORT_ISSUE','legacy_import_issues',issue_id_value,'RESOLVED',jsonb_build_object('resolved',false),jsonb_build_object('resolved',true));
      UPDATE legacy_import_issues SET resolved_at=now(),resolution='Resolved by reconciliation run '||${sql(input.runId)} WHERE id=issue_id_value;
    END LOOP;
  END $$`;
}

function applyCourseDecisionsSql(input) {
  return `DO $$ DECLARE r record; session_row record; decision_id_value bigint; course_id_value bigint; session_id_value bigint; issue_id_value bigint; BEGIN
    FOR r IN SELECT * FROM stage_course_decisions ORDER BY course_name LOOP
      SELECT target_id INTO course_id_value FROM legacy_import_entity_map WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='COURSE' AND source_key_hash=r.course_hash AND target_table='courses';
      INSERT INTO legacy_reconciliation_decisions(run_id,sheet_name,decision_type,source_key_hash,target_source_key_hash,outcome)
      VALUES(${sql(input.runId)},'Cursos','UPDATE_COURSE_AND_CREATE_SESSIONS',r.course_hash,NULL,'APPLIED') RETURNING id INTO decision_id_value;
      INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,previous_state,new_state)
      SELECT ${sql(input.runId)},decision_id_value,'COURSE','courses',id,'UPDATED',jsonb_build_object('duration',duration,'max_students',max_students),jsonb_build_object('duration',r.duration_hours,'max_students',r.max_students) FROM courses WHERE id=course_id_value;
      UPDATE courses SET duration=r.duration_hours,max_students=r.max_students,updated_at=now() WHERE id=course_id_value;
      FOR session_row IN SELECT * FROM stage_course_sessions WHERE course_hash=r.course_hash ORDER BY session_date LOOP
        INSERT INTO course_sessions(course_id,session_date,start_time,end_time,classroom_id,classroom_name,status,topic,notes,is_recurring,recurrence_rule,parent_session_id,created_at,updated_at)
        VALUES(course_id_value,session_row.session_date,session_row.start_time,session_row.end_time,NULL,session_row.classroom_name,'SCHEDULED',NULL,'Generada desde conciliacion legacy '||${sql(input.runId)},true,NULL,NULL,now(),now())
        RETURNING id INTO session_id_value;
        INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,new_state)
        VALUES(${sql(input.runId)},decision_id_value,'COURSE_SESSION','course_sessions',session_id_value,'CREATED',jsonb_build_object('course_id',course_id_value));
      END LOOP;
    END LOOP;
    IF (SELECT count(DISTINCT decision.source_key_hash) FROM legacy_reconciliation_decisions decision JOIN legacy_reconciliation_runs run ON run.run_id=decision.run_id WHERE run.original_import_run_id=${sql(input.originalImportRunId)} AND run.status IN ('RUNNING','COMPLETED') AND decision.sheet_name='Cursos')=40 THEN
      SELECT id INTO issue_id_value FROM legacy_import_issues WHERE run_id=${sql(input.originalImportRunId)} AND issue_code='MISSING_SCHEDULE' AND resolved_at IS NULL;
      IF issue_id_value IS NOT NULL THEN
        SELECT id INTO decision_id_value FROM legacy_reconciliation_decisions WHERE run_id=${sql(input.runId)} AND sheet_name='Cursos' ORDER BY id LIMIT 1;
        INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,previous_state,new_state)
        VALUES(${sql(input.runId)},decision_id_value,'LEGACY_IMPORT_ISSUE','legacy_import_issues',issue_id_value,'RESOLVED',jsonb_build_object('resolved',false),jsonb_build_object('resolved',true));
        UPDATE legacy_import_issues SET resolved_at=now(),resolution='All courses reconciled by run '||${sql(input.runId)} WHERE id=issue_id_value;
      END IF;
    END IF;
  END $$`;
}

function applyStudentIdentifierDecisionsSql(input) {
  return `DO $$ DECLARE r record; decision_id_value bigint; student_id_value bigint; previous_number text; BEGIN
    FOR r IN SELECT * FROM stage_student_identifiers ORDER BY source_row LOOP
      SELECT target_id INTO student_id_value FROM legacy_import_entity_map
      WHERE run_id=${sql(input.originalImportRunId)} AND entity_type='STUDENT' AND source_key_hash=r.student_hash AND target_table='students';
      SELECT student_number INTO previous_number FROM students WHERE id=student_id_value;
      INSERT INTO legacy_reconciliation_decisions(run_id,sheet_name,decision_type,source_key_hash,target_source_key_hash,outcome)
      VALUES(${sql(input.runId)},'Identificadores alumnos','CONFIRM_STUDENT_NUMBER',r.student_hash,r.student_number_hash,CASE WHEN previous_number=r.student_number THEN 'CONFIRMED_NO_CHANGE' ELSE 'APPLIED' END)
      RETURNING id INTO decision_id_value;
      IF previous_number IS DISTINCT FROM r.student_number THEN
        INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,previous_state,new_state)
        VALUES(${sql(input.runId)},decision_id_value,'STUDENT_NUMBER','students',student_id_value,'UPDATED',jsonb_build_object('student_number',previous_number),jsonb_build_object('student_number',r.student_number));
        UPDATE students SET student_number=r.student_number,updated_at=now() WHERE id=student_id_value;
      END IF;
    END LOOP;
  END $$`;
}

function applyInstitutionalDecisionsSql(input) {
  return `DO $$ DECLARE r record; decision_id_value bigint; policy_id_value bigint; issue_id_value bigint; BEGIN
    FOR r IN SELECT * FROM stage_institutional_decisions ORDER BY source_key_hash LOOP
      INSERT INTO legacy_reconciliation_decisions(run_id,sheet_name,decision_type,source_key_hash,target_source_key_hash,outcome)
      VALUES(${sql(input.runId)},'Decisiones',CASE WHEN r.action='CREATE_GENERAL_ENROLLMENT_FEE_POLICY' THEN r.action ELSE 'INSTITUTIONAL_DECISION_RECORDED' END,r.source_key_hash,r.decision_hash,CASE WHEN r.action='CREATE_GENERAL_ENROLLMENT_FEE_POLICY' THEN 'APPLIED' ELSE 'RECORDED_ONLY' END)
      RETURNING id INTO decision_id_value;
      IF r.action='CREATE_GENERAL_ENROLLMENT_FEE_POLICY' THEN
        INSERT INTO tuition_enrollment_fee_policies(amount,automatic_debit_eligible,created_at,currency,default_policy,name,payment_due_days,status,updated_at,valid_from,valid_to)
        VALUES(r.amount,false,now(),'ARS',true,'Matricula general 2026',3,'ACTIVE',now(),DATE '2026-04-01',DATE '2026-12-31')
        RETURNING id INTO policy_id_value;
        INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,new_state)
        VALUES(${sql(input.runId)},decision_id_value,'ENROLLMENT_FEE_POLICY','tuition_enrollment_fee_policies',policy_id_value,'CREATED',jsonb_build_object('amount',r.amount,'currency','ARS','default_policy',true));
        SELECT id INTO issue_id_value FROM legacy_import_issues
        WHERE run_id=${sql(input.originalImportRunId)} AND issue_code='GENERAL_ENROLLMENT_FEE_UNKNOWN' AND resolved_at IS NULL;
        IF issue_id_value IS NOT NULL THEN
          INSERT INTO legacy_reconciliation_changes(run_id,decision_id,entity_type,target_table,target_id,change_type,previous_state,new_state)
          VALUES(${sql(input.runId)},decision_id_value,'LEGACY_IMPORT_ISSUE','legacy_import_issues',issue_id_value,'RESOLVED',jsonb_build_object('resolved',false),jsonb_build_object('resolved',true));
          UPDATE legacy_import_issues SET resolved_at=now(),resolution='General enrollment fee confirmed at ARS 90000 by reconciliation run '||${sql(input.runId)} WHERE id=issue_id_value;
        END IF;
      END IF;
    END LOOP;
  END $$`;
}

function postflightSql(input) {
  const expectedDecisions = input.summary.confirmedTotal;
  return `DO $$ BEGIN
    IF (SELECT count(*) FROM legacy_reconciliation_decisions WHERE run_id=${sql(input.runId)}) <> ${expectedDecisions} THEN
      RAISE EXCEPTION 'Reconciliation decision count mismatch';
    END IF;
    IF EXISTS (
      SELECT 1 FROM stage_guardian_decisions staged
      JOIN legacy_import_entity_map student_map ON student_map.run_id=${sql(input.originalImportRunId)} AND student_map.entity_type='STUDENT' AND student_map.source_key_hash=staged.student_hash AND student_map.target_table='students'
      LEFT JOIN legacy_import_entity_map guardian_map ON guardian_map.run_id=${sql(input.originalImportRunId)} AND guardian_map.entity_type='GUARDIAN' AND guardian_map.source_key_hash=staged.final_guardian_hash AND guardian_map.target_table='users'
      JOIN students student ON student.id=student_map.target_id
      LEFT JOIN users self_guardian ON self_guardian.id=student.guardian_id AND self_guardian.role='GUARDIAN' AND self_guardian.username='adulto-legacy-'||staged.legacy_user AND self_guardian.active=false
      WHERE (staged.mode='SELF_GUARDIAN' AND self_guardian.id IS NULL)
         OR (staged.mode<>'SELF_GUARDIAN' AND student.guardian_id<>guardian_map.target_id)
    ) THEN RAISE EXCEPTION 'Student guardian postflight mismatch'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_guardian_decisions staged
      JOIN legacy_import_entity_map enrollment_map ON enrollment_map.run_id=${sql(input.originalImportRunId)} AND enrollment_map.entity_type='ENROLLMENT' AND enrollment_map.source_key_hash=staged.enrollment_hash AND enrollment_map.target_table='enrollments'
      JOIN enrollments enrollment ON enrollment.id=enrollment_map.target_id
      JOIN students student ON student.id=enrollment.student_id
      LEFT JOIN legacy_import_entity_map guardian_map ON guardian_map.run_id=${sql(input.originalImportRunId)} AND guardian_map.entity_type='GUARDIAN' AND guardian_map.source_key_hash=staged.final_guardian_hash AND guardian_map.target_table='users'
      LEFT JOIN tuition_applications application ON application.enrollment_id=enrollment.id
      WHERE application.id IS NULL OR application.guardian_user_id<>CASE WHEN staged.mode='SELF_GUARDIAN' THEN student.guardian_id ELSE guardian_map.target_id END
         OR (staged.mode IN ('MISSING','SELF_GUARDIAN') AND ((SELECT count(*) FROM tuition_ledger_entries ledger WHERE ledger.application_id=application.id)<>5 OR (SELECT count(*) FROM tuition_ledger_entries ledger JOIN billing_charges charge ON charge.source_type='TUITION_LEDGER' AND charge.source_id=ledger.id WHERE ledger.application_id=application.id)<>5))
    ) THEN RAISE EXCEPTION 'Tuition or billing postflight mismatch'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_course_decisions staged
      JOIN legacy_import_entity_map course_map ON course_map.run_id=${sql(input.originalImportRunId)} AND course_map.entity_type='COURSE' AND course_map.source_key_hash=staged.course_hash AND course_map.target_table='courses'
      JOIN courses course ON course.id=course_map.target_id
      WHERE course.duration<>staged.duration_hours OR course.max_students<>staged.max_students
         OR (SELECT count(*) FROM course_sessions session WHERE session.course_id=course.id)<>staged.expected_sessions
    ) THEN RAISE EXCEPTION 'Course reconciliation postflight mismatch'; END IF;
    IF EXISTS (
      SELECT 1 FROM stage_student_identifiers staged
      JOIN legacy_import_entity_map student_map ON student_map.run_id=${sql(input.originalImportRunId)} AND student_map.entity_type='STUDENT' AND student_map.source_key_hash=staged.student_hash AND student_map.target_table='students'
      JOIN students student ON student.id=student_map.target_id
      WHERE student.student_number<>staged.student_number
    ) THEN RAISE EXCEPTION 'Student identifier postflight mismatch'; END IF;
    IF EXISTS (SELECT 1 FROM stage_institutional_decisions WHERE action='CREATE_GENERAL_ENROLLMENT_FEE_POLICY')
       AND NOT EXISTS (SELECT 1 FROM tuition_enrollment_fee_policies WHERE name='Matricula general 2026' AND amount=90000 AND currency='ARS' AND default_policy=true AND status='ACTIVE') THEN
      RAISE EXCEPTION 'General enrollment fee policy postflight mismatch';
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

function requiredArg(name) {
  return path.resolve(requiredValue(name));
}

function requiredValue(name) {
  const value = args.get(name);
  if (!value) throw new Error(`Missing --${name}`);
  return value;
}

function defaultRunId() {
  const timestamp = new Date().toISOString().replace(/[-:.]/g, "");
  return `LEGACY-RECON-2026-${timestamp}-${crypto.randomBytes(4).toString("hex")}`;
}

function assertIdentifier(value, label) {
  if (!/^[A-Za-z0-9._:-]{1,64}$/.test(value)) throw new Error(`Invalid ${label}`);
}

function assertSha256(value, label) {
  if (!/^[a-f0-9]{64}$/.test(value)) throw new Error(`Invalid ${label}`);
}

function reconciliationState(value, rowNumber) {
  const state = normalize(value);
  if (!["PENDIENTE", "CONFIRMADO", "NO APLICA", "DESCARTADO"].includes(state)) {
    throw new Error(`Workbook row ${rowNumber}: unsupported Estado ${text(value)}`);
  }
  return state;
}

function parseDays(value, rowNumber) {
  const normalized = normalize(value);
  if (!normalized) throw new Error(`Cursos row ${rowNumber}: days are required`);
  const aliases = new Map([
    ["DOMINGO", 0], ["DOM", 0], ["SUNDAY", 0],
    ["LUNES", 1], ["LUN", 1], ["MONDAY", 1],
    ["MARTES", 2], ["MAR", 2], ["TUESDAY", 2],
    ["MIERCOLES", 3], ["MIE", 3], ["WEDNESDAY", 3],
    ["JUEVES", 4], ["JUE", 4], ["THURSDAY", 4],
    ["VIERNES", 5], ["VIE", 5], ["FRIDAY", 5],
    ["SABADO", 6], ["SAB", 6], ["SATURDAY", 6],
  ]);
  const ignored = new Set(["Y", "AND"]);
  const days = [];
  for (const token of normalized.split(" ")) {
    if (ignored.has(token)) continue;
    if (!aliases.has(token)) throw new Error(`Cursos row ${rowNumber}: unsupported day token ${token}`);
    days.push(aliases.get(token));
  }
  return [...new Set(days)].sort();
}

function parseTime(value, label, rowNumber) {
  if (value instanceof Date && !Number.isNaN(value.valueOf())) return value.toISOString().slice(11, 16);
  if (typeof value === "number" && value >= 0 && value < 1) {
    const minutes = Math.round(value * 24 * 60);
    return `${String(Math.floor(minutes / 60) % 24).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
  }
  const raw = text(value);
  const match = raw.match(/^(\d{1,2})[:.]([0-5]\d)(?::[0-5]\d)?$/);
  if (!match || Number(match[1]) > 23) throw new Error(`Cursos row ${rowNumber}: invalid ${label}`);
  return `${match[1].padStart(2, "0")}:${match[2]}`;
}

function timeToMinutes(value) {
  const [hours, minutes] = value.split(":").map(Number);
  return hours * 60 + minutes;
}

function isoDate(value) {
  const parsed = new Date(`${value}T00:00:00Z`);
  if (Number.isNaN(parsed.valueOf())) throw new Error(`Invalid ISO date ${value}`);
  return parsed;
}

function addUtcDays(value, days) {
  const copy = new Date(value.valueOf());
  copy.setUTCDate(copy.getUTCDate() + days);
  return copy;
}

function positiveInteger(value, label, rowNumber) {
  const number = Number(text(value).replace(",", "."));
  if (!Number.isInteger(number) || number <= 0) throw new Error(`Workbook row ${rowNumber}: ${label} must be a positive integer`);
  return number;
}

function nonNegativeInteger(value, label, rowNumber) {
  const number = Number(text(value));
  if (!Number.isInteger(number) || number < 0) throw new Error(`Workbook row ${rowNumber}: ${label} must be a non-negative integer`);
  return number;
}

function assertUnique(values, label) {
  if (new Set(values).size !== values.length) throw new Error(`Duplicate ${label} in reconciliation workbook`);
}

function text(value) {
  if (value === null || value === undefined) return "";
  if (value instanceof Date) return value.toISOString().slice(0, 10);
  if (typeof value === "number" && Number.isInteger(value)) return String(value);
  return String(value).trim();
}

function normalize(value) {
  return text(value).normalize("NFKD").replace(/\p{Diacritic}/gu, "").toUpperCase().replace(/[^A-Z0-9]+/g, " ").trim();
}

function digits(value) {
  return text(value).replace(/\D/g, "");
}

function firstMoneyAmount(value) {
  const match = text(value).match(/(?:ARS\s*)?([0-9]+(?:[.,][0-9]{3})*)/i);
  if (!match) return null;
  const normalized = match[1].replace(/[.,]/g, "");
  const amount = Number.parseInt(normalized, 10);
  return Number.isSafeInteger(amount) ? amount : null;
}

function sha256(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function sourceHash(type, value) {
  return sha256(`${type}:${normalize(value)}`);
}

function insertValues(table, columns, rows) {
  return rows.length
    ? `INSERT INTO ${table}(${columns.join(",")}) VALUES\n${rows.map((row) => `  (${row.map(sql).join(",")})`).join(",\n")}`
    : "SELECT 1";
}

function sql(value) {
  if (value === null || value === undefined || value === "") return "NULL";
  if (typeof value === "number") return String(value);
  if (typeof value === "boolean") return value ? "true" : "false";
  return `'${String(value).replaceAll("'", "''")}'`;
}
