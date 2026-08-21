import crypto from "node:crypto";

export const LEGACY_TEACHER_ACCOUNTS = Object.freeze([
  teacher("ROSADO, AGUSTIN", "arosado", "Agustin", "Rosado", ["Junior starter B", "Junior 1 A", "Teens 3 C", "Children 4 D", "Teens 1 B"]),
  teacher("MASTRACCHIO, ANDRES", "amastracchio", "Andres", "Mastracchio", ["Adults - Starter", "Adults - Elementary"], "ADMIN"),
  teacher("ESCOBAR VELASQUEZ, CAMILA AILÉN", "cescobar", "Camila", "Escobar Velasquez", ["KIDS", "Children starter A", "Children starter B", "Children 1 B", "Children 2 D"]),
  teacher("DIAZ, MAURO EZEQUIEL", "mdiaz", "Mauro", "Diaz", ["Junior 2 A", "Teens 1 A"]),
  teacher("ENRIQUEZ, DANNA FLORENCIA", "denriquez", "Danna", "Enriquez", ["Children 1 C"]),
  teacher("FERREYRA, GABRIELA", "gferreyra", "Gabriela", "Ferreyra", ["Children 4 A", "Teens 3 A"]),
  teacher("ALTAMIRANO, HORIANA MICAELA", "haltamirano", "Horiana", "Altamirano", ["Children 2 C", "Children 4 B", "Teens 3 B", "Children 5 B"]),
  teacher("MONZON, ORNELLA", "omonzon", "Ornella", "Monzon", ["Children 3 A", "Children 3 C", "Junior starter A"]),
  teacher("CORDOVA, PAOLA", "pcordova", "Paola", "Cordova", ["Children 1 A", "Children 2 A", "Children 2 B", "Children 3 B", "Children 4 C", "Children 6 A", "Junior 2 B", "Teens 2 A", "teens 4 A", "Teens 4 B", "Senior 1 A", "Children 5 A", "teens 4 C"]),
  teacher("Mainero, Regina", "rmainero", "Regina", "Mainero", ["Teens 5 A", "Senior 2 A", "Senior 1 B"], "ADMIN"),
]);

export function normalizeLegacyValue(value) {
  return String(value ?? "")
    .trim()
    .normalize("NFKD")
    .replace(/\p{Diacritic}/gu, "")
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, " ")
    .trim();
}

export function resolveLegacyTeacher(sourceName) {
  const normalized = normalizeLegacyValue(sourceName);
  const matches = LEGACY_TEACHER_ACCOUNTS.filter((entry) => entry.normalizedSourceName === normalized);
  if (matches.length !== 1) {
    throw new Error(`Legacy teacher '${sourceName}' has ${matches.length} account mappings`);
  }
  return matches[0];
}

export function legacyTeacherDocument(sourceName) {
  const digest = crypto.createHash("sha256").update(`teacher:${normalizeLegacyValue(sourceName)}`).digest("hex");
  return `LEGACY-${digest.slice(0, 20)}`;
}

export function legacyCourseCode(courseName) {
  return `2026-${normalizeLegacyValue(courseName).replace(/ /g, "-")}`.slice(0, 50);
}

function teacher(sourceName, username, firstName, lastName, courseNames, existingRole = null) {
  return Object.freeze({
    sourceName,
    normalizedSourceName: normalizeLegacyValue(sourceName),
    username,
    firstName,
    lastName,
    existingRole,
    legacyDocument: legacyTeacherDocument(sourceName),
    courseNames: Object.freeze([...courseNames]),
  });
}
