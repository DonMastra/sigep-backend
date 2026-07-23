# DATABASE_STRUCTURE.md

## Estado actual de estructura de Base de Datos (SiGEP)

**Fecha de relevamiento:** 2026-05-31  
**Ultima actualizacion:** 2026-07-21 (incluye V17 y desglose fiscal WSFE)
**Entorno auditado:** `sigep_db` (PostgreSQL 15), validado contra codigo Kotlin actual y migraciones SQL.

## 1) Fuente de verdad operativa

Orden de prioridad:
1. **BD real en ejecucion**.
2. **Entidades JPA** (`**/domain/model/*.kt`).
3. **Migraciones SQL** (`scripts/migrations/*.sql`).
4. **Contrato API** (`API_CONTRACT.md`).

## 2) Snapshot estructural actual

### Tablas detectadas/modeladas: 36
`billing_outbox`, `classrooms`, `course_attendance`, `course_certificates`, `course_materials`, `course_sessions`, `courses`, `enrollments`, `exam_grade_history`, `exam_submissions`, `exams`, `fiscal_invoice_attempts`, `fiscal_invoice_taxes`, `fiscal_invoice_vat_subtotals`, `fiscal_invoices`, `non_teaching_staff`, `notifications`, `payment_receipts`, `payments`, `registration_requests`, `reservations`, `schedule_slots`, `session_exceptions`, `staff_attendance`, `students`, `teaching_staff`, `tuition_academic_years`, `tuition_applications`, `tuition_discounts`, `tuition_fee_plans`, `tuition_ledger_entries`, `tuition_level_progression`, `tuition_levels`, `tuition_seat_reservations`, `users`, `voucher_sequences`.

### Cambios de V12 (Scheduling)
- Se elimina tabla legacy `course_schedules`.
- Se crean tablas nuevas:
  - `classrooms`
  - `schedule_slots`
  - `reservations`
- Se agrega indice unico parcial `uq_slot_active_reservation` para garantizar maximo una reserva activa por slot (`status <> 'INACTIVE'`).

### Cambios de V13 (Tuition)
- Se agrega bounded context `tuition` para matriculacion como proceso.
- Tablas nuevas:
  - `tuition_academic_years`
  - `tuition_levels`
  - `tuition_level_progression`
  - `tuition_fee_plans`
  - `tuition_discounts`
  - `tuition_applications`
  - `tuition_seat_reservations`
  - `tuition_ledger_entries`
- `tuition_level_progression` usa indice unico parcial `uq_tuition_progression_active_from` para permitir una sola progresion activa por nivel origen.
- `tuition_seat_reservations` reserva una vacante por solicitud antes de crear `enrollments`.
- `tuition_ledger_entries` modela deuda mock; no factura, no emite CAE y no almacena datos de tarjeta.

### Cambios de V14/V15 (primer flujo manual)

- `teaching_staff.linked_user_id` enlaza un docente con una cuenta `users` activa de rol
  `TEACHER`; `photo_data`, `photo_content_type` y `photo_filename` almacenan la foto en
  PostgreSQL. El indice parcial evita dos docentes con la misma cuenta.
- `courses.teacher_id` pasa a ser nullable y `uk_courses_code_ci` garantiza codigo unico
  sin distinguir mayusculas.
- `tuition_levels.course_level` explicita el mapeo al nivel de cursos; la migracion conserva
  `BEGINNER`/`ELEMENTARY` y traduce `A1`/`A2` a esos valores.
- `tuition_applications.progression_rule` y `requires_admin_override` registran excepciones
  de progresion.
- `course_attendance.course_session_id` referencia la sesion real; el indice unico parcial
  `uk_attendance_enrollment_session` permite una asistencia por alumno/sesion y varias sesiones
  en una misma fecha.
- V15 solo reemplaza el hash BCrypt legacy conocido de los usuarios de prueba; no migra
  contrasenas arbitrarias.

### Cambios de V16 (facturacion persistente)

