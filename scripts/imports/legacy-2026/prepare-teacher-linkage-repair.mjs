import fs from "node:fs";
import path from "node:path";
import { LEGACY_TEACHER_ACCOUNTS, legacyCourseCode } from "./teacher-account-mapping.mjs";

const args = parseArgs(process.argv.slice(2));
const credentialsPath = requiredPath("credentials");
const outputPath = requiredPath("output");
const rollbackPath = requiredPath("rollback-output");
const expectedDatabase = args.get("expected-database") ?? "sigep_prod";
const runId = args.get("run-id") ?? `TEACHER-LINKAGE-${new Date().toISOString().replace(/[-:.]/g, "")}`;

assertIdentifier(runId, "run id");
assertIdentifier(expectedDatabase, "database name");
[outputPath, rollbackPath].forEach((target) => {
  if (fs.existsSync(target)) throw new Error(`Refusing to overwrite ${target}`);
  fs.mkdirSync(path.dirname(target), { recursive: true });
});

const credentialRows = JSON.parse(fs.readFileSync(credentialsPath, "utf8"));
if (!Array.isArray(credentialRows)) throw new Error("Credentials must be a JSON array");
const passwordHashes = new Map(credentialRows.map((entry) => [entry.username, entry.passwordHash]));
const expectedUsernames = LEGACY_TEACHER_ACCOUNTS.map((teacher) => teacher.username).sort();
if (passwordHashes.size !== 10 || expectedUsernames.some((username) => !passwordHashes.has(username))) {
  throw new Error(`Credentials must contain exactly: ${expectedUsernames.join(", ")}`);
}
for (const [username, passwordHash] of passwordHashes) {
  if (!/^\$2[aby]\$\d{2}\$.{53}$/.test(passwordHash)) throw new Error(`Invalid BCrypt hash for ${username}`);
}

const teachers = LEGACY_TEACHER_ACCOUNTS.map((teacher) => [
  teacher.username,
  teacher.firstName,
  teacher.lastName,
  teacher.legacyDocument,
  teacher.existingRole ?? "TEACHER",
  passwordHashes.get(teacher.username),
]);
const courses = LEGACY_TEACHER_ACCOUNTS.flatMap((teacher) =>
  teacher.courseNames.map((courseName) => [legacyCourseCode(courseName), teacher.username])
);
if (courses.length !== 40 || new Set(courses.map(([code]) => code)).size !== 40) {
  throw new Error("Expected exactly 40 unique course assignments");
}

fs.writeFileSync(outputPath, repairSql(), { encoding: "utf8", flag: "wx" });
fs.writeFileSync(rollbackPath, rollbackSql(), { encoding: "utf8", flag: "wx" });
console.log(JSON.stringify({ runId, teachers: teachers.length, courses: courses.length, outputPath, rollbackPath }, null, 2));

