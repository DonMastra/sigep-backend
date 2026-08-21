import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { LEGACY_TEACHER_ACCOUNTS } from "./teacher-account-mapping.mjs";

test("generates guarded repair and rollback scripts for ten teachers and forty courses", () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "sigep-teachers-"));
  const credentialsPath = path.join(directory, "hashes.json");
  const outputPath = path.join(directory, "repair.sql");
  const rollbackPath = path.join(directory, "rollback.sql");
  const fakeHash = `$2b$10$${"a".repeat(53)}`;
  fs.writeFileSync(credentialsPath, JSON.stringify(
    LEGACY_TEACHER_ACCOUNTS.map(({ username }) => ({ username, passwordHash: fakeHash }))
  ));

  execFileSync(process.execPath, [
    path.resolve("scripts/imports/legacy-2026/prepare-teacher-linkage-repair.mjs"),
    "--credentials", credentialsPath,
    "--output", outputPath,
    "--rollback-output", rollbackPath,
    "--expected-database", "sigep_prod",
    "--run-id", "TEACHER-LINKAGE-TEST",
  ]);

  const repair = fs.readFileSync(outputPath, "utf8");
  const rollback = fs.readFileSync(rollbackPath, "utf8");
  assert.match(repair, /Teacher preflight: expected 10/);
  assert.match(repair, /Course preflight: expected 40/);
  assert.match(repair, /amastracchio and rmainero must already be ADMIN accounts/);
  assert.match(repair, /must_change_password=true/);
  assert.match(repair, /fk_courses_teacher_user/);
  assert.doesNotMatch(repair, /password123/i);
  assert.match(rollback, /change_type='CREATED'/);
  assert.match(rollback, /status='ROLLED_BACK'/);
});
