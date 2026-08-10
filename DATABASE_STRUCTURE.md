# DATABASE_STRUCTURE.md

## Estado actual de estructura de Base de Datos (SiGEP)

**Fecha de relevamiento:** 2026-05-31  
**Ultima actualizacion:** 2026-08-10 (incluye V26: foto docente normalizada a BYTEA)
**Entorno auditado:** `sigep_db` (PostgreSQL 15), validado contra codigo Kotlin actual y migraciones SQL.

## 1) Fuente de verdad operativa

Orden de prioridad:
1. **BD real en ejecucion**.
2. **Entidades JPA** (`**/domain/model/*.kt`).
3. **Migraciones SQL** (`scripts/migrations/*.sql`).
4. **Contrato API** (`API_CONTRACT.md`).

## 2) Snapshot estructural actual

### Tablas detectadas/modeladas: 47
`automatic_debit_events`, `automatic_debit_instructions`, `automatic_debit_mandates`,
`billing_accounts`, `billing_charge_adjustments`, `billing_charge_fiscal_decisions`,
`billing_charges`, `billing_outbox`, `billing_profiles`, `billing_run_items`, `billing_runs`,
`classrooms`, `course_attendance`, `course_certificates`, `course_materials`, `course_sessions`,
`courses`, `enrollments`, `exam_grade_history`, `exam_submissions`, `exams`,
`fiscal_invoice_attempts`, `fiscal_invoice_taxes`, `fiscal_invoice_vat_subtotals`,
`fiscal_invoices`, `non_teaching_staff`, `payment_allocations`, `payment_receipts`, `payments`,
`registration_requests`, `reservations`, `schedule_slots`, `session_exceptions`,
`staff_attendance`, `students`, `teaching_staff`,
`tuition_academic_years`, `tuition_applications`, `tuition_discounts`,
`tuition_enrollment_fee_policies`, `tuition_fee_plans`, `tuition_placement_assessments`,
`tuition_ledger_entries`, `tuition_level_progression`, `tuition_levels`,
`users`, `voucher_sequences`.

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
  - `tuition_ledger_entries`
- `tuition_level_progression` usa indice unico parcial `uq_tuition_progression_active_from` para permitir una sola progresion activa por nivel origen.
- V25 elimina `tuition_seat_reservations`; el cupo se valida exclusivamente durante la asignacion administrativa.
- `tuition_ledger_entries` modela deuda academica; V18 sincroniza cada entrada con un
  `billing_charge`. Tuition no emite CAE ni almacena datos de tarjeta.

### Cambios de V14/V15 (primer flujo manual)

- `teaching_staff.linked_user_id` enlaza un docente con una cuenta `users` activa de rol
  `TEACHER`; `photo_data`, `photo_content_type` y `photo_filename` almacenan la foto en
  PostgreSQL. V26 garantiza que `photo_data` use `BYTEA`, en concordancia con la entidad JPA,
  y falla de forma explicita si encuentra fotos legacy que requieran una conversion dirigida.
  El indice parcial evita dos docentes con la misma cuenta.
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

### Cambios de V18 (cuentas, cargos y ejecuciones manuales)

- `billing_accounts` agrupa al responsable de pago y `billing_profiles` conserva sus datos
  fiscales reutilizables, con estado `INCOMPLETE` o `READY`.
- `billing_charges` representa deudas originadas por matricula o cuota y evita duplicados por
  `(source_type, source_id)`.
- `payment_allocations` registra la imputacion de un pago al cargo que liquida.
- `billing_runs` y `billing_run_items` auditan preparaciones individuales, seleccionadas o
  filtradas, con idempotencia y resultado por cargo.
- `fiscal_invoices` puede referenciar exactamente un pago legacy o un cargo.
- La configuracion del primer cliente fuerza `rg_5866_applicable=false`.

### Cambios de V22 a V24

- V22 agrega `base_amount`, `paid_amount`, `PARTIALLY_PAID` y `fiscal_disposition` a
  `billing_charges`; elimina la unicidad por cargo de `payment_allocations` y crea
  `billing_charge_fiscal_decisions`. El ledger de matricula replica pagado y recargo.
- V23 agrega al plan `monthly_due_day`, `late_fee_percentage`, elegibilidad de debito para cuota y
  matricula, y guarda la instantanea en cada cargo. `billing_charge_adjustments` conserva base,
  porcentaje, importe, fecha efectiva, origen y reversa del unico recargo activo.