- `payments` conserva filas legacy y suma moneda, referencias externas, claves/fingerprints
  idempotentes, confirmacion auditada y `@Version`; `payment_date` pasa a nullable para pagos
  `PENDING` y el monto se normaliza a `NUMERIC(12,2)`.
- `payment_receipts` mantiene una instantanea inmutable por pago del recibo X no fiscal, con
  la leyenda `DOCUMENTO NO VALIDO COMO FACTURA`.
- `fiscal_invoices` mantiene una factura por pago, preflight, datos WSFE, numero, CAE,
  observaciones/errores sanitizados y version optimista.
- `fiscal_invoice_attempts` audita autorizaciones y conciliaciones sin guardar XML, Token o Sign.
- `billing_outbox` desacopla la transaccion local de la llamada fiscal y distingue resultados
  pendientes, procesados, fallidos o en conciliacion.
- `voucher_sequences` serializa por CUIT emisor, punto de venta y tipo de comprobante; el worker
  usa bloqueo pesimista antes de asignar numero.

### Cambios de V17 (detalle fiscal)

- `fiscal_invoices.receiver_address` conserva el domicilio del receptor requerido por el PDF.
- `fiscal_invoice_vat_subtotals` persiste codigo, base e importe por alicuota IVA.
- `fiscal_invoice_taxes` persiste codigo, descripcion, base, alicuota e importe por tributo.
- Ambas colecciones conservan orden, restringen valores invalidos y referencian la factura.

### PK por modulo
- Modulos generales (`users`, `students`, `courses`, `staff`, `scheduling`, `tuition`, etc.): **BIGINT**.
- Modulo exams (`exams`, `exam_submissions`, `exam_grade_history`): **UUID** en PK.

### FK cross-modulo (vigentes)
- `exams.course_id` -> `courses.id` (BIGINT)
- `exams.created_by` -> `users.id` (BIGINT)
- `exam_submissions.student_id` -> `students.id` (BIGINT)
- `exam_submissions.graded_by` -> `users.id` (BIGINT)
- `exam_grade_history.changed_by` -> `users.id` (BIGINT)
- `tuition_applications.guardian_user_id` -> `users.id` (BIGINT)
- `tuition_applications.student_id` -> `students.id` (BIGINT, nullable)
- `tuition_applications.requested_course_id` -> `courses.id` (BIGINT)
- `tuition_applications.enrollment_id` -> `enrollments.id` (BIGINT, nullable)
- `tuition_discounts.student_id` -> `students.id` (BIGINT, nullable)
- `tuition_ledger_entries.student_id` -> `students.id` (BIGINT, nullable)
- `teaching_staff.linked_user_id` -> `users.id` (BIGINT, `ON DELETE SET NULL`)
- `course_attendance.course_session_id` -> `course_sessions.id` (BIGINT, `ON DELETE RESTRICT`)
- `payment_receipts.payment_id` -> `payments.id` (BIGINT, unico, `ON DELETE RESTRICT`)
- `fiscal_invoices.payment_id` -> `payments.id` (BIGINT, unico, `ON DELETE RESTRICT`)
- `fiscal_invoice_attempts.invoice_id` -> `fiscal_invoices.id` (BIGINT, `ON DELETE RESTRICT`)
- `billing_outbox.invoice_id` -> `fiscal_invoices.id` (BIGINT, `ON DELETE RESTRICT`)
- `fiscal_invoice_vat_subtotals.invoice_id` -> `fiscal_invoices.id` (BIGINT, `ON DELETE RESTRICT`)
- `fiscal_invoice_taxes.invoice_id` -> `fiscal_invoices.id` (BIGINT, `ON DELETE RESTRICT`)

### FK de scheduling (V12)
- `schedule_slots.classroom_id` -> `classrooms.id` (`ON DELETE RESTRICT`)
- `reservations.slot_id` -> `schedule_slots.id` (`ON DELETE RESTRICT`)

## 3) Cambios recientes relevantes (V10 a V17)

