import assert from "node:assert/strict";
import test from "node:test";
import {
  LEGACY_TEACHER_ACCOUNTS,
  legacyCourseCode,
  resolveLegacyTeacher,
} from "./teacher-account-mapping.mjs";

test("maps the ten reviewed legacy teachers to unique usernames", () => {
  assert.equal(LEGACY_TEACHER_ACCOUNTS.length, 10);
  assert.equal(new Set(LEGACY_TEACHER_ACCOUNTS.map((entry) => entry.username)).size, 10);
  assert.equal(resolveLegacyTeacher("ESCOBAR VELASQUEZ, CAMILA AILÉN").username, "cescobar");
  assert.equal(resolveLegacyTeacher("mainero, regina").username, "rmainero");
});

test("keeps one deterministic assignment for every imported course", () => {
  const courseCodes = LEGACY_TEACHER_ACCOUNTS.flatMap((teacher) => teacher.courseNames.map(legacyCourseCode));
  assert.equal(courseCodes.length, 40);
  assert.equal(new Set(courseCodes).size, 40);
  assert.equal(legacyCourseCode("Adults - Elementary"), "2026-ADULTS-ELEMENTARY");
});

test("rejects a teacher that was not reviewed", () => {
  assert.throws(() => resolveLegacyTeacher("DOCENTE DESCONOCIDO"), /0 account mappings/);
});