- V24 agrega `billing_charges.collection_channel` y crea `automatic_debit_mandates`,
  `automatic_debit_instructions` y `automatic_debit_events`. La adhesion es por cuenta y guarda
  procesadora, tipo de instrumento, alcance, vigencia, referencia opaca y etiqueta enmascarada.
  Cada instruccion referencia obligatoriamente una factura fiscal autorizada y persiste presentacion,
  resultado y resolucion contable manual. No existen columnas para PAN, CVV, token de navegador,
  CBU completo ni archivos de exportacion.
- `billing_charges.amount` sigue siendo el total compatible; `base_amount` es capital y la diferencia
  es recargo. `paid_amount <= amount` y el saldo se deriva como `amount - paid_amount`.
- La primera version de V24 nunca fue promovida; por eso el script fue corregido directamente y
  no requiere una migracion compensatoria.
- V1-V24 se reprodujeron en orden numerico sobre PostgreSQL 15 Alpine descartable el 2026-08-07.
  Un fixture creado luego de V18 con cargo de 100 e imputacion confirmada de 40 fue migrado por V22
  a `PARTIALLY_PAID`, `paid_amount=40`, saldo 60 y `fiscal_disposition=PENDING`; V24 agrego
  `collection_channel=REGULAR` y las tres tablas de debito. Se verifico `invoice_id NOT NULL`,
  `processing_date NOT NULL`, cero columnas PAN/CVV/CBU/token/export y cero tablas de exportacion.
  El contenedor descartable se elimino al terminar.

### Cambios de V25

- `tuition_enrollment_fee_policies` independiza importe, vencimiento y elegibilidad de debito
  de la matricula respecto del plan de cuotas. Un indice unico parcial permite una sola politica
  predeterminada.
- `tuition_applications` permite ciclo, nivel, curso y plan nulos hasta la asignacion y referencia
  la politica aplicada. Se agregan estados de nivelacion, asignacion y lista de espera.
- `tuition_placement_assessments` audita resultado, nivel recomendado, evaluador, notas y fecha.
- Las aplicaciones historicas conservan sus referencias y reciben una politica migrada desde
  el plan seleccionado. El 2026-08-08 se reconstruyo la base local desde cero y se reprodujeron
  V1-V25 en orden: quedaron 47 tablas, sin `tuition_seat_reservations`, sin columnas
  `requested_*` y con los ocho estados nuevos como unica restriccion valida. El arranque posterior
  con `ddl-auto=validate` y `/actuator/health` confirmo JPA, PostgreSQL y Redis en estado `UP`.

### Cambios de V26

- Normaliza `teaching_staff.photo_data` desde el tipo legacy `OID` a `BYTEA` cuando la columna
  no contiene fotos. La migracion es reejecutable si la columna ya esta en `BYTEA` y se detiene
  antes de modificar datos cuando detecta contenido legacy.
- QA deja de usar Hibernate como modificador de esquema: `render.yaml` y el fallback de
  `application-qa.yml` configuran `JPA_DDL_AUTO=validate`.

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
- `tuition_applications.assigned_level_id` -> `tuition_levels.id` (BIGINT, nullable hasta la asignacion)
- `tuition_applications.assigned_course_id` -> `courses.id` (BIGINT, nullable hasta la asignacion)
- `tuition_applications.enrollment_id` -> `enrollments.id` (BIGINT, nullable)
- `tuition_discounts.student_id` -> `students.id` (BIGINT, nullable)
- `tuition_ledger_entries.student_id` -> `students.id` (BIGINT, nullable)
- `billing_accounts.guardian_user_id` -> `users.id` (BIGINT, unico, `ON DELETE RESTRICT`)
- `billing_profiles.account_id` -> `billing_accounts.id` (BIGINT, unico, `ON DELETE RESTRICT`)
- `billing_profiles.updated_by` -> `users.id` (BIGINT, nullable, `ON DELETE RESTRICT`)
- `billing_charges.account_id` -> `billing_accounts.id` (BIGINT, `ON DELETE RESTRICT`)
- `billing_charges.student_id` -> `students.id` (BIGINT, nullable)
- `payment_allocations.payment_id` -> `payments.id` (BIGINT, `ON DELETE RESTRICT`)
- `payment_allocations.charge_id` -> `billing_charges.id` (BIGINT, no unico, `ON DELETE RESTRICT`);
  la unicidad vigente es `(payment_id, charge_id)` para permitir varios pagos por cargo.
