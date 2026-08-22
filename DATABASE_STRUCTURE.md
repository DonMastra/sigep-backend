# Estructura de base de datos de SiGEP

## 1. Alcance y fuente de verdad

**Última auditoría:** 2026-08-21

**Entorno auditado:** Neon, base productiva `sigep_prod`, esquema `public`

**Motor informado por el servidor:** PostgreSQL 18.6

**Modo de auditoría:** conexión con `default_transaction_read_only=on` y consultas sin datos personales.

El orden de precedencia para describir el estado actual es:

1. Catálogo y constraints reales de `sigep_prod`.
2. Entidades y repositorios Kotlin.
3. Scripts manuales de `scripts/migrations`.
4. Contrato REST de `API_CONTRACT.md`.

Los scripts SQL son migraciones manuales; el proyecto no usa Flyway ni Liquibase. QA y producción
deben arrancar con `JPA_DDL_AUTO=validate`: Hibernate valida, pero no repara el esquema.

## 2. Snapshot productivo antes de V33

`sigep_prod` contiene **59 tablas públicas**. `guardian_client_profiles` todavía no existe en este
snapshot y será incorporada por V33; por lo tanto, no debe presentarse como desplegada hasta aplicar
la migración de forma controlada.

### Seguridad e identidad

| Tabla | Filas observadas | Función |
|---|---:|---|
| `users` | 615 | Identidad, credenciales, rol y estado de acceso. |
| `registration_requests` | 0 | Solicitudes de registro público. |
| `guardian_invitations` | 0 | Invitación administrativa de tutores. |
| `schema_version` | 4 | Registro operativo agregado desde V27. |

Distribución exacta de `users` durante la auditoría:

- 4 ADMIN `ACTIVE`, 1 ADMIN `REJECTED`.
- 602 GUARDIAN `PENDING_APPROVAL` con `active=false`.
- 8 TEACHER `ACTIVE`.

El estado de acceso no representa por sí solo el estado comercial: de esos 602 tutores, 312 tienen
estudiantes vinculados y 180 ya tienen cuenta de facturación activa. El módulo de tutores/clientes
debe exponer ambas dimensiones sin mezclarlas.

### Estudiantes y vínculo con tutor

| Tabla | Filas observadas | Función |
|---|---:|---|
| `students` | 546 | Perfil académico del estudiante. |
| `student_guardian_link_events` | 357 | Auditoría inmutable de vínculos y reasignaciones. |

- `students.guardian_id -> users.id` es nullable y representa **un único tutor vigente**.
- `student_guardian_link_events` no reemplaza ese vínculo: registra actor, origen, acción, tutor
  anterior/nuevo, motivo y fecha.
- Hay 357 estudiantes vinculados y 189 sin tutor; 308 estudiantes están activos.
- 312 tutores tienen al menos un estudiante; 290 no tienen ninguno. El máximo observado es 3.
- Los 357 eventos productivos son `LINKED` con origen `ADMIN`.
- No se detectaron tutores inexistentes o con rol distinto de `GUARDIAN` en `students.guardian_id`.

### Cursos, cursadas y asistencia

| Tabla | Filas observadas |
|---|---:|
| `courses` | 40 |
| `enrollments` | 320 |
| `course_sessions` | 1205 |
| `course_attendance` | 0 |
| `course_materials` | 0 |
| `course_certificates` | 0 |

`enrollments.student_id -> students.id` y `enrollments.course_id -> courses.id`. Hay 308 cursadas
`ACTIVE` y 12 `DROPPED`. En producción, `courses.teacher_id` está definido `NOT NULL`, referencia
`users.id` y la FK declara `ON DELETE SET NULL`; esta contradicción se registra como deuda técnica.

### Matriculación (`tuition`)

| Tabla | Filas observadas |
|---|---:|
| `tuition_academic_years` | 1 |
| `tuition_levels` | 24 |
| `tuition_level_progression` | 18 |
| `tuition_fee_plans` | 2 |
| `tuition_enrollment_fee_policies` | 2 |
| `tuition_discounts` | 0 |
| `tuition_applications` | 211 |
| `tuition_placement_assessments` | 0 |
| `tuition_ledger_entries` | 1055 |

Relaciones principales:

- `tuition_applications.guardian_user_id -> users.id` identifica al tutor representado.
- `actor_user_id -> users.id` identifica quién ejecutó la operación y `origin` distingue ADMIN/GUARDIAN.
- `student_id -> students.id` se resuelve antes de generar cargos.
- `enrollment_id -> enrollments.id`, `assigned_course_id -> courses.id` y las referencias a ciclo,
  nivel, plan y política conservan la decisión académica.
- `tuition_ledger_entries.application_id -> tuition_applications.id` y `student_id -> students.id`.

Estado exacto observado:

- 211 solicitudes `APPROVED`, todas `REGULAR_PROMOTION`, origen `ADMIN` y estudiante `EXISTING`.
- 1055 entradas de ledger `PENDING` por ARS 94.650.000 en total.
- 180 tutores distintos tienen solicitudes; no hay discrepancias entre el tutor de la solicitud y
  `students.guardian_id`.