function repairSql() {
  return `-- Generated teacher repair. Contains BCrypt hashes, never plaintext passwords.
BEGIN;

DO $$ BEGIN
  IF current_database() <> ${sql(expectedDatabase)} THEN
    RAISE EXCEPTION 'Wrong database: expected %, got %', ${sql(expectedDatabase)}, current_database();
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS legacy_teacher_linkage_repair_runs (
  run_id varchar(80) PRIMARY KEY,
  status varchar(24) NOT NULL,
  started_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at timestamp,
  rolled_back_at timestamp
);
CREATE TABLE IF NOT EXISTS legacy_teacher_linkage_repair_backup (
  run_id varchar(80) NOT NULL REFERENCES legacy_teacher_linkage_repair_runs(run_id),
  entity_type varchar(24) NOT NULL,
  target_id bigint NOT NULL,
  change_type varchar(16) NOT NULL,
  previous_state jsonb,
  PRIMARY KEY (run_id, entity_type, target_id)
);

CREATE TEMP TABLE stage_teacher_accounts (
  username varchar(100) PRIMARY KEY,
  first_name varchar(100) NOT NULL,
  last_name varchar(100) NOT NULL,
  legacy_document varchar(50) UNIQUE NOT NULL,
  required_role varchar(20) NOT NULL,
  password_hash varchar(100) NOT NULL
) ON COMMIT DROP;
${insertValues("stage_teacher_accounts", ["username", "first_name", "last_name", "legacy_document", "required_role", "password_hash"], teachers)};

CREATE TEMP TABLE stage_teacher_courses (
  course_code varchar(50) PRIMARY KEY,
  username varchar(100) NOT NULL REFERENCES stage_teacher_accounts(username)
) ON COMMIT DROP;
${insertValues("stage_teacher_courses", ["course_code", "username"], courses)};

DO $$
DECLARE teacher_count integer; course_count integer;
BEGIN
  IF EXISTS (SELECT 1 FROM legacy_teacher_linkage_repair_runs WHERE run_id=${sql(runId)}) THEN
    RAISE EXCEPTION 'Repair run already exists';
  END IF;
  SELECT count(*) INTO teacher_count
  FROM teaching_staff staff JOIN stage_teacher_accounts staged ON staged.legacy_document=staff.document_number
  WHERE staff.is_active=true;
  IF teacher_count<>10 THEN RAISE EXCEPTION 'Teacher preflight: expected 10, got %', teacher_count; END IF;
  IF EXISTS (
    SELECT staged.legacy_document FROM stage_teacher_accounts staged
    LEFT JOIN teaching_staff staff ON staff.document_number=staged.legacy_document AND staff.is_active=true
    GROUP BY staged.legacy_document HAVING count(staff.id)<>1
  ) THEN RAISE EXCEPTION 'Each teacher must match one active staff row'; END IF;

  SELECT count(*) INTO course_count
  FROM courses course JOIN stage_teacher_courses staged ON staged.course_code=course.code;
  IF course_count<>40 THEN RAISE EXCEPTION 'Course preflight: expected 40, got %', course_count; END IF;
  IF EXISTS (
    SELECT staged.course_code FROM stage_teacher_courses staged
    LEFT JOIN courses course ON course.code=staged.course_code
    GROUP BY staged.course_code HAVING count(course.id)<>1
  ) THEN RAISE EXCEPTION 'Each reviewed course must match one row'; END IF;

  IF EXISTS (
    SELECT 1 FROM stage_teacher_accounts staged
    LEFT JOIN users account ON lower(account.username)=lower(staged.username)
    WHERE staged.required_role='ADMIN' AND (account.id IS NULL OR account.role<>'ADMIN')
  ) THEN RAISE EXCEPTION 'amastracchio and rmainero must already be ADMIN accounts'; END IF;
  IF EXISTS (
    SELECT 1 FROM stage_teacher_accounts staged
    JOIN users account ON lower(account.username)=lower(staged.username)
    WHERE staged.required_role='TEACHER' AND account.role<>'TEACHER'
  ) THEN RAISE EXCEPTION 'A teacher username is owned by a non-TEACHER account'; END IF;
  IF EXISTS (
    SELECT 1 FROM stage_teacher_accounts staged
    JOIN teaching_staff staff ON staff.document_number=staged.legacy_document
    JOIN users account ON lower(account.email)=lower(staff.email)
    WHERE staged.required_role='TEACHER' AND lower(account.username)<>lower(staged.username)
  ) THEN RAISE EXCEPTION 'A teacher email is owned by another account'; END IF;
END $$;

INSERT INTO legacy_teacher_linkage_repair_runs(run_id,status) VALUES (${sql(runId)},'IN_PROGRESS');
INSERT INTO legacy_teacher_linkage_repair_backup(run_id,entity_type,target_id,change_type,previous_state)
SELECT ${sql(runId)},'TEACHING_STAFF',staff.id,'UPDATED',to_jsonb(staff)
FROM teaching_staff staff JOIN stage_teacher_accounts staged ON staged.legacy_document=staff.document_number;
INSERT INTO legacy_teacher_linkage_repair_backup(run_id,entity_type,target_id,change_type,previous_state)
SELECT ${sql(runId)},'COURSE',course.id,'UPDATED',to_jsonb(course)
FROM courses course JOIN stage_teacher_courses staged ON staged.course_code=course.code;
INSERT INTO legacy_teacher_linkage_repair_backup(run_id,entity_type,target_id,change_type,previous_state)
SELECT ${sql(runId)},'USER',account.id,'UPDATED',to_jsonb(account)
FROM users account JOIN stage_teacher_accounts staged ON lower(staged.username)=lower(account.username);

INSERT INTO users(username,email,password,first_name,last_name,phone_number,address,date_of_birth,
                  document_number,emergency_contact,role,status,active,must_change_password,
                  password_changed_at,created_at,updated_at)
SELECT staged.username,staff.email,staged.password_hash,staged.first_name,staged.last_name,
       staff.phone_number,staff.address,staff.birth_date,staff.document_number,
       concat_ws(' / ',staff.emergency_contact_name,staff.emergency_contact_phone),
       'TEACHER','ACTIVE',true,true,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
FROM stage_teacher_accounts staged
JOIN teaching_staff staff ON staff.document_number=staged.legacy_document
LEFT JOIN users account ON lower(account.username)=lower(staged.username)
WHERE staged.required_role='TEACHER' AND account.id IS NULL;

INSERT INTO legacy_teacher_linkage_repair_backup(run_id,entity_type,target_id,change_type,previous_state)
SELECT ${sql(runId)},'USER',account.id,'CREATED',NULL
FROM users account JOIN stage_teacher_accounts staged ON lower(staged.username)=lower(account.username)
WHERE NOT EXISTS (
  SELECT 1 FROM legacy_teacher_linkage_repair_backup backup
  WHERE backup.run_id=${sql(runId)} AND backup.entity_type='USER' AND backup.target_id=account.id
);

UPDATE users account SET
  password=staged.password_hash,first_name=staged.first_name,last_name=staged.last_name,
  status='ACTIVE',active=true,must_change_password=true,password_changed_at=NULL,updated_at=CURRENT_TIMESTAMP
FROM stage_teacher_accounts staged WHERE lower(account.username)=lower(staged.username);

UPDATE teaching_staff staff SET
  linked_user_id=account.id,first_name=staged.first_name,last_name=staged.last_name,updated_at=CURRENT_TIMESTAMP
FROM stage_teacher_accounts staged JOIN users account ON lower(account.username)=lower(staged.username)
WHERE staff.document_number=staged.legacy_document;

UPDATE courses course SET teacher_id=account.id,updated_at=CURRENT_TIMESTAMP
FROM stage_teacher_courses staged JOIN users account ON lower(account.username)=lower(staged.username)
WHERE course.code=staged.course_code;

UPDATE teaching_staff staff SET
  assigned_students_count=(
    SELECT count(DISTINCT enrollment.student_id)
    FROM courses course JOIN enrollments enrollment ON enrollment.course_id=course.id AND enrollment.status='ACTIVE'
    WHERE course.teacher_id=staff.linked_user_id
  ), updated_at=CURRENT_TIMESTAMP
WHERE staff.document_number IN (SELECT legacy_document FROM stage_teacher_accounts);

DO $$ BEGIN
  IF EXISTS (
    SELECT 1 FROM stage_teacher_accounts staged
    JOIN teaching_staff staff ON staff.document_number=staged.legacy_document
    LEFT JOIN users account ON account.id=staff.linked_user_id
    WHERE account.id IS NULL OR lower(account.username)<>lower(staged.username)
       OR account.role NOT IN ('TEACHER','ADMIN') OR account.status<>'ACTIVE'
       OR account.active=false OR account.must_change_password=false
  ) THEN RAISE EXCEPTION 'Teacher-account postflight mismatch'; END IF;
  IF (SELECT count(*) FROM stage_teacher_courses staged JOIN courses course ON course.code=staged.course_code JOIN users account ON account.id=course.teacher_id WHERE lower(account.username)=lower(staged.username))<>40
  THEN RAISE EXCEPTION 'Course-teacher postflight mismatch'; END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname='fk_courses_teacher_user' AND conrelid='courses'::regclass) THEN
    ALTER TABLE courses ADD CONSTRAINT fk_courses_teacher_user FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE SET NULL;
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_courses_teacher_id ON courses(teacher_id) WHERE teacher_id IS NOT NULL;

UPDATE legacy_teacher_linkage_repair_runs SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE run_id=${sql(runId)};
COMMIT;
`;
}