- `billing_runs.requested_by` -> `users.id` (BIGINT, `ON DELETE RESTRICT`)
- `billing_run_items.run_id` -> `billing_runs.id` (BIGINT, `ON DELETE RESTRICT`)
- `billing_run_items.charge_id` -> `billing_charges.id` (BIGINT, `ON DELETE RESTRICT`)
- `billing_run_items.invoice_id` -> `fiscal_invoices.id` (BIGINT, unico, `ON DELETE RESTRICT`)
- `teaching_staff.linked_user_id` -> `users.id` (BIGINT, `ON DELETE SET NULL`)
- `course_attendance.course_session_id` -> `course_sessions.id` (BIGINT, `ON DELETE RESTRICT`)
- `payment_receipts.payment_id` -> `payments.id` (BIGINT, unico, `ON DELETE RESTRICT`)
- `fiscal_invoices.payment_id` -> `payments.id` (BIGINT, unico y nullable, `ON DELETE RESTRICT`)
- `fiscal_invoices.charge_id` -> `billing_charges.id` (BIGINT, unico y nullable, `ON DELETE RESTRICT`)
- `fiscal_invoice_attempts.invoice_id` -> `fiscal_invoices.id` (BIGINT, `ON DELETE RESTRICT`)
- `billing_outbox.invoice_id` -> `fiscal_invoices.id` (BIGINT, `ON DELETE RESTRICT`)
- `fiscal_invoice_vat_subtotals.invoice_id` -> `fiscal_invoices.id` (BIGINT, `ON DELETE RESTRICT`)
- `fiscal_invoice_taxes.invoice_id` -> `fiscal_invoices.id` (BIGINT, `ON DELETE RESTRICT`)

### Calificaciones de idiomas (V21)

- `exam_submissions.reading_score`, `writing_score` y `listening_score` son enteros nullable con
  restriccion `0..100`; permiten guardar progreso parcial sin inventar una nota final.
- `exam_submissions.score` conserva la nota final calculada y las notas legacy previas a V21.
- `exam_grade_history` conserva los valores anterior/nuevo de cada categoria y permite
  `new_score` nullable cuando la nueva carga queda incompleta.
- `exam_submissions.version` es el control de concurrencia optimista usado por el guardado en lote.
- `scripts/migrations/V21__add_language_skill_exam_grades.sql` es una migracion manual e idempotente;
  debe aplicarse en la base del ambiente antes de desplegar el backend que lee estas columnas.

### FK de scheduling (V12)
- `schedule_slots.classroom_id` -> `classrooms.id` (`ON DELETE RESTRICT`)
- `reservations.slot_id` -> `schedule_slots.id` (`ON DELETE RESTRICT`)

## 3) Cambios recientes relevantes (V10 a V18)

| Version | Archivo | Resultado |
|---|---|---|
| V10 | `scripts/migrations/V10__auth_registration_approval_workflow.sql` | `users.status` + tabla `registration_requests` para aprobacion de registro |
| V11 | `scripts/migrations/V11__extend_users_profile_fields.sql` | nuevos campos de perfil en `users` |
| V12 | `scripts/migrations/V12__create_scheduling_module.sql` | nuevo esquema de scheduling (`classrooms`, `schedule_slots`, `reservations`) y drop de `course_schedules` |
| V13 | `scripts/migrations/V13__create_tuition_module.sql` | nuevo esquema tuition para ciclo lectivo, niveles, planes, solicitudes, reservas y ledger |
| V14 | `scripts/migrations/V14__fix_first_manual_flow.sql` | compatibilidad del primer flujo manual |
| V15 | `scripts/migrations/V15__repair_legacy_test_password_hash.sql` | reparacion acotada de hashes legacy de prueba |
| V16 | `scripts/migrations/V16__create_billing_persistence.sql` | pagos compatibles, recibos X, facturas, intentos, outbox y secuencias |
| V17 | `scripts/migrations/V17__add_fiscal_tax_breakdown.sql` | domicilio receptor y detalle IVA/tributos |
| V18 | `scripts/migrations/V18__create_billing_accounts_charges_and_runs.sql` | cuentas, perfiles, cargos, imputaciones y ejecuciones manuales |
| V19 | `scripts/migrations/V19__repair_tuition_ledger_statuses.sql` | normalizacion de estados heredados del ledger de matriculacion |
| V20 | `scripts/migrations/V20__repair_hibernate_tuition_ledger_status_constraint.sql` | reemplazo del `CHECK` legacy generado por Hibernate y correccion del valor por defecto |
| V25 | `scripts/migrations/V25__separate_tuition_request_placement_and_assignment.sql` | politica de matricula, nivelacion y referencias academicas opcionales hasta la asignacion |
| V26 | `scripts/migrations/V26__convert_teaching_staff_photo_to_bytea.sql` | normalizacion segura de la foto docente legacy desde `OID` a `BYTEA` |

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