El ledger es deuda académica. No es un recibo, pago ni factura fiscal.

### Cobranza, pagos y facturación

| Tabla | Filas observadas |
|---|---:|
| `billing_accounts` | 180 |
| `billing_profiles` | 180 |
| `billing_charges` | 1055 |
| `payment_allocations` | 0 |
| `payments` | 0 |
| `payment_receipts` | 0 |
| `fiscal_invoices` | 0 |
| `fiscal_invoice_attempts` | 0 |
| `fiscal_invoice_vat_subtotals` | 0 |
| `fiscal_invoice_taxes` | 0 |
| `billing_charge_fiscal_decisions` | 0 |
| `billing_charge_adjustments` | 0 |
| `billing_runs` | 0 |
| `billing_run_items` | 0 |
| `billing_outbox` | 0 |
| `automatic_debit_mandates` | 0 |
| `automatic_debit_instructions` | 0 |
| `automatic_debit_events` | 0 |
| `voucher_sequences` | 0 |

Relaciones y separación de documentos:

- `billing_accounts.guardian_user_id -> users.id` es único: una cuenta por tutor.
- `billing_profiles.account_id -> billing_accounts.id` es único: un perfil fiscal reutilizable.
- `billing_charges.account_id -> billing_accounts.id`; `student_id -> students.id` es nullable.
- `(source_type, source_id)` evita duplicar el cargo proveniente del ledger.
- `payment_allocations` es la relación muchos-a-muchos entre pagos y cargos, única por
  `(payment_id, charge_id)`.
- `payment_receipts` es el recibo X no fiscal de un pago.
- `fiscal_invoices` referencia exactamente un pago o un cargo y conserva el circuito fiscal.

Los 180 `billing_accounts` están `ACTIVE`, todos tienen `billing_profile`; los 1055 cargos están
`OPEN`, sin importes pagados, por ARS 94.650.000. No hay pagos, recibos ni facturas. No se
detectaron cuentas cuyo titular no fuera GUARDIAN, cargos cuyo tutor difiriera del tutor vigente
del estudiante ni cuentas sin perfil.

### Staff, scheduling, exámenes y comunicaciones

| Dominio | Tablas y filas observadas |
|---|---|
| Staff | `teaching_staff` 10; `non_teaching_staff` 0; `staff_attendance` 0. |
| Scheduling | `classrooms` 0; `schedule_slots` 0; `reservations` 0; `session_exceptions` 0. |
| Exams | `exams` 0; `exam_submissions` 0; `exam_grade_history` 0. |

### Auditoría de importación legacy

| Tabla | Filas observadas |
|---|---:|
| `legacy_import_runs` | 1 |
| `legacy_import_entity_map` | 3736 |
| `legacy_import_relationships` | 391 |
| `legacy_import_issues` | 148 |
| `legacy_reconciliation_runs` | 2 |
| `legacy_reconciliation_decisions` | 840 |
| `legacy_reconciliation_changes` | 2161 |
| `legacy_teacher_linkage_repair_runs` | 2 |
| `legacy_teacher_linkage_repair_backup` | 80 |

Estas tablas son evidencia de importación/reconciliación y no deben usarse como fuente operativa
de la ficha actual de estudiantes, tutores o cobros.

## 3. Inventario completo de tablas en `sigep_prod`

```text
automatic_debit_events
automatic_debit_instructions
automatic_debit_mandates
billing_accounts
billing_charge_adjustments
billing_charge_fiscal_decisions
billing_charges
billing_outbox
billing_profiles
billing_run_items
billing_runs
classrooms
course_attendance
course_certificates
course_materials
course_sessions
courses
enrollments
exam_grade_history
exam_submissions
exams
fiscal_invoice_attempts
fiscal_invoice_taxes
fiscal_invoice_vat_subtotals
fiscal_invoices
guardian_invitations
legacy_import_entity_map
legacy_import_issues
legacy_import_relationships
legacy_import_runs
legacy_reconciliation_changes
legacy_reconciliation_decisions
legacy_reconciliation_runs
legacy_teacher_linkage_repair_backup
legacy_teacher_linkage_repair_runs
non_teaching_staff
payment_allocations
payment_receipts
payments
registration_requests
reservations
schedule_slots
schema_version
session_exceptions
staff_attendance
student_guardian_link_events
students
teaching_staff
tuition_academic_years
tuition_applications
tuition_discounts
tuition_enrollment_fee_policies
tuition_fee_plans
tuition_ledger_entries
tuition_level_progression
tuition_levels
tuition_placement_assessments
users
voucher_sequences
```

Secuencias detectadas: 12, correspondientes a cuentas/cargos/perfiles/ejecuciones de facturación,
imputaciones, auditorías legacy/reconciliación y eventos tutor-estudiante. Entidades principales
importadas conservan IDs explícitos; no se debe inferir que toda PK BIGINT tiene secuencia.

## 4. Registro de migraciones productivo

