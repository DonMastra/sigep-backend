import crypto from "node:crypto";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { resolveLegacyTeacher } from "./teacher-account-mapping.mjs";

const IMPORTER_VERSION = "legacy-2026-v2";
const SQL_BOUNDARY = "-- SIGEP_STATEMENT_BOUNDARY";
const UNKNOWN_TEXT = "SIN INFORMAR - MIGRACION LEGACY 2026";
const UNKNOWN_DATE = "1900-01-01";
const DISABLED_PASSWORD_HASH = "$2a$10$zMRfcjxtD9tGfPJ1wbHI/OvWpQa2gEurEaTMmX9cGGlz4o5pvh96K";

const args = parseArgs(process.argv.slice(2));
const sourceDir = requiredArg("source-dir");
const convertedDir = requiredArg("converted-dir");
const outputPath = requiredArg("output");
const artifactToolRoot = requiredArg("artifact-tool-root");
const expectedDatabase = args.get("expected-database") ?? "sigep_prod";
const targetGitCommit = args.get("git-commit") ?? "UNCOMMITTED";
const runId = args.get("run-id") ?? defaultRunId();

const artifactModule = path.join(artifactToolRoot, "dist", "artifact_tool.mjs");
const { FileBlob, SpreadsheetFile } = await import(pathToFileURL(artifactModule).href);

const sourceFiles = {
  courses: path.join(sourceDir, "1. Cursadas.xlsx"),
  fees: path.join(sourceDir, "2. Aranceles 2026.xlsx"),
  levels: path.join(sourceDir, "3. Estructura de niveles .xlsx"),
  guardiansOriginal: path.join(sourceDir, "4.Listado de responsables.xls"),
  studentsOriginal: path.join(sourceDir, "5.Listado de alumnos.xls"),
  guardians: path.join(convertedDir, "4.Listado de responsables.converted.xlsx"),
  students: path.join(convertedDir, "5.Listado de alumnos.converted.xlsx"),
};

for (const filePath of Object.values(sourceFiles)) await fs.access(filePath);

const [courseRows, feeRows, levelRows, guardianRows, studentRows] = await Promise.all([
  readFirstSheet(sourceFiles.courses),
  readFirstSheet(sourceFiles.fees),
  readFirstSheet(sourceFiles.levels),
  readFirstSheet(sourceFiles.guardians),
  readFirstSheet(sourceFiles.students),
]);

const courseRecords = records(courseRows, 2);
const feeRecords = records(feeRows, 2);
const guardianRecords = records(guardianRows, 1);
const studentRecords = records(studentRows, 1);
assertSourceShape({ courseRecords, feeRecords, guardianRecords, studentRecords, levelRows });

const fileManifest = await buildFileManifest(sourceDir, sourceFiles);
const sourceManifestJson = JSON.stringify({ files: fileManifest });
const sourceManifestSha256 = sha256(sourceManifestJson);

const guardians = prepareGuardians(guardianRecords);
const students = prepareStudents(studentRecords, guardians);
const levels = curriculumLevels();
verifyCurriculum(levelRows, levels);
const { teachers, courses } = prepareTeachersAndCourses(courseRecords, levels);
const enrollments = prepareEnrollments(studentRecords, students, courses);
const relationships = prepareGuardianRelationships(students);
const ledgers = prepareLedgers(enrollments);

const summary = {
  guardianUsers: guardians.length,
  canonicalStudents: students.length,
  teachingStaff: teachers.length,
  tuitionLevels: levels.length,
  courses: courses.length,
  enrollments: enrollments.length,
  activeEnrollments: enrollments.filter((row) => row.status === "ACTIVE").length,
  droppedEnrollments: enrollments.filter((row) => row.status === "DROPPED").length,
  guardianRelationships: relationships.length,
  selectedGuardianRelationships: relationships.filter((row) => row.selected).length,
  billingReadyStudents: new Set(ledgers.map((row) => row.studentHash)).size,
  monthlyLedgerEntries: ledgers.length,
  unmatchedCurrentStudents: enrollments.filter((row) => row.status === "ACTIVE" && !row.guardianHash).length,
  ambiguousCurrentStudents: enrollments.filter((row) => row.status === "ACTIVE" && row.guardianCandidateCount > 1).length,
};

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const migrationSql = await fs.readFile(path.resolve(scriptDir, "../../migrations/V28__create_legacy_import_audit.sql"), "utf8");
const studentIdentifierMigrationSql = await fs.readFile(path.resolve(scriptDir, "../../migrations/V30__add_student_business_identifier.sql"), "utf8");
const sqlText = buildSql({
  runId, expectedDatabase, targetGitCommit, sourceManifestSha256, sourceManifestJson,
  summary, migrationSql, studentIdentifierMigrationSql, guardians, students, teachers, levels, courses, enrollments,
  relationships, ledgers,
});

await fs.mkdir(path.dirname(outputPath), { recursive: true });
await fs.writeFile(outputPath, sqlText, { encoding: "utf8", flag: "wx" });
console.log(JSON.stringify({ status: "prepared", runId, expectedDatabase, sourceManifestSha256, outputPath, summary }, null, 2));

function parseArgs(values) {
  const parsed = new Map();
  for (let index = 0; index < values.length; index += 2) {
    const key = values[index];
    const value = values[index + 1];
    if (!key?.startsWith("--") || value === undefined) throw new Error(`Invalid argument near ${key ?? "<end>"}`);
    parsed.set(key.slice(2), value);
  }
  return parsed;
}

function requiredArg(name) {
  const value = args.get(name);
  if (!value) throw new Error(`Missing --${name}`);
  return path.resolve(value);
}

function defaultRunId() {
  const timestamp = new Date().toISOString().replace(/[-:.]/g, "");
  return `LEGACY-2026-${timestamp}-${crypto.randomBytes(4).toString("hex")}`;
}

async function readFirstSheet(filePath) {
  const blob = await FileBlob.load(filePath);
  const workbook = await SpreadsheetFile.importXlsx(blob);
  const sheet = workbook.worksheets.items[0];
  if (!sheet) throw new Error(`Workbook has no sheets: ${path.basename(filePath)}`);
  return sheet.getUsedRange()?.values ?? [];
}