### Alcance de V18 (2026-07-28)

- Agrega `billing_accounts`, `billing_profiles`, `billing_charges`, `payment_allocations`,
  `billing_runs` y `billing_run_items`.
- Permite que `fiscal_invoices` se origine en un pago legacy o en un cargo, exactamente uno.
- Hace `payments.student_id` nullable para cobrar la matricula antes de crear el estudiante.
- Migra el ledger `MOCK_PENDING/MOCK_PAID` a `PENDING/PAID` y renombra
  `mock_reference` a `billing_reference`.
- Fija `rg_5866_applicable=false` mediante constraint para el primer cliente.
- V18 se valido y reaplico sobre PostgreSQL 15 Alpine descartable sin tocar la base del proyecto.
  Preservo filas legacy, migro estado/referencia, creo seis tablas y mantuvo los constraints.

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
- `POST /api/v1/tuition/applications/{id}/enrollment-charge` aplica una politica independiente y
  crea el ledger/cargo de matricula idempotente.
- `POST /api/v1/billing/charges/{id}/payments` crea/imputa el pago y recibo X; el observer de
  tuition marca el ledger `PAID`, crea el estudiante pendiente de nivelacion y bloquea la
  asignacion si el pago se revierte.
- `PUT /api/v1/tuition/applications/{id}/placement` registra la entrevista o su dispensa.
- `PUT /api/v1/tuition/applications/{id}/assignment` valida cupo y progresion, crea `Enrollment`
  y materializa ledger/cargos de cuotas.

## 7) Scripts operativos de validacion

- `scripts/migrations/V12__create_scheduling_module.sql` -> migracion de scheduling.
- `scripts/migrations/V13__create_tuition_module.sql` -> migracion de tuition.
- `scripts/migrations/V16__create_billing_persistence.sql` -> migracion de pagos/facturacion.
- `scripts/migrations/V17__add_fiscal_tax_breakdown.sql` -> domicilio y desglose impositivo.
- `scripts/migrations/V18__create_billing_accounts_charges_and_runs.sql` -> cuentas, perfiles,
  cargos, imputaciones y ejecuciones manuales de facturas.
- `scripts/migrations/V25__separate_tuition_request_placement_and_assignment.sql` -> separacion
  de solicitud, politica de matricula, nivelacion y asignacion academica; elimina la reserva legacy.
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

5. **Aplicacion de V14/V15/V16/V17/V18**:
   - Los scripts estan versionados como artefactos operativos. Validarlos primero en una
     base descartable o en una transaccion revertida; el contenedor local existente no debe
     modificarse automaticamente durante el desarrollo.

    - V16/V17/V18 ya fueron validadas en una base descartable; aun deben incorporarse al
      procedimiento controlado de despliegue de cada ambiente.

    - En bases legacy con filas existentes, V16 agrega `currency` y `version` como columnas
      rellenables, aplica `ARS`/`0` y recien despues las fija como `NOT NULL`. El mapeo JPA conserva
      esos defaults como salvaguarda adicional cuando `ddl-auto=update` esta activo en `dev`.

6. **Ledger y cargos**:
   - Las cuotas mensuales se generan desde el mes de asignacion, sin deuda retroactiva, y se
     limitan por inicio/fin del plan, inicio/fin del ciclo y `installments`. Si el vencimiento
     nominal del primer mes ya paso, esa primera cuota vence en la fecha de asignacion.
     No se reescriben filas historicas y este cambio no requiere una migracion de esquema.

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