| Version | Archivo | Resultado |
|---|---|---|
| V10 | `scripts/migrations/V10__auth_registration_approval_workflow.sql` | `users.status` + tabla `registration_requests` para aprobacion de registro |
| V11 | `scripts/migrations/V11__extend_users_profile_fields.sql` | nuevos campos de perfil en `users` |
| V12 | `scripts/migrations/V12__create_scheduling_module.sql` | nuevo esquema de scheduling (`classrooms`, `schedule_slots`, `reservations`) y drop de `course_schedules` |
| V13 | `scripts/migrations/V13__create_tuition_module.sql` | nuevo esquema tuition para ciclo lectivo, niveles, planes, solicitudes, reservas y ledger mock |
| V14 | `scripts/migrations/V14__fix_first_manual_flow.sql` | compatibilidad del primer flujo manual |
| V15 | `scripts/migrations/V15__repair_legacy_test_password_hash.sql` | reparacion acotada de hashes legacy de prueba |
| V16 | `scripts/migrations/V16__create_billing_persistence.sql` | pagos compatibles, recibos X, facturas, intentos, outbox y secuencias |
| V17 | `scripts/migrations/V17__add_fiscal_tax_breakdown.sql` | domicilio receptor y detalle IVA/tributos |

## 4) Validacion ejecutada en BD (2026-05-31)

### Ejecucion de migracion
- Script ejecutado: `scripts/migrations/V12__create_scheduling_module.sql`
- Resultado: `DROP TABLE`, `CREATE TABLE` e indices creados sin error.

### Estado del esquema scheduling
- `classrooms`: columnas y defaults correctos (`active=true`, timestamps con `now()`).
- `schedule_slots`: constraint `chk_slot_day_of_week` vigente y FK a `classrooms` vigente.
- `reservations`: constraints `chk_reservation_target_type` y `chk_reservation_status` vigentes.
- Indices detectados:
  - `idx_classroom_name`
  - `idx_slot_classroom`
  - `idx_slot_day`
  - `idx_reservation_slot`
  - `idx_reservation_status`
  - `idx_reservation_target`
  - `uq_slot_active_reservation` (unico parcial)

### Estado de datos al cierre de validacion
- `classrooms`: 0 filas
- `schedule_slots`: 0 filas
- `reservations`: 0 filas

### Verificacion de deprecacion legacy
- `course_schedules_exists = false` (tabla removida correctamente).

### Validacion complementaria de V16 (2026-07-21)

- Se creo una base PostgreSQL 15 descartable con la tabla `payments` legacy y una fila previa.
- V16 se aplico con `ON_ERROR_STOP=1` y se volvio a aplicar para comprobar idempotencia.
- La fila legacy se preservo con moneda `ARS`, version `0` y su fecha original.
- Se verifico la existencia de las cinco tablas nuevas: `payment_receipts`, `fiscal_invoices`,
  `fiscal_invoice_attempts`, `billing_outbox` y `voucher_sequences`.
- La aplicacion arranco contra ese esquema y expuso el controller protegido; la base descartable
  se elimino al terminar.

### Validacion complementaria de V17 (2026-07-21)

- V16 y V17 se aplicaron en orden sobre PostgreSQL 15 descartable con una factura previa a V17.
- Se verifico el backfill de `receiver_address`, la insercion de una alicuota IVA y un tributo.
- V17 se reaplico con `ON_ERROR_STOP=1` para confirmar idempotencia; el contenedor se elimino.

## 5) Validacion complementaria de `users`

- Se ejecuto `scripts/validate-db-schema.sql` y se verifico:
  - Persisten los campos extendidos (`phone_number`, `address`, `date_of_birth`, `document_number`, `emergency_contact`).
  - No hubo regresiones funcionales por V12 sobre `users`.
- Observacion: la base actual muestra indices `uk...` para `email` y `username`; no aparece `idx_users_document_number` en este entorno auditado.

## 6) Trazabilidad BD <-> Codigo <-> API