function records(rows, headerRow) {
  const headers = (rows[headerRow - 1] ?? []).map(text);
  return rows.slice(headerRow).flatMap((row, index) => {
    const record = Object.fromEntries(headers.flatMap((header, column) => header ? [[header, row[column]]] : []));
    record._sourceRow = headerRow + index + 1;
    return Object.values(record).some((value) => text(value)) ? [record] : [];
  });
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

function normalizedEmail(value) { return text(value).toLowerCase().replace(/\s+/g, ""); }
function digits(value) { return text(value).replace(/\D/g, ""); }
function sha256(value) { return crypto.createHash("sha256").update(value).digest("hex"); }
function sourceHash(type, value) { return sha256(`${type}:${normalize(value)}`); }
function validEmail(value) { return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(normalizedEmail(value)); }
function cycleYear(value) { const match = text(value).match(/20\d{2}/); return match ? Number(match[0]) : null; }

function parseDate(value) {
  if (value instanceof Date && !Number.isNaN(value.valueOf())) return value.toISOString().slice(0, 10);
  const raw = digits(value);
  if (raw.length !== 8) return null;
  const candidates = [
    { day: raw.slice(0, 2), month: raw.slice(2, 4), year: raw.slice(4, 8) },
    { day: raw.slice(6, 8), month: raw.slice(4, 6), year: raw.slice(0, 4) },
  ];
  for (const candidate of candidates) {
    const iso = `${candidate.year}-${candidate.month}-${candidate.day}`;
    const parsed = new Date(`${iso}T00:00:00Z`);
    if (!Number.isNaN(parsed.valueOf()) && parsed.toISOString().slice(0, 10) === iso) return iso;
  }
  return null;
}

function assertSourceShape({ courseRecords, feeRecords, guardianRecords, studentRecords, levelRows }) {
  for (const [actual, expected, label] of [[courseRecords.length, 42, "course rows"], [feeRecords.length, 2, "fee rows"], [guardianRecords.length, 591, "guardian rows"], [studentRecords.length, 625, "student rows"]]) {
    if (actual !== expected) throw new Error(`Unexpected ${label}: ${actual}; expected ${expected}`);
  }
  if (levelRows.length < 32) throw new Error("Curriculum workbook is incomplete");
  const currentRows = studentRecords.filter((row) => cycleYear(row["Ciclo Lectivo"]) === 2026);
  if (currentRows.length !== 320) throw new Error(`Unexpected 2026 student rows: ${currentRows.length}`);
}

async function buildFileManifest(directory, requiredFiles) {
  const names = (await fs.readdir(directory)).filter((name) => /\.(xlsx|xls|jpg)$/i.test(name)).sort();
  const allPaths = new Set([...names.map((name) => path.join(directory, name)), ...Object.values(requiredFiles)]);
  const manifest = [];
  for (const filePath of [...allPaths].sort()) {
    const bytes = await fs.readFile(filePath);
    manifest.push({ fileName: path.basename(filePath), bytes: bytes.length, sha256: sha256(bytes) });
  }
  return manifest;
}

function prepareGuardians(rows) {
  const seenEmails = new Set();
  return rows.map((row) => {
    const document = digits(row.Documento);
    if (!document) throw new Error(`Guardian row ${row._sourceRow} has no document`);
    const hash = sourceHash("guardian", document);
    const candidateEmail = normalizedEmail(row.Mail);
    const usableEmail = validEmail(candidateEmail) && !seenEmails.has(candidateEmail);
    const email = usableEmail ? candidateEmail : `responsable-${hash.slice(0, 16)}@invalid.sigep.local`;
    seenEmails.add(email);
    const phone = text(row.Celular) || text(row["Teléfono"]);
    const addressParts = [text(row["Domicilio (Calle - Nro. - Piso)"]), text(row.Localidad), text(row["Domicilio Provincia"]), text(row["Código postal"])].filter(Boolean);
    return {
      sourceRow: row._sourceRow, hash, document, firstName: text(row.Nombre), lastName: text(row.Apellido), email,
      phone: phone || null, address: addressParts.join(", ") || null, dateOfBirth: parseDate(row["Fecha Nacimiento (DDMMAAAA)"]),
      childText: text(row["Hijo/s"]),
      contactScore: (usableEmail ? 8 : 0) + (text(row.Celular) ? 4 : 0) + (text(row["Teléfono"]) ? 2 : 0) + (addressParts.length ? 1 : 0),
    };
  });
}

function prepareStudents(rows, guardians) {
  const grouped = new Map();
  for (const row of rows) {
    const legacyUser = digits(row.Usuario);
    if (!legacyUser) throw new Error(`Student row ${row._sourceRow} has no legacy user id`);
    if (!grouped.has(legacyUser)) grouped.set(legacyUser, []);
    grouped.get(legacyUser).push(row);
  }
  const result = [];
  for (const [legacyUser, history] of grouped) {
    const latest = [...history].sort((a, b) => (cycleYear(b["Ciclo Lectivo"]) ?? 0) - (cycleYear(a["Ciclo Lectivo"]) ?? 0) || b._sourceRow - a._sourceRow)[0];
    const names = new Set(history.map((row) => normalize(`${text(row.Apellido)} ${text(row.Nombre)}`)));
    if (names.size !== 1) throw new Error(`Student identity conflict at source row ${latest._sourceRow}`);
    const hash = sourceHash("student", legacyUser);
    const currentRows = history.filter((row) => cycleYear(row["Ciclo Lectivo"]) === 2026);
    const currentStudentNumbers = new Set(currentRows.map((row) => digits(row["Matrícula"])).filter(Boolean));
    if (currentStudentNumbers.size > 1) {
      throw new Error(`Student ${legacyUser} has more than one 2026 student number`);
    }
    const current = currentRows.find((row) => normalize(row["Situación académica"]) !== "BAJA") ?? currentRows[0] ?? null;
    const studentNumber = [...currentStudentNumbers][0] ?? digits(latest["Matrícula"]);
    if (!studentNumber) throw new Error(`Student row ${latest._sourceRow} has no student number`);
    const candidateGuardians = guardians.filter((guardian) => {
      const childText = ` ${normalize(guardian.childText)} `;
      return [normalize(`${text(latest.Apellido)} ${text(latest.Nombre)}`), normalize(`${text(latest.Nombre)} ${text(latest.Apellido)}`)]
        .some((name) => name.length >= 5 && childText.includes(` ${name} `));
    }).sort((a, b) => b.contactScore - a.contactScore || a.sourceRow - b.sourceRow);
    const primaryGuardian = candidateGuardians[0] ?? null;
    const suppliedEmail = normalizedEmail(latest.Mail);
    const latestYear = cycleYear(latest["Ciclo Lectivo"]);
    result.push({
      sourceRow: latest._sourceRow, hash, studentNumber, firstName: text(latest.Nombre), lastName: text(latest.Apellido),
      email: validEmail(suppliedEmail) ? suppliedEmail : `alumno-${hash.slice(0, 16)}@invalid.sigep.local`,
      phone: text(latest["Teléfono"]) || primaryGuardian?.phone || UNKNOWN_TEXT,
      emergencyContact: primaryGuardian ? `${primaryGuardian.firstName} ${primaryGuardian.lastName}`.trim() : UNKNOWN_TEXT,
      enrollmentDate: `${latestYear ?? 2026}-04-01`, currentLevel: mapStudentCurrentLevel(text((current ?? latest).Curso), Boolean(current)),
      active: currentRows.some((row) => ["ALUMNO REGULAR", "INSCRIPTO", "CONTINUA"].includes(normalize(row["Situación académica"]))),
      primaryGuardianHash: primaryGuardian?.hash ?? null,
      guardianCandidateHashes: candidateGuardians.map((guardian) => guardian.hash),
      guardianCandidateCount: candidateGuardians.length, currentSourceRows: currentRows.map((row) => row._sourceRow),
    });
  }
  return result.sort((a, b) => a.sourceRow - b.sourceRow);
}

function prepareGuardianRelationships(students) {
  return students.flatMap((student) => student.guardianCandidateHashes.map((guardianHash) => ({
    studentHash: student.hash, guardianHash, selected: guardianHash === student.primaryGuardianHash,
    reason: guardianHash === student.primaryGuardianHash
      ? (student.guardianCandidateCount === 1 ? "UNIQUE_EXACT_NAME_MATCH" : "BEST_CONTACT_COMPLETENESS_THEN_SOURCE_ROW")
      : "ALTERNATE_EXACT_NAME_MATCH",
  })));
}

function prepareTeachersAndCourses(rows, levels) {
  const courseGroups = new Map();
  for (const row of rows) {
    const key = normalize(row.Curso);
    if (!courseGroups.has(key)) courseGroups.set(key, []);
    courseGroups.get(key).push(row);
  }
  const teacherNames = new Set();
  const courses = [];
  for (const [courseKey, group] of courseGroups) {
    const teacherCandidates = [...new Set(group.map((row) => text(row.Docente)).filter(Boolean))];
    if (teacherCandidates.length !== 1) throw new Error(`Course ${courseKey} does not resolve to one teacher`);
    const teacherName = teacherCandidates[0];
    teacherNames.add(teacherName);
    const levelCode = mapCourseToLevelCode(text(group[0].Curso));
    const level = levels.find((row) => row.code === levelCode);
    courses.push({
      sourceRow: Math.min(...group.map((row) => row._sourceRow)), hash: sourceHash("course-2026", courseKey),
      code: `2026-${courseKey.replace(/ /g, "-")}`.slice(0, 50), name: text(group[0].Curso), levelCode,
      courseLevel: level.courseLevel, teacherHash: sourceHash("teacher", teacherName),
      maxStudents: 20, price: levelCode === "KIDS" ? 80000 : 90000,
    });
  }
  const teachers = [...teacherNames].sort().map((fullName, index) => {
    const account = resolveLegacyTeacher(fullName);
    const hash = sourceHash("teacher", fullName);
    return { sourceRow: index + 1, hash, username: account.username, firstName: account.firstName, lastName: account.lastName, email: `docente-${hash.slice(0, 16)}@invalid.sigep.local`, document: `LEGACY-${hash.slice(0, 20)}` };
  });
  return { teachers, courses: courses.sort((a, b) => a.code.localeCompare(b.code)) };
}

function prepareEnrollments(rows, students, courses) {
  const studentByCurrentRow = new Map(students.flatMap((row) => row.currentSourceRows.map((sourceRow) => [sourceRow, row])));
  const courseByName = new Map(courses.map((row) => [normalize(row.name), row]));
  return rows.filter((row) => cycleYear(row["Ciclo Lectivo"]) === 2026).map((row) => {
    const student = studentByCurrentRow.get(row._sourceRow); const course = courseByName.get(normalize(row.Curso));
    if (!student || !course) throw new Error(`Cannot resolve enrollment source row ${row._sourceRow}`);
    const originalStatus = normalize(row["Situación académica"]);
    const hash = sourceHash("enrollment-2026", `${student.studentNumber}|${normalize(row.Curso)}`);
    const planCode = course.levelCode === "KIDS" ? "PLAN-KIDS-2026" : "PLAN-GENERAL-2026";
    return { sourceRow: row._sourceRow, hash, applicationHash: sha256(`application:${hash}`), studentHash: student.hash, courseHash: course.hash, levelCode: course.levelCode, planCode, planHash: sourceHash("fee-plan", planCode), status: originalStatus === "BAJA" ? "DROPPED" : "ACTIVE", originalStatus, guardianHash: student.primaryGuardianHash, guardianCandidateCount: student.guardianCandidateCount };
  });
}

function prepareLedgers(enrollments) {
  return enrollments.filter((row) => row.status === "ACTIVE" && row.guardianHash).flatMap((enrollment) => [8, 9, 10, 11, 12].map((month) => {
    const dueDate = `2026-${String(month).padStart(2, "0")}-10`;
    return { hash: sha256(`ledger:${enrollment.hash}:${dueDate}`), applicationHash: enrollment.applicationHash, enrollmentHash: enrollment.hash, studentHash: enrollment.studentHash, guardianHash: enrollment.guardianHash, dueDate, amount: enrollment.planCode === "PLAN-KIDS-2026" ? 80000 : 90000 };
  }));
}

function mapCourseToLevelCode(value) {
  const course = normalize(value).replace(/^JUNIR\b/, "JUNIOR");
  if (/^KIDS(?: |$)/.test(course)) return "KIDS";
  let match = course.match(/^CHILDREN (STARTER|[1-6])(?: |$)/); if (match) return `CHILDREN_${match[1]}`;
  match = course.match(/^JUNIOR (STARTER|[1-3])(?: |$)/); if (match) return `JUNIOR_${match[1]}`;
  match = course.match(/^TEENS ([1-5])(?: |$)/); if (match) return `TEENS_${match[1]}`;
  match = course.match(/^SENIOR ([1-2])(?: |$)/); if (match) return `SENIOR_${match[1]}`;
  if (/^ADULTS STARTER(?: |$)/.test(course)) return "ADULTS_1";
  if (/^ADULTS ELEMENTARY(?: |$)/.test(course)) return "ADULTS_2";
  match = course.match(/^ADULTS ([1-5])(?: |$)/); if (match) return `ADULTS_${match[1]}`;
  throw new Error(`Unmapped course name: ${value}`);
}

function mapStudentCurrentLevel(value, isCurrent2026) {
  if (!text(value)) return "UNASSIGNED";
  try {
    return mapCourseToLevelCode(value);
  } catch (error) {
    if (isCurrent2026) throw error;
    return `LEGACY_${normalize(value).replace(/ /g, "_").slice(0, 80)}`;
  }
}

function curriculumLevels() {
  const definitions = [
    ["KIDS", "Kids", "CHILDREN", 1, "BEGINNER", "PRE-A1"],
    ["CHILDREN_STARTER", "Children Starter", "CHILDREN", 10, "BEGINNER", "PRE-A1.1"], ["CHILDREN_1", "Children 1", "CHILDREN", 11, "BEGINNER", "PRE-A1.1"], ["CHILDREN_2", "Children 2", "CHILDREN", 12, "BEGINNER", "A1"], ["CHILDREN_3", "Children 3", "CHILDREN", 13, "BEGINNER", "A1"], ["CHILDREN_4", "Children 4", "CHILDREN", 14, "BEGINNER", "A1+"], ["CHILDREN_5", "Children 5", "CHILDREN", 15, "ELEMENTARY", "A2"], ["CHILDREN_6", "Children 6", "CHILDREN", 16, "PRE_INTERMEDIATE", "A2+"],
    ["JUNIOR_STARTER", "Junior Starter", "CHILDREN", 20, "BEGINNER", "A1"], ["JUNIOR_1", "Junior 1", "CHILDREN", 21, "BEGINNER", "A1+"], ["JUNIOR_2", "Junior 2", "CHILDREN", 22, "ELEMENTARY", "A2"], ["JUNIOR_3", "Junior 3", "CHILDREN", 23, "PRE_INTERMEDIATE", "A2+"],
    ["TEENS_1", "Teens 1", "TEENS", 30, "BEGINNER", "A1+"], ["TEENS_2", "Teens 2", "TEENS", 31, "ELEMENTARY", "A2"], ["TEENS_3", "Teens 3", "TEENS", 32, "PRE_INTERMEDIATE", "A2+"], ["TEENS_4", "Teens 4", "TEENS", 33, "INTERMEDIATE", "B1"], ["TEENS_5", "Teens 5", "TEENS", 34, "INTERMEDIATE", "B1+"],
    ["SENIOR_1", "Senior 1", "TEENS", 40, "UPPER_INTERMEDIATE", "B2"], ["SENIOR_2", "Senior 2", "TEENS", 41, "ADVANCED", "C1"],
    ["ADULTS_1", "Adults 1 - Beginner", "ADULTS", 50, "BEGINNER", "A1+"], ["ADULTS_2", "Adults 2 - Elementary", "ADULTS", 51, "ELEMENTARY", "A2"], ["ADULTS_3", "Adults 3 - Pre-intermediate", "ADULTS", 52, "PRE_INTERMEDIATE", "A2+/B1"], ["ADULTS_4", "Adults 4 - Intermediate", "ADULTS", 53, "INTERMEDIATE", "B1+"], ["ADULTS_5", "Adults 5 - Upper intermediate", "ADULTS", 54, "UPPER_INTERMEDIATE", "B2"],
  ];
  return definitions.map(([code, name, segment, levelOrder, courseLevel, internationalLevel], index) => ({ sourceRow: index + 1, hash: sourceHash("tuition-level", code), code, name, segment, levelOrder, courseLevel, internationalLevel }));
}

function verifyCurriculum(rows, levels) {
  const actual = rows.slice(2).flatMap((row, index) => text(row[1]) && normalize(row[2]) ? [{ sourceRow: index + 3, international: normalize(row[2]) }] : []);
  if (actual.length !== levels.length) throw new Error(`Unexpected curriculum rows: ${actual.length}; expected ${levels.length}`);
  const mismatches = actual.filter((row, index) => !row.international.startsWith(normalize(levels[index].internationalLevel)));
  if (mismatches.length) throw new Error("Curriculum international levels differ from the reviewed mapping");
}

function buildSql(input) {
  const statements = [
    "BEGIN",
    "SET LOCAL lock_timeout = '10s'",
    "SET LOCAL statement_timeout = '15min'",
    ...splitSimpleMigration(input.migrationSql),
    ...splitSimpleMigration(input.studentIdentifierMigrationSql),
    preflightSql(input),
  ];
  statements.push(`INSERT INTO legacy_import_runs (run_id,source_system,source_manifest_sha256,source_manifest,importer_version,target_git_commit,status) VALUES (${sql(input.runId)},'Quinttos legacy',${sql(input.sourceManifestSha256)},${sql(input.sourceManifestJson)}::jsonb,'${IMPORTER_VERSION}',${sql(input.targetGitCommit)},'RUNNING')`);
  statements.push(...stageStatements(input), importSystemActorSql(input.runId), importGuardiansSql(input.runId), importRelationshipsSql(input.runId), importIssuesSql(input.runId), importStudentsSql(input.runId), importTeachersSql(input.runId), importLevelsSql(input.runId), importCatalogSql(input.runId), importCoursesSql(input.runId), importEnrollmentsSql(input.runId), importApplicationsSql(input.runId), importBillingSql(input.runId), postflightSql(input));
  statements.push(`UPDATE legacy_import_runs SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,summary=${sql(JSON.stringify(input.summary))}::jsonb WHERE run_id=${sql(input.runId)}`);
  statements.push(
    `INSERT INTO schema_version(version,git_commit,description) VALUES ('V28',${sql(input.targetGitCommit)},'Legacy import audit and 2026 training dataset') ON CONFLICT(version) DO UPDATE SET git_commit=EXCLUDED.git_commit,applied_at=CURRENT_TIMESTAMP,description=EXCLUDED.description`,
    `INSERT INTO schema_version(version,git_commit,description) VALUES ('V30',${sql(input.targetGitCommit)},'Student business identifier') ON CONFLICT(version) DO UPDATE SET git_commit=EXCLUDED.git_commit,applied_at=CURRENT_TIMESTAMP,description=EXCLUDED.description`,
    "COMMIT",
  );
  return statements.map((statement) => `${statement.trim().replace(/;\s*$/, "")};`).join(`\n\n${SQL_BOUNDARY}\n\n`) + "\n";
}

function splitSimpleMigration(value) { return value.replace(/--.*$/gm, "").replace(/^\s*BEGIN\s*;/i, "").replace(/COMMIT\s*;\s*$/i, "").split(";").map((part) => part.trim()).filter(Boolean); }

function preflightSql(input) {
  return `DO $$ DECLARE functional_rows bigint; BEGIN
    IF current_database() <> ${sql(input.expectedDatabase)} THEN RAISE EXCEPTION 'Target database mismatch'; END IF;
    IF NOT EXISTS (SELECT 1 FROM schema_version WHERE version='V27') THEN RAISE EXCEPTION 'Schema V27 baseline is not recorded'; END IF;
    IF EXISTS (SELECT 1 FROM legacy_import_runs WHERE run_id=${sql(input.runId)}) THEN RAISE EXCEPTION 'Import run already exists'; END IF;
    SELECT (SELECT count(*) FROM users)+(SELECT count(*) FROM students)+(SELECT count(*) FROM teaching_staff)+(SELECT count(*) FROM courses)+(SELECT count(*) FROM enrollments)+(SELECT count(*) FROM tuition_applications)+(SELECT count(*) FROM tuition_ledger_entries)+(SELECT count(*) FROM billing_charges) INTO functional_rows;
    IF functional_rows <> 0 THEN RAISE EXCEPTION 'Target functional tables are not empty'; END IF;
  END $$`;
}

function stageStatements(input) {
  return [
    `CREATE TEMP TABLE stage_guardians(source_row int,source_hash varchar(64),first_name text,last_name text,email text,phone text,address text,birth_date date,document_number text) ON COMMIT DROP`,
    insertValues("stage_guardians", ["source_row","source_hash","first_name","last_name","email","phone","address","birth_date","document_number"], input.guardians.map((r) => [r.sourceRow,r.hash,r.firstName,r.lastName,r.email,r.phone,r.address,r.dateOfBirth,r.document])),
    `CREATE TEMP TABLE stage_students(source_row int,source_hash varchar(64),student_number varchar(32),first_name text,last_name text,email text,phone text,emergency_contact text,enrollment_date date,current_level text,active boolean,guardian_hash varchar(64)) ON COMMIT DROP`,
    insertValues("stage_students", ["source_row","source_hash","student_number","first_name","last_name","email","phone","emergency_contact","enrollment_date","current_level","active","guardian_hash"], input.students.map((r) => [r.sourceRow,r.hash,r.studentNumber,r.firstName,r.lastName,r.email,r.phone,r.emergencyContact,r.enrollmentDate,r.currentLevel,r.active,r.primaryGuardianHash])),
    `CREATE TEMP TABLE stage_teachers(source_row int,source_hash varchar(64),username text,first_name text,last_name text,email text,document_number text) ON COMMIT DROP`,
    insertValues("stage_teachers", ["source_row","source_hash","username","first_name","last_name","email","document_number"], input.teachers.map((r) => [r.sourceRow,r.hash,r.username,r.firstName,r.lastName,r.email,r.document])),
    `CREATE TEMP TABLE stage_levels(source_row int,source_hash varchar(64),code text,name text,segment text,level_order int,course_level text) ON COMMIT DROP`,
    insertValues("stage_levels", ["source_row","source_hash","code","name","segment","level_order","course_level"], input.levels.map((r) => [r.sourceRow,r.hash,r.code,r.name,r.segment,r.levelOrder,r.courseLevel])),
    `CREATE TEMP TABLE stage_courses(source_row int,source_hash varchar(64),code text,name text,level_code text,course_level text,teacher_hash varchar(64),max_students int,price numeric(12,2)) ON COMMIT DROP`,
    insertValues("stage_courses", ["source_row","source_hash","code","name","level_code","course_level","teacher_hash","max_students","price"], input.courses.map((r) => [r.sourceRow,r.hash,r.code,r.name,r.levelCode,r.courseLevel,r.teacherHash,r.maxStudents,r.price])),
    `CREATE TEMP TABLE stage_enrollments(source_row int,source_hash varchar(64),application_hash varchar(64),student_hash varchar(64),course_hash varchar(64),level_code text,plan_code text,plan_hash varchar(64),status text,original_status text,guardian_hash varchar(64),guardian_candidate_count int) ON COMMIT DROP`,
    insertValues("stage_enrollments", ["source_row","source_hash","application_hash","student_hash","course_hash","level_code","plan_code","plan_hash","status","original_status","guardian_hash","guardian_candidate_count"], input.enrollments.map((r) => [r.sourceRow,r.hash,r.applicationHash,r.studentHash,r.courseHash,r.levelCode,r.planCode,r.planHash,r.status,r.originalStatus,r.guardianHash,r.guardianCandidateCount])),
    `CREATE TEMP TABLE stage_relationships(student_hash varchar(64),guardian_hash varchar(64),selected boolean,reason text) ON COMMIT DROP`,
    insertValues("stage_relationships", ["student_hash","guardian_hash","selected","reason"], input.relationships.map((r) => [r.studentHash,r.guardianHash,r.selected,r.reason])),
    `CREATE TEMP TABLE stage_ledgers(source_hash varchar(64),application_hash varchar(64),enrollment_hash varchar(64),student_hash varchar(64),guardian_hash varchar(64),due_date date,amount numeric(12,2)) ON COMMIT DROP`,
    insertValues("stage_ledgers", ["source_hash","application_hash","enrollment_hash","student_hash","guardian_hash","due_date","amount"], input.ledgers.map((r) => [r.hash,r.applicationHash,r.enrollmentHash,r.studentHash,r.guardianHash,r.dueDate,r.amount])),
  ];
}

function importSystemActorSql(runId) {
  const actorHash = sourceHash("system-actor", "legacy-import");
  return `DO $$ DECLARE new_id bigint; BEGIN
    INSERT INTO users(username,email,password,first_name,last_name,role,status,active,created_at,updated_at) VALUES('system.legacy-import','legacy-import@invalid.sigep.local','${DISABLED_PASSWORD_HASH}','Importador','Legacy','ADMIN','REJECTED',false,now(),now()) RETURNING id INTO new_id;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,target_table,target_id,mapping_status,notes) VALUES(${sql(runId)},'SYSTEM_ACTOR','${actorHash}','users',new_id,'PLACEHOLDER_FIELDS','Non-login audit actor');
  END $$`;
}

function importGuardiansSql(runId) {
  return `DO $$ DECLARE r record; new_id bigint; BEGIN FOR r IN SELECT * FROM stage_guardians ORDER BY source_row LOOP
    INSERT INTO users(username,email,password,first_name,last_name,phone_number,address,date_of_birth,document_number,role,status,active,created_at,updated_at) VALUES('responsable-legacy-'||lpad(r.source_row::text,4,'0'),r.email,'${DISABLED_PASSWORD_HASH}',r.first_name,r.last_name,r.phone,r.address,r.birth_date,r.document_number,'GUARDIAN','PENDING_APPROVAL',false,now(),now()) RETURNING id INTO new_id;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,source_row,target_table,target_id,mapping_status,notes) VALUES(${sql(runId)},'GUARDIAN',r.source_hash,r.source_row,'users',new_id,'IMPORTED','Login disabled pending individual invitation');
  END LOOP; END $$`;
}

function importRelationshipsSql(runId) { return `INSERT INTO legacy_import_relationships(run_id,relationship_type,left_source_key_hash,right_source_key_hash,selected,selection_reason) SELECT ${sql(runId)},'STUDENT_GUARDIAN',student_hash,guardian_hash,selected,reason FROM stage_relationships`; }

function importIssuesSql(runId) {
  return `DO $$ BEGIN
    INSERT INTO legacy_import_issues(run_id,entity_type,source_key_hash,source_row,issue_code,severity,details)
      SELECT ${sql(runId)},'ENROLLMENT',source_hash,source_row,'MISSING_GUARDIAN_MATCH','BLOCKER',jsonb_build_object('effect','ACADEMIC_ONLY_NO_BILLING')
      FROM stage_enrollments WHERE status='ACTIVE' AND guardian_hash IS NULL;
    INSERT INTO legacy_import_issues(run_id,entity_type,source_key_hash,source_row,issue_code,severity,details)
      SELECT ${sql(runId)},'ENROLLMENT',source_hash,source_row,'MULTIPLE_GUARDIAN_MATCHES','WARNING',jsonb_build_object('candidate_count',guardian_candidate_count,'selection','BEST_CONTACT_COMPLETENESS_THEN_SOURCE_ROW')
      FROM stage_enrollments WHERE status='ACTIVE' AND guardian_candidate_count > 1;
    INSERT INTO legacy_import_issues(run_id,entity_type,issue_code,severity,details) VALUES
      (${sql(runId)},'STUDENT','MISSING_STUDENT_PROFILE_FIELDS','WARNING',jsonb_build_object('affected_students',(SELECT count(*) FROM stage_students),'placeholder_date','${UNKNOWN_DATE}')),
      (${sql(runId)},'COURSE','MISSING_SCHEDULE','BLOCKER',jsonb_build_object('affected_courses',(SELECT count(*) FROM stage_courses),'confirmed_capacity',20,'confirmed_duration_hours',60)),
      (${sql(runId)},'ENROLLMENT_FEE_POLICY','GENERAL_ENROLLMENT_FEE_UNKNOWN','BLOCKER',jsonb_build_object('general_policy_created',false,'kids_policy_status','PENDING_VALIDATION')),
      (${sql(runId)},'PAYMENT','PAYMENT_HISTORY_NOT_SUPPLIED','WARNING',jsonb_build_object('generated_period','2026-08 through 2026-12'));
  END $$`;
}

function importStudentsSql(runId) {
  const actorHash = sourceHash("system-actor", "legacy-import");
  return `DO $$ DECLARE r record; new_id bigint; guardian_id_value bigint; actor_id_value bigint; BEGIN
    SELECT target_id INTO actor_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash='${actorHash}' AND target_table='users';
    FOR r IN SELECT * FROM stage_students ORDER BY source_row LOOP guardian_id_value:=NULL;
      IF r.guardian_hash IS NOT NULL THEN SELECT target_id INTO guardian_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=r.guardian_hash AND target_table='users'; END IF;
      INSERT INTO students(student_number,first_name,last_name,email,date_of_birth,address,phone_number,emergency_contact,document_type,document_country,document_number,normalized_document_number,guardian_id,enrollment_date,medical_notes,active,current_level,created_at,updated_at) VALUES(r.student_number,r.first_name,r.last_name,r.email,DATE '${UNKNOWN_DATE}','${UNKNOWN_TEXT}',r.phone,r.emergency_contact,'NO_DOCUMENT','AR',NULL,NULL,guardian_id_value,r.enrollment_date,NULL,r.active,r.current_level,now(),now()) RETURNING id INTO new_id;
      INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,source_row,target_table,target_id,mapping_status,notes) VALUES(${sql(runId)},'STUDENT',r.source_hash,r.source_row,'students',new_id,'PLACEHOLDER_FIELDS','Birth date and address absent from legacy export');
      IF guardian_id_value IS NOT NULL THEN INSERT INTO student_guardian_link_events(student_id,previous_guardian_user_id,guardian_user_id,action,origin,actor_user_id,reason,created_at) VALUES(new_id,NULL,guardian_id_value,'LINKED','ADMIN',actor_id_value,'Legacy 2026 exact-name relationship import',now()); END IF;
    END LOOP; END $$`;
}

function importTeachersSql(runId) {
  return `DO $$ DECLARE r record; staff_id_value bigint; user_id_value bigint; BEGIN FOR r IN SELECT * FROM stage_teachers ORDER BY source_row LOOP
    INSERT INTO users(username,email,password,first_name,last_name,phone_number,address,date_of_birth,document_number,role,status,active,must_change_password,created_at,updated_at) VALUES(r.username,r.email,'${DISABLED_PASSWORD_HASH}',r.first_name,r.last_name,'${UNKNOWN_TEXT}','${UNKNOWN_TEXT}',DATE '${UNKNOWN_DATE}',r.document_number,'TEACHER','ACTIVE',true,true,now(),now()) RETURNING id INTO user_id_value;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,source_row,target_table,target_id,mapping_status,notes) VALUES(${sql(runId)},'TEACHER_ACCOUNT',r.source_hash,r.source_row,'users',user_id_value,'PLACEHOLDER_FIELDS','Account requires an out-of-band temporary password before first login');
    INSERT INTO teaching_staff(created_at,created_by,is_active,updated_at,updated_by,address,assigned_students_count,birth_date,document_number,email,emergency_contact_name,emergency_contact_phone,first_name,hire_date,last_name,linked_user_id,monthly_salary,payment_status,phone_number,specialization,qualifications,observations,notes) VALUES(now(),'legacy-import',true,now(),'legacy-import','${UNKNOWN_TEXT}',0,DATE '${UNKNOWN_DATE}',r.document_number,r.email,'${UNKNOWN_TEXT}','${UNKNOWN_TEXT}',r.first_name,DATE '2026-04-01',r.last_name,user_id_value,0,'UP_TO_DATE','${UNKNOWN_TEXT}','Ingles',NULL,'Datos de legajo pendientes de completar','Importado desde asignaciones de cursadas 2026') RETURNING id INTO staff_id_value;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,source_row,target_table,target_id,mapping_status,notes) VALUES(${sql(runId)},'TEACHER',r.source_hash,r.source_row,'teaching_staff',staff_id_value,'PLACEHOLDER_FIELDS','Only teacher name was present in course export');
  END LOOP; END $$`;
}

function importLevelsSql(runId) {
  return `DO $$ DECLARE r record; new_id bigint; BEGIN FOR r IN SELECT * FROM stage_levels ORDER BY level_order LOOP
    INSERT INTO tuition_levels(active,code,created_at,level_order,name,segment,updated_at,course_level) VALUES(true,r.code,now(),r.level_order,r.name,r.segment,now(),r.course_level) RETURNING id INTO new_id;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,source_row,target_table,target_id,mapping_status) VALUES(${sql(runId)},'TUITION_LEVEL',r.source_hash,r.source_row,'tuition_levels',new_id,'IMPORTED');
  END LOOP;
  INSERT INTO tuition_level_progression(active,created_at,rule,updated_at,from_level_id,to_level_id) SELECT true,now(),'PASS_PREVIOUS_LEVEL',now(),a.id,b.id FROM (VALUES ('CHILDREN_STARTER','CHILDREN_1'),('CHILDREN_1','CHILDREN_2'),('CHILDREN_2','CHILDREN_3'),('CHILDREN_3','CHILDREN_4'),('CHILDREN_4','CHILDREN_5'),('CHILDREN_5','CHILDREN_6'),('JUNIOR_STARTER','JUNIOR_1'),('JUNIOR_1','JUNIOR_2'),('JUNIOR_2','JUNIOR_3'),('TEENS_1','TEENS_2'),('TEENS_2','TEENS_3'),('TEENS_3','TEENS_4'),('TEENS_4','TEENS_5'),('SENIOR_1','SENIOR_2'),('ADULTS_1','ADULTS_2'),('ADULTS_2','ADULTS_3'),('ADULTS_3','ADULTS_4'),('ADULTS_4','ADULTS_5')) edge(from_code,to_code) JOIN tuition_levels a ON a.code=edge.from_code JOIN tuition_levels b ON b.code=edge.to_code;
  END $$`;
}

function importCatalogSql(runId) {
  return `DO $$ DECLARE year_id bigint; kids_level_id bigint; new_id bigint; BEGIN
    INSERT INTO tuition_academic_years(created_at,end_date,first_term_end_date,first_term_start_date,name,second_term_end_date,second_term_start_date,start_date,status,updated_at) VALUES(now(),DATE '2026-12-31',DATE '2026-07-31',DATE '2026-04-01','Ciclo lectivo 2026',DATE '2026-12-31',DATE '2026-08-01',DATE '2026-04-01','OPEN',now()) RETURNING id INTO year_id;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,target_table,target_id,mapping_status) VALUES(${sql(runId)},'ACADEMIC_YEAR','${sourceHash("academic-year", "2026")}','tuition_academic_years',year_id,'IMPORTED');
    SELECT id INTO kids_level_id FROM tuition_levels WHERE code='KIDS';
    INSERT INTO tuition_fee_plans(created_at,currency,installments,monthly_fee,name,segment,status,updated_at,valid_from,valid_to,academic_year_id,level_id,monthly_due_day,late_fee_percentage,automatic_debit_monthly) VALUES(now(),'ARS',9,90000,'Arancel general 2026',NULL,'ACTIVE',now(),DATE '2026-04-01',DATE '2026-12-31',year_id,NULL,10,0,false) RETURNING id INTO new_id;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,target_table,target_id,mapping_status) VALUES(${sql(runId)},'FEE_PLAN','${sourceHash("fee-plan", "PLAN-GENERAL-2026")}','tuition_fee_plans',new_id,'IMPORTED');
    INSERT INTO tuition_fee_plans(created_at,currency,installments,monthly_fee,name,segment,status,updated_at,valid_from,valid_to,academic_year_id,level_id,monthly_due_day,late_fee_percentage,automatic_debit_monthly) VALUES(now(),'ARS',9,80000,'Arancel Kids 2026','CHILDREN','ACTIVE',now(),DATE '2026-04-01',DATE '2026-12-31',year_id,kids_level_id,10,0,false) RETURNING id INTO new_id;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,target_table,target_id,mapping_status) VALUES(${sql(runId)},'FEE_PLAN','${sourceHash("fee-plan", "PLAN-KIDS-2026")}','tuition_fee_plans',new_id,'IMPORTED');
  END $$`;
}

function importCoursesSql(runId) {
  return `DO $$ DECLARE r record; new_id bigint; teacher_id_value bigint; BEGIN FOR r IN SELECT * FROM stage_courses ORDER BY code LOOP
    SELECT target_id INTO teacher_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=r.teacher_hash AND target_table='users' AND entity_type='TEACHER_ACCOUNT';
    INSERT INTO courses(code,created_at,description,duration,end_date,is_published,level,max_students,min_students,name,price,start_date,status,teacher_id,updated_at) VALUES(r.code,now(),'Cursada 2026 importada del sistema legacy. Horario y modalidad pendientes de conciliacion.',60,DATE '2026-12-31',true,r.course_level,r.max_students,1,r.name,r.price,DATE '2026-04-01','ACTIVE',teacher_id_value,now()) RETURNING id INTO new_id;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,source_row,target_table,target_id,mapping_status,notes) VALUES(${sql(runId)},'COURSE',r.source_hash,r.source_row,'courses',new_id,'IMPORTED','Duration 60 hours and capacity 20 confirmed; scheduling remains pending');
  END LOOP; END $$`;
}

function importEnrollmentsSql(runId) {
  return `DO $$ DECLARE r record; new_id bigint; student_id_value bigint; course_id_value bigint; BEGIN FOR r IN SELECT * FROM stage_enrollments ORDER BY source_row LOOP
    SELECT target_id INTO student_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=r.student_hash AND target_table='students';
    SELECT target_id INTO course_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=r.course_hash AND target_table='courses';
    INSERT INTO enrollments(completion_date,created_at,enrollment_date,final_grade,notes,status,student_id,updated_at,course_id) VALUES(NULL,now(),DATE '2026-04-01',NULL,'Importacion legacy 2026; estado original: '||r.original_status,r.status,student_id_value,now(),course_id_value) RETURNING id INTO new_id;
    INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,source_row,target_table,target_id,mapping_status) VALUES(${sql(runId)},'ENROLLMENT',r.source_hash,r.source_row,'enrollments',new_id,'IMPORTED');
  END LOOP; UPDATE teaching_staff t SET assigned_students_count=(SELECT count(*) FROM enrollments e JOIN courses c ON c.id=e.course_id WHERE c.teacher_id=t.linked_user_id AND e.status='ACTIVE'),updated_at=now(); END $$`;
}

function importApplicationsSql(runId) {
  const actorHash = sourceHash("system-actor", "legacy-import");
  return `DO $$ DECLARE r record; new_id bigint; student_id_value bigint; guardian_id_value bigint; enrollment_id_value bigint; course_id_value bigint; level_id_value bigint; plan_id_value bigint; year_id_value bigint; actor_id_value bigint; BEGIN
    SELECT target_id INTO actor_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash='${actorHash}' AND target_table='users';
    SELECT target_id INTO year_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash='${sourceHash("academic-year", "2026")}' AND target_table='tuition_academic_years';
    FOR r IN SELECT * FROM stage_enrollments WHERE status='ACTIVE' AND guardian_hash IS NOT NULL ORDER BY source_row LOOP
      SELECT target_id INTO student_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=r.student_hash AND target_table='students'; SELECT target_id INTO guardian_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=r.guardian_hash AND target_table='users'; SELECT target_id INTO enrollment_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=r.source_hash AND target_table='enrollments'; SELECT target_id INTO course_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=r.course_hash AND target_table='courses'; SELECT id INTO level_id_value FROM tuition_levels WHERE code=r.level_code; SELECT target_id INTO plan_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=r.plan_hash AND target_table='tuition_fee_plans';
      INSERT INTO tuition_applications(admin_notes,application_type,approved_at,approved_by,created_at,enrollment_id,guardian_user_id,status,submitted_at,updated_at,academic_year_id,fee_plan_id,requires_admin_override,assigned_course_id,assigned_level_id,actor_user_id,origin,student_id,student_resolution,idempotency_key,version) VALUES('Importacion de continuidad ciclo 2026','REGULAR_PROMOTION',now(),actor_id_value,now(),enrollment_id_value,guardian_id_value,'APPROVED',TIMESTAMP '2026-04-01 00:00:00',now(),year_id_value,plan_id_value,false,course_id_value,level_id_value,actor_id_value,'ADMIN',student_id_value,'EXISTING','legacy-2026-'||r.source_hash,0) RETURNING id INTO new_id;
      INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,source_row,target_table,target_id,mapping_status,notes) VALUES(${sql(runId)},'TUITION_APPLICATION',r.application_hash,r.source_row,'tuition_applications',new_id,'IMPORTED','No prior payments or enrollment fee imported');
    END LOOP; END $$`;
}

function importBillingSql(runId) {
  return `DO $$ DECLARE lr record; gh record; guardian_user record; student_user record; app_id_value bigint; ledger_id_value bigint; account_id_value bigint; charge_id_value bigint; BEGIN
    FOR gh IN SELECT DISTINCT guardian_hash FROM stage_ledgers LOOP
      SELECT u.* INTO guardian_user FROM users u JOIN legacy_import_entity_map m ON m.target_id=u.id AND m.target_table='users' WHERE m.run_id=${sql(runId)} AND m.source_key_hash=gh.guardian_hash;
      INSERT INTO billing_accounts(guardian_user_id,display_name,status,created_at,updated_at,version) VALUES(guardian_user.id,trim(guardian_user.first_name||' '||guardian_user.last_name),'ACTIVE',now(),now(),0) RETURNING id INTO account_id_value;
      INSERT INTO billing_profiles(account_id,receiver_name,receiver_address,receiver_document_number,default_fiscal_concept,fiscal_currency,rg_5866_applicable,status,created_at,updated_at,version) VALUES(account_id_value,trim(guardian_user.first_name||' '||guardian_user.last_name),guardian_user.address,guardian_user.document_number,2,'PES',false,'INCOMPLETE',now(),now(),0);
    END LOOP;
    FOR lr IN SELECT * FROM stage_ledgers ORDER BY due_date,student_hash LOOP
      SELECT target_id INTO app_id_value FROM legacy_import_entity_map WHERE run_id=${sql(runId)} AND source_key_hash=lr.application_hash AND target_table='tuition_applications';
      SELECT s.* INTO student_user FROM students s JOIN legacy_import_entity_map m ON m.target_id=s.id AND m.target_table='students' WHERE m.run_id=${sql(runId)} AND m.source_key_hash=lr.student_hash;
      INSERT INTO tuition_ledger_entries(concept,created_at,discount_amount,due_date,gross_amount,net_amount,status,student_id,updated_at,application_id,paid_amount,late_fee_amount,billing_reference) VALUES('MONTHLY_FEE',now(),0,lr.due_date,lr.amount,lr.amount,'PENDING',student_user.id,now(),app_id_value,0,0,'LEGACY-2026-'||substr(lr.source_hash,1,40)) RETURNING id INTO ledger_id_value;
      INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,target_table,target_id,mapping_status) VALUES(${sql(runId)},'TUITION_LEDGER',lr.source_hash,'tuition_ledger_entries',ledger_id_value,'IMPORTED');
      SELECT a.id INTO account_id_value FROM billing_accounts a JOIN legacy_import_entity_map m ON m.target_id=a.guardian_user_id AND m.target_table='users' WHERE m.run_id=${sql(runId)} AND m.source_key_hash=lr.guardian_hash;
      INSERT INTO billing_charges(account_id,student_id,student_name,source_type,source_id,concept,description,base_amount,amount,paid_amount,currency,due_date,service_from,service_to,late_fee_percentage,late_fee_eligible,automatic_debit_eligible,collection_channel,fiscal_disposition,status,created_at,updated_at,version) VALUES(account_id_value,student_user.id,trim(student_user.first_name||' '||student_user.last_name),'TUITION_LEDGER',ledger_id_value,'MONTHLY_FEE','Cuota '||extract(month from lr.due_date)::int||'/2026 - '||trim(student_user.first_name||' '||student_user.last_name),lr.amount,lr.amount,0,'ARS',lr.due_date,date_trunc('month',lr.due_date)::date,(date_trunc('month',lr.due_date)+interval '1 month - 1 day')::date,0,true,false,'REGULAR','PENDING','OPEN',now(),now(),0) RETURNING id INTO charge_id_value;
      INSERT INTO legacy_import_entity_map(run_id,entity_type,source_key_hash,target_table,target_id,mapping_status) VALUES(${sql(runId)},'BILLING_CHARGE',lr.source_hash,'billing_charges',charge_id_value,'IMPORTED');
    END LOOP; END $$`;
}

function postflightSql(input) {
  return `DO $$ BEGIN
    IF (SELECT count(*) FROM users) <> ${input.summary.guardianUsers + input.summary.teachingStaff + 1} THEN RAISE EXCEPTION 'User count mismatch'; END IF; IF (SELECT count(*) FROM students) <> ${input.summary.canonicalStudents} THEN RAISE EXCEPTION 'Student count mismatch'; END IF; IF (SELECT count(*) FROM teaching_staff) <> ${input.summary.teachingStaff} THEN RAISE EXCEPTION 'Teacher count mismatch'; END IF; IF (SELECT count(*) FROM tuition_levels) <> ${input.summary.tuitionLevels} THEN RAISE EXCEPTION 'Level count mismatch'; END IF; IF (SELECT count(*) FROM courses) <> ${input.summary.courses} THEN RAISE EXCEPTION 'Course count mismatch'; END IF; IF (SELECT count(*) FROM enrollments) <> ${input.summary.enrollments} THEN RAISE EXCEPTION 'Enrollment count mismatch'; END IF; IF (SELECT count(*) FROM tuition_applications) <> ${input.summary.billingReadyStudents} THEN RAISE EXCEPTION 'Application count mismatch'; END IF; IF (SELECT count(*) FROM tuition_ledger_entries) <> ${input.summary.monthlyLedgerEntries} THEN RAISE EXCEPTION 'Ledger count mismatch'; END IF; IF (SELECT count(*) FROM billing_charges) <> ${input.summary.monthlyLedgerEntries} THEN RAISE EXCEPTION 'Charge count mismatch'; END IF;
    IF EXISTS (SELECT 1 FROM teaching_staff t LEFT JOIN users u ON u.id=t.linked_user_id WHERE t.is_active=true AND (u.id IS NULL OR u.role NOT IN ('TEACHER','ADMIN') OR u.status<>'ACTIVE' OR u.active=false)) THEN RAISE EXCEPTION 'Active teaching staff without an eligible account'; END IF;
    IF EXISTS (SELECT 1 FROM courses c LEFT JOIN users u ON u.id=c.teacher_id WHERE c.teacher_id IS NOT NULL AND (u.id IS NULL OR u.role NOT IN ('TEACHER','ADMIN') OR u.status<>'ACTIVE' OR u.active=false)) THEN RAISE EXCEPTION 'Course references an ineligible teacher account'; END IF;
    IF EXISTS (SELECT 1 FROM enrollments e LEFT JOIN students s ON s.id=e.student_id LEFT JOIN courses c ON c.id=e.course_id WHERE s.id IS NULL OR c.id IS NULL) THEN RAISE EXCEPTION 'Broken enrollment references'; END IF; IF EXISTS (SELECT 1 FROM tuition_ledger_entries l LEFT JOIN billing_charges c ON c.source_type='TUITION_LEDGER' AND c.source_id=l.id WHERE c.id IS NULL) THEN RAISE EXCEPTION 'Ledger without billing charge'; END IF;
  END $$`;
}

function insertValues(table, columns, rows) { return rows.length ? `INSERT INTO ${table}(${columns.join(",")}) VALUES\n${rows.map((row) => `  (${row.map(sql).join(",")})`).join(",\n")}` : "SELECT 1"; }
function sql(value) { if (value === null || value === undefined || value === "") return "NULL"; if (typeof value === "number") return String(value); if (typeof value === "boolean") return value ? "true" : "false"; return `'${String(value).replaceAll("'", "''")}'`; }
