# DATABASE_STRUCTURE.md

## Estado actual de estructura de Base de Datos (SiGEP)

**Fecha de relevamiento:** 2026-05-10  
**Ultima actualizacion:** 2026-05-10 (incluye V11)  
**Entorno auditado:** `sigep_db` (PostgreSQL), contraste con codigo Kotlin actual + snapshot de `users`.

## 1) Fuente de verdad operativa

Orden de prioridad:
1. **BD real en ejecucion**.
2. **Entidades JPA** (`**/domain/model/*.kt`).
3. **Migraciones SQL** (`scripts/migrations/*.sql`).
4. **Contrato API** (`API_CONTRACT.md`).

## 2) Snapshot estructural actual

### Tablas detectadas: 19
`course_attendance`, `course_certificates`, `course_materials`, `course_schedules`, `course_sessions`, `courses`, `enrollments`, `exam_grade_history`, `exam_submissions`, `exams`, `non_teaching_staff`, `notifications`, `payments`, `registration_requests`, `session_exceptions`, `staff_attendance`, `students`, `teaching_staff`, `users`.

### PK por modulo
- Modulos generales (`users`, `students`, `courses`, `staff`, `payments`, etc.): **BIGINT**.
- Modulo exams (`exams`, `exam_submissions`, `exam_grade_history`): **UUID** en PK.

### FK cross-modulo (vigentes)
- `exams.course_id` -> `courses.id` (BIGINT)
- `exams.created_by` -> `users.id` (BIGINT)
- `exam_submissions.student_id` -> `students.id` (BIGINT)
- `exam_submissions.graded_by` -> `users.id` (BIGINT)
- `exam_grade_history.changed_by` -> `users.id` (BIGINT)

## 3) Cambios recientes relevantes (V10 + V11)

| Version | Archivo | Resultado |
|---|---|---|
| V10 | `scripts/migrations/V10__auth_registration_approval_workflow.sql` | `users.status` + tabla `registration_requests` para aprobacion de registro |
| V11 | `scripts/migrations/V11__extend_users_profile_fields.sql` | nuevos campos de perfil en `users` + indice/constraint para `document_number` |

## 4) Tabla `users` (estado actual)

### Columnas base (previas)
- `id` (BIGINT, PK)
- `username` (varchar, unique, not null)
- `email` (varchar, unique, not null)
- `password` (varchar, not null)
- `first_name` (varchar, not null)
- `last_name` (varchar, not null)
- `role` (varchar, not null)
- `status` (varchar, not null)
- `active` (boolean, not null)
- `created_at` (timestamp, not null)
- `updated_at` (timestamp, not null)

### Columnas nuevas (V11)
- `phone_number` (varchar(20), null)
- `address` (varchar(500), null)
- `date_of_birth` (date, null)
- `document_number` (varchar(50), null)
- `emergency_contact` (varchar(255), null)

### Restricciones/indices nuevos (V11)
- Indice: `idx_users_document_number` en `users(document_number)`.
- Constraint: `users_document_number_unique_not_null` (UNIQUE NULLS DISTINCT con `WHERE document_number IS NOT NULL`).

### Evidencia de coherencia
- El snapshot de `users` exportado (CSV) ya refleja estas 5 columnas nuevas.
- En codigo, `User.kt` mapea explicitamente:
  - `phoneNumber` -> `phone_number`
  - `address` -> `address`
  - `dateOfBirth` -> `date_of_birth`
  - `documentNumber` -> `document_number`
  - `emergencyContact` -> `emergency_contact`

## 5) Trazabilidad BD <-> Codigo <-> API

### Seguridad/Auth
- `POST /api/v1/auth/register` ahora acepta perfil extendido y persiste en `users`.
- `GET /api/v1/users/me` expone esos campos desde `users`.

### Students
- `POST /api/v1/students/self-registration` usa `userId` del JWT y puede precargar datos desde perfil de `users`.
- `guardianId` de `students` se deriva del usuario autenticado (no del payload).

## 6) Scripts operativos de validacion y datos

- `scripts/validate-db-schema.sql` -> validacion SQL compatible con DBeaver.
- `scripts/validate-db-schema.sh` -> validacion por consola (psql).
- `scripts/backfill_users_profile_fields_dbeaver.sql` -> backfill de campos nuevos en `users` para datos historicos.

## 7) Brechas residuales

1. **Pipeline unico de migraciones**:
   - Sigue pendiente consolidar ejecucion automatica de migraciones para todo el monolito.

2. **Scripts legacy de datos**:
   - `scripts/insert-test-data.sql` puede contener referencias desactualizadas frente a limpieza V8.

3. **Datos historicos incompletos en perfil**:
   - Usuarios preexistentes pueden quedar con campos de perfil en null hasta ejecutar backfill.

## 8) Comandos de auditoria rapida

```powershell
docker exec sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name;"
docker exec sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT table_name,column_name,data_type,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position;"
docker exec sigep-postgres psql -U sigep_user -d sigep_db -f /docker-entrypoint-initdb.d/validate-db-schema.sql
```