`schema_version` tiene columnas `version`, `git_commit`, `applied_at`, `description` y contiene:

| Versión | Aplicada (UTC) | Descripción registrada |
|---|---|---|
| V27 | 2026-08-14 19:26 | baseline de esquema QA validada para UAT de capacitación |
| V28 | 2026-08-14 22:25 | auditoría de importación legacy y dataset 2026 |
| V29 | 2026-08-15 15:08 | reconciliación institucional legacy 2026 |
| V30 | 2026-08-15 15:08 | identificador de negocio del estudiante |

La presencia física de `users.must_change_password` y de la FK docente de cursos demuestra cambios
posteriores, pero V31/V32 no están registrados en esa tabla. El registro debe corregirse en el
procedimiento de despliegue; no debe inventarse un hash Git ni insertarse desde una migración de
dominio.

## 5. V33: perfil administrativo de tutor/cliente

Archivo: `scripts/migrations/V33__create_guardian_client_profiles.sql`.

V33 crea `guardian_client_profiles`, con relación 1:1 a `users`:

| Columna | Tipo | Regla |
|---|---|---|
| `guardian_user_id` | BIGINT | PK y FK a `users(id)`, `ON DELETE CASCADE`. |
| `client_number` | VARCHAR(32) | único, estable, formato `CLI-` + ID rellenado. |
| `preferred_contact_channel` | VARCHAR(20) | `EMAIL`, `PHONE` o `WHATSAPP`. |
| `administrative_notes` | VARCHAR(1000) | nullable; observación interna, no dato fiscal. |
| `updated_by` | BIGINT | FK nullable a `users(id)`, `ON DELETE RESTRICT`. |
| `created_at`, `updated_at` | TIMESTAMP | auditoría temporal. |
| `version` | BIGINT | concurrencia optimista. |

La migración:

- Es reejecutable (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`).
- Retrocompleta exactamente los usuarios con rol `GUARDIAN`; no crea perfiles para ADMIN/TEACHER.
- Valida las dos FKs después del backfill; la migración falla si detecta un perfil huérfano.
- No crea otra tabla de relación tutor-estudiante: `students.guardian_id` sigue siendo la fuente
  vigente y `student_guardian_link_events` su auditoría.
- No copia ni duplica ledger, cargos, pagos, recibos o facturas.
- Llevará el total del esquema a 60 tablas cuando sea promovida.

## 6. Constraints e índices relevantes

- `users`: únicos por `username` y `email`; checks de rol y estado.
- `students`: PK BIGINT, único `student_number`; índice de tutor e identidad normalizada creado por V27.
- `student_guardian_link_events`: índices por estudiante/fecha y tutor.
- `tuition_applications`: FKs a tutor, actor, estudiante, ciclo, nivel, plan y política; idempotencia
  opcional y unicidad parcial para solicitudes abiertas.
- `tuition_ledger_entries`: único `billing_reference`; unicidades parciales por matrícula y período.
- `enrollments`: unicidad parcial de cursada activa por estudiante/curso.
- `billing_accounts`: único `guardian_user_id`.
- `billing_profiles`: único `account_id`.
- `billing_charges`: único `(source_type, source_id)`; índices por cuenta, estudiante y estado/vencimiento.
- `payment_allocations`: único `(payment_id, charge_id)`.
- V33: único `client_number` e índice de canal preferido.

## 7. Desvíos reales a conservar en diagnóstico

1. `courses.teacher_id` es `NOT NULL` pero `fk_courses_teacher_user` usa `ON DELETE SET NULL`.
2. `billing_charges.student_id` tiene `billing_charges_student_id_fkey` y
   `fk_billing_charge_student`; la segunda está `NOT VALID`.
3. Varias FKs agregadas por V27 permanecen `NOT VALID`: aplican a filas nuevas, pero PostgreSQL no
   certificó todo el histórico.
4. `billing_charges.fiscal_disposition` es `VARCHAR(30)` en producción y `length=20` en JPA.
5. `schema_version` no registra V31/V32 pese a que sus cambios físicos están presentes.

Estos puntos no forman parte de V33. Deben resolverse mediante una migración de reparación separada,
con preflight y verificación histórica.

## 8. Validaciones mínimas para promover V33

```sql
SELECT count(*) FROM users WHERE role = 'GUARDIAN';
SELECT count(*) FROM guardian_client_profiles;
SELECT count(*) FROM guardian_client_profiles p
LEFT JOIN users u ON u.id = p.guardian_user_id
WHERE u.id IS NULL OR u.role <> 'GUARDIAN';
SELECT client_number, count(*) FROM guardian_client_profiles
GROUP BY client_number HAVING count(*) > 1;
```

Resultados esperados inmediatamente después de V33 sobre el snapshot auditado: 602 perfiles,
cero perfiles huérfanos/no-GUARDIAN y cero números duplicados. Después debe arrancarse la aplicación
con `JPA_DDL_AUTO=validate` y probarse la API ADMIN sin realizar escrituras en producción durante el
preflight.