function rollbackSql() {
  return `-- Rollback for ${runId}. Restores accounts, staff links and course links from the audit snapshot.
BEGIN;
DO $$ BEGIN
  IF current_database()<>${sql(expectedDatabase)} THEN RAISE EXCEPTION 'Wrong database'; END IF;
  IF NOT EXISTS (SELECT 1 FROM legacy_teacher_linkage_repair_runs WHERE run_id=${sql(runId)} AND status='COMPLETED') THEN
    RAISE EXCEPTION 'Completed repair run not found';
  END IF;
END $$;

UPDATE courses course SET
  teacher_id=NULLIF(backup.previous_state->>'teacher_id','')::bigint,
  updated_at=(backup.previous_state->>'updated_at')::timestamp
FROM legacy_teacher_linkage_repair_backup backup
WHERE backup.run_id=${sql(runId)} AND backup.entity_type='COURSE' AND backup.target_id=course.id;

UPDATE teaching_staff staff SET
  linked_user_id=NULLIF(backup.previous_state->>'linked_user_id','')::bigint,
  first_name=backup.previous_state->>'first_name',last_name=backup.previous_state->>'last_name',
  assigned_students_count=(backup.previous_state->>'assigned_students_count')::integer,
  updated_at=(backup.previous_state->>'updated_at')::timestamp
FROM legacy_teacher_linkage_repair_backup backup
WHERE backup.run_id=${sql(runId)} AND backup.entity_type='TEACHING_STAFF' AND backup.target_id=staff.id;

UPDATE users account SET
  password=backup.previous_state->>'password',first_name=backup.previous_state->>'first_name',
  last_name=backup.previous_state->>'last_name',role=backup.previous_state->>'role',
  status=backup.previous_state->>'status',active=(backup.previous_state->>'active')::boolean,
  must_change_password=(backup.previous_state->>'must_change_password')::boolean,
  password_changed_at=NULLIF(backup.previous_state->>'password_changed_at','')::timestamp,
  updated_at=(backup.previous_state->>'updated_at')::timestamp
FROM legacy_teacher_linkage_repair_backup backup
WHERE backup.run_id=${sql(runId)} AND backup.entity_type='USER' AND backup.change_type='UPDATED' AND backup.target_id=account.id;

DELETE FROM users account USING legacy_teacher_linkage_repair_backup backup
WHERE backup.run_id=${sql(runId)} AND backup.entity_type='USER' AND backup.change_type='CREATED' AND backup.target_id=account.id;
UPDATE legacy_teacher_linkage_repair_runs SET status='ROLLED_BACK',rolled_back_at=CURRENT_TIMESTAMP WHERE run_id=${sql(runId)};
COMMIT;
`;
}

function parseArgs(values) {
  const result = new Map();
  for (let index=0; index<values.length; index+=2) {
    if (!values[index]?.startsWith("--") || values[index+1]===undefined) throw new Error(`Invalid argument near ${values[index] ?? "<end>"}`);
    result.set(values[index].slice(2),values[index+1]);
  }
  return result;
}
function requiredPath(name) {
  const value=args.get(name); if (!value) throw new Error(`Missing --${name}`); return path.resolve(value);
}
function assertIdentifier(value,label) {
  if (!/^[A-Za-z0-9._:-]{1,80}$/.test(value)) throw new Error(`Invalid ${label}`);
}
function insertValues(table,columns,rows) {
  return `INSERT INTO ${table}(${columns.join(",")}) VALUES\n${rows.map((row)=>`  (${row.map(sql).join(",")})`).join(",\n")}`;
}
function sql(value) {
  if (value===null || value===undefined) return "NULL"; return `'${String(value).replaceAll("'","''")}'`;
}
