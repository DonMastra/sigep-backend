# DATABASE_STRUCTURE.md

## Estado actual de estructura de Base de Datos (SiGEP)

**Fecha de relevamiento:** 2026-05-05  
**Última actualización:** 2026-05-05 (V6 + V7 + V8 + V9 + V10 aplicadas)  
**Entorno auditado:** `sigep_db` en contenedor `sigep-postgres` (Docker local)

## 1) Fuente de verdad operativa

Orden de prioridad:
1. **BD real en ejecución**.
2. **Entidades JPA** (`**/domain/model/*.kt`).
3. **Scripts SQL/migrations**.
4. **`API_CONTRACT.md`**.

## 2) Snapshot real actual

### Tablas detectadas: 19
`course_attendance`, `course_certificates`, `course_materials`, `course_schedules`, `course_sessions`, `courses`, `enrollments`, `exam_grade_history`, `exam_submissions`, `exams`, `non_teaching_staff`, `notifications`, `payments`, `registration_requests`, `session_exceptions`, `staff_attendance`, `students`, `teaching_staff`, `users`.

### PK por módulo
- Módulos generales (`users`, `students`, `courses`, `staff`, `payments`, etc.): **BIGINT**.
- Módulo exams (`exams`, `exam_submissions`, `exam_grade_history`): **UUID** en PK.

### FK cross-módulo (corregidas)
- `exams.course_id` -> `courses.id` (BIGINT)
- `exams.created_by` -> `users.id` (BIGINT)
- `exam_submissions.student_id` -> `students.id` (BIGINT)
- `exam_submissions.graded_by` -> `users.id` (BIGINT)
- `exam_grade_history.changed_by` -> `users.id` (BIGINT)

## 3) Migraciones aplicadas en este ciclo

| Versión | Archivo | Resultado |
|---|---|---|
| V6 | `exams/src/main/resources/db/migration/V6__fix_exams_cross_module_id_types.sql` | UUID->BIGINT en referencias cross-módulo de exams |
| V7 | `scripts/migrations/V7__fix_schema_integrity.sql` | `students.guardian_id` nullable + NOT NULL en campos críticos + normalización de `course_sessions` |
| V8 | `scripts/migrations/V8__cleanup_legacy_schema.sql` | limpieza de columnas legacy + eliminación de tablas huérfanas |
| V9 | `scripts/migrations/V9__align_check_constraints_with_enums.sql` | CHECKs alineados con enums (`exams.status`, `non_teaching_staff.role`) |
| V10 | `scripts/migrations/V10__auth_registration_approval_workflow.sql` | `users.status` + tabla `registration_requests` para workflow de aprobación |

## 4) Brechas cerradas

### A) Columnas legacy
Se eliminaron 23 columnas legacy (ya no mapeadas por entidades actuales), incluyendo:
- `students.phone`, `students.status`
- `courses.max_capacity`, `courses.schedule`, `courses.classroom`
- `course_sessions.session_number`, `scheduled_date`, `classroom`, `created_by`, `updated_by`
- `course_materials.uploaded_at`
- `course_certificates.expiration_date`, `certificate_url`
- `exam_grade_history.change_reason`, `version`
- `staff_attendance.staff_id`, `staff_type`, `created_by`, `updated_by`
- `teaching_staff.status`
- `non_teaching_staff.position`, `company`, `status`

### B) Tablas huérfanas
Se eliminaron:
- `exam_results`
- `course_enrollments`

### C) Contrato API
Se actualizó `API_CONTRACT.md` en sección de Exams:
- IDs reales (`UUID` en `exams` y `exam-submissions`)
- Endpoints reales de `ExamController` y `ExamSubmissionController`
- Enums reales (`ExamStatus`, `SubmissionStatus`, `ExamModality`)

### D) Alineación de checks y entidades
- `exams.status` ya **no** permite `GRADED`; queda alineado con `ExamStatus`.
- `non_teaching_staff.role` ahora permite `IT` además de `IT_SUPPORT`; queda alineado con `NonTeachingRole`.
- `payments.paymentDate` se alineó en código como no nullable (`LocalDate`) para coincidir con `payment_date NOT NULL`.

## 5) Brechas residuales

1. **Sin pipeline único de migraciones automáticas para todo el monolito**:
   - hoy se usan scripts por módulo y ejecución manual en este entorno.

2. **Scripts legacy de datos de prueba no actualizados**:
   - `scripts/insert-test-data.sql` referencia columnas eliminadas en V8.
   - No afecta endpoints/productivo, pero sí bootstrap manual con ese script.

3. **Pendiente de hardening para producción del flujo de registro**:
   - en dev se validó con migración manual V10; en ambientes superiores conviene mover este script a pipeline automatizado y ejecutar con ventana controlada.

## 6) Archivos impactados

- `scripts/migrations/V8__cleanup_legacy_schema.sql` (nuevo)
- `scripts/migrations/V9__align_check_constraints_with_enums.sql` (nuevo)
- `scripts/migrations/V10__auth_registration_approval_workflow.sql` (nuevo)
- `payments/src/main/kotlin/com/sigep/payments/domain/model/Payment.kt` (nullable fix)
- `API_CONTRACT.md` (actualizado sección Exams)
- `DATABASE_STRUCTURE.md` (este archivo)

## 7) Comandos de auditoría rápida

```powershell
docker exec sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name;"
docker exec sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT table_name,column_name,data_type,udt_name,is_nullable,column_default FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position;"
docker exec sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT tc.table_name,kcu.column_name,ccu.table_name AS ref_table,ccu.column_name AS ref_column FROM information_schema.table_constraints tc JOIN information_schema.key_column_usage kcu ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name=tc.constraint_name AND ccu.table_schema=tc.table_schema WHERE tc.constraint_type='FOREIGN KEY' AND tc.table_schema='public' ORDER BY tc.table_name,kcu.column_name;"
```