### Seguridad/Auth
- `POST /api/v1/auth/register` persiste perfil extendido en `users`.
- `GET /api/v1/users/me` expone esos campos desde `users`.

### Scheduling
- `CourseSchedule` queda deprecado en modelo y BD.
- La asignacion horaria se modela con:
  - `schedule_slots` (franjas por aula)
  - `reservations` (asignacion a `COURSE` o `SESSION`)

### Tuition
- `POST /api/v1/tuition/applications` crea solicitudes de matriculacion para guardian autenticado.
- `POST /api/v1/tuition/applications/{id}/reserve-seat` crea `tuition_seat_reservations` y `tuition_ledger_entries` de matricula inicial.
- `POST /api/v1/tuition/applications/{id}/mock-payment` marca la matricula inicial como `MOCK_PAID`.
- `PUT /api/v1/tuition/applications/{id}/approve` confirma la reserva, activa guardian si corresponde, crea estudiante cuando es nuevo y crea `enrollments`.
- `tuition_ledger_entries` queda como ledger mock interno hasta que exista el modulo real de pagos/facturacion.

## 7) Scripts operativos de validacion

- `scripts/migrations/V12__create_scheduling_module.sql` -> migracion de scheduling.
- `scripts/migrations/V13__create_tuition_module.sql` -> migracion de tuition.
- `scripts/migrations/V16__create_billing_persistence.sql` -> migracion de pagos/facturacion.
- `scripts/migrations/V17__add_fiscal_tax_breakdown.sql` -> domicilio y desglose impositivo.
- `scripts/validate-db-schema.sql` -> validacion de esquema de `users`.
- `scripts/validate-db-schema.sh` -> validacion por consola (psql).

## 8) Brechas residuales

1. **Pipeline unico de migraciones**:
   - Pendiente consolidar ejecucion automatica para todo el monolito.

2. **Script de validacion general**:
   - `scripts/validate-db-schema.sql` hoy valida principalmente `users`; conviene extenderlo para incluir `classrooms`, `schedule_slots`, `reservations` y tablas `tuition_*`.

3. **Datos semilla scheduling**:
   - No hay seed inicial de aulas/slots/reservas; el estado actual queda vacio por diseno.

4. **Datos semilla tuition**:
   - No hay seed inicial de ciclos, niveles, progresiones ni planes de cuota; deben cargarse por API admin antes de usar el flujo.

5. **Aplicacion de V14/V15/V16/V17**:
   - Los scripts estan versionados como artefactos operativos. Validarlos primero en una
     base descartable o en una transaccion revertida; el contenedor local existente no debe
     modificarse automaticamente durante el desarrollo.

   - V16/V17 ya fueron validadas en una base descartable; aun deben incorporarse al procedimiento
     controlado de despliegue de cada ambiente.

6. **Ledger mock**:
   - Las cuotas mensuales se generan/normalizan para enero-diciembre del año de inicio del
     ciclo lectivo (hasta 12 filas). La normalizacion de DTO no reescribe filas historicas.

## 9) Comandos de auditoria rapida

```powershell
Get-Content "scripts/migrations/V12__create_scheduling_module.sql" | docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -v ON_ERROR_STOP=1
Get-Content "scripts/migrations/V13__create_tuition_module.sql" | docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -v ON_ERROR_STOP=1
Get-Content "scripts/migrations/V16__create_billing_persistence.sql" | docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -v ON_ERROR_STOP=1
Get-Content "scripts/migrations/V17__add_fiscal_tax_breakdown.sql" | docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -v ON_ERROR_STOP=1
docker exec sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name;"
docker exec sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT tablename, indexname FROM pg_indexes WHERE schemaname='public' AND tablename IN ('classrooms','schedule_slots','reservations') ORDER BY tablename, indexname;"
docker exec sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT tablename, indexname FROM pg_indexes WHERE schemaname='public' AND tablename LIKE 'tuition_%' ORDER BY tablename, indexname;"
docker exec sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name='course_schedules') AS course_schedules_exists;"
```




