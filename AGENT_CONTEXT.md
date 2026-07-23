# AGENT_CONTEXT.md - Contexto para Agentes SiGEP Backend

> **Estado QA (2026-07-21).** Facturacion ya tiene persistencia, casos de uso idempotentes,
> outbox, mock embebido/externo, cliente WSAA/WSFEv1, resiliencia, metricas, parametricas,
> detalle impositivo y PDFs; el flujo QA previo se mantiene estable. Este documento complementa las secciones historicas
> y describe el contrato operativo que debe respetarse al cerrar el primer flujo
> manual. Ante una discrepancia, prevalecen las entidades/DTOs y los controladores
> actuales, seguidos por `API_CONTRACT.md` y las migraciones versionadas.

## Objetivo del Sistema

SiGEP Backend es la API REST que provee datos y reglas de negocio a la aplicacion web Angular SiGEP. El dominio es un instituto privado de ensenanza de ingles que necesita gestionar estudiantes, guardianes, cursos, matriculacion, inscripciones, asistencia, materiales, certificados, personal, examenes, horarios y, en el roadmap, pagos/facturacion, comunicaciones y reportes.

El objetivo tecnico es mantener un monolito modular con limites de dominio claros, preparado para una futura extraccion a microservicios sin romper contratos frontend.

## Orientacion de Producto

La API debe soportar una gestion academica privada:

- Administracion de usuarios, docentes, guardianes y estudiantes.
- Oferta academica con cursos publicados y cursos administrados.
- Matriculacion como proceso: solicitud, reserva de vacante, pago inicial mock y aprobacion administrativa.
- Inscripcion y seguimiento academico de estudiantes.
- Asistencia a clases y sesiones.
- Gestion de materiales por curso.
- Emision y verificacion de certificados.
- Evaluaciones presenciales, notas e historial de calificaciones.
- Gestion operativa de aulas, horarios y reservas.
- Gestion de personal docente/no docente.
- Facturacion/pagos en desarrollo para pagos, recibos X, comprobantes y estado de deuda.
- Comunicaciones y reportes planificados para flujos administrativos.

## Arquitectura

El repo es un monolito modular Gradle:

```text
common/          base compartida y contratos cross-module
security/        seguridad transversal
students/        dominio estudiantes
courses/         dominio cursos e inscripciones
staff/           dominio personal
exams/           dominio examenes
scheduling/      dominio horarios/reservas
payments/        dominio facturacion/pagos con persistencia, outbox y frontera fiscal
tuition/         dominio matriculacion y ledger mock inicial
communications/  dominio notificaciones planificado
reports/         dominio reportes planificado
application/     aplicacion Spring Boot que importa modulos
```

Cada modulo implementado usa capas:

- `domain/model`: entidades y enums.
- `domain/repository`: repositorios JPA.
- `application/dto`: contratos de entrada/salida.
- `application/service`: casos de uso.
- `infrastructure/config`: scans y configuracion del modulo.
- `presentation/controller`: REST.

## Principios de Diseno

- Controladores delgados; logica en servicios.
- DTOs explicitos para contratos HTTP.
- Excepciones centralizadas desde `common`.
- Wrappers de respuesta consistentes cuando sea posible.
- Separacion por bounded contexts.
- Integracion cross-module mediante provider interfaces en `common`.
- Evitar que features planificadas se consuman como si fueran estables.
- Mantener compatibilidad con Angular: rutas estables, paginacion clara y errores tipados.

## Patrones Cross-Module

Patron recomendado:

1. Declarar interfaz en `common/application/service`.
2. Implementarla en el modulo dueno de los datos.
3. Consumir la interfaz desde otro modulo.
4. Evitar dependencia Gradle directa nueva entre bounded contexts.

Ejemplos:

- `EnrollmentServiceProvider`: `students` consulta informacion academica sin depender directamente de `courses`.
- `TeacherInfoProvider`: otros dominios resuelven datos docentes via interfaz.
- `ReservationInfoProvider` y `ReservationAssignmentProvider`: coordinan reservas sin acoplar scheduling a cursos/sesiones.
- `SchedulingTargetValidationProvider`: valida targets de reserva en el dominio correspondiente.
- `StudentProfileProvider`: `tuition` crea/valida estudiantes sin depender de `students`.
- `CourseEnrollmentCommandProvider`: `tuition` consulta cupos y crea `Enrollment` final sin depender de `courses`.
- `GuardianAccountProvider`: `tuition` activa guardianes al aprobar matriculacion sin depender de repositorios de `security`.

Estado real a considerar: `staff` y `exams` todavia dependen directamente de `courses` y `students`. No ampliar este patron; preferir providers nuevos.

## Seguridad

La seguridad se concentra en `security`:

- JWT access token y refresh token.
- `JwtAuthenticationFilter`.
- `SecurityConfig`.
- Roles `ADMIN`, `TEACHER`, `GUARDIAN`.
- Estado de cuenta `PENDING_APPROVAL`, `ACTIVE`, `REJECTED`.
- Aprobacion/rechazo administrativo de registros publicos.
- Rate limiting con Bucket4j.

Anotaciones recomendadas:

- `@RequireAdmin`
- `@RequireAdminOrTeacher`
- `@RequireGuardian`
- `@RequireStaffOrGuardian`

Los controladores que necesitan actor actual leen `userId` y `userRole` desde `HttpServletRequest`, seteados por el filtro JWT.

## Datos y Persistencia

- PostgreSQL es la fuente principal.
- Redis se usa para cache.
- Hibernate/JPA gestiona entidades.
- `ddl-auto: update` esta activo en dev; produccion deberia usar `validate` y migraciones.
- Scripts SQL viven en `scripts/` y `scripts/migrations/`.
- La mayoria de IDs son `Long`.
- `exams` usa `UUID` por razones historicas de migracion.
- `V14__fix_first_manual_flow.sql` agrega compatibilidad del flujo QA (vinculo y foto
  docente, docente nullable en cursos, codigo de curso case-insensitive, `course_level`,
  reglas de progresion y asistencia por sesion). `V15__repair_legacy_test_password_hash.sql`
  solo corrige el hash conocido de los usuarios de prueba legacy.
- `V16__create_billing_persistence.sql` amplia `payments` de forma compatible y crea recibos,
  facturas, intentos, outbox y secuencias de comprobantes.
- `V17__add_fiscal_tax_breakdown.sql` agrega domicilio del receptor y colecciones persistentes
  para `Iva` y `Tributos`.
- Las migraciones SQL se validan sobre una base descartable o dentro de una transaccion
  revertida; no se deben ejecutar automaticamente sobre el contenedor actual sin backup
  y aprobacion explicita.

## API y Contrato Frontend

La API esta bajo `/api/v1`. El frontend Angular deberia:

- Usar un interceptor para JWT.
- Usar modelos genericos `ApiResponse<T>` y `PageResponse<T>`.
- Tener adaptadores para endpoints de `exams` que devuelven DTOs directos.
- Manejar `ErrorResponse` centralmente.
- Tratar imagenes de estudiantes como `Blob`.
- Usar `limit` como paginacion principal salvo endpoints con `size`.
- El nucleo persistente de pagos/facturacion ya existe, pero no asumir emision ARCA real: el
  provider de dev es mock y `tuition` continua siendo un ledger independiente.
- Nunca consumir WSAA/WSFE desde Angular ni exponer secretos fiscales en respuestas.
- Las fechas JSON sin hora se serializan como `YYYY-MM-DD` y las horas de sesiones/reservas
  como `HH:mm`, sin conversion UTC.

Consultar `API_CONTRACT.md` antes de implementar pantallas.

## Modulos Funcionales

### Security

Responsabilidades:

- Login/register/refresh/logout.
- Perfil autenticado.
- Admin registration requests.
- Admin users catalog.
- Estados de cuenta.

### Students

Responsabilidades:

- Estudiantes.
- Busqueda.
- Relacion guardian-estudiante.
- Fotos.
- Alta por admin y self-registration por guardian.
- Estado de pago temporal.

### Courses

Responsabilidades:

- Cursos y catalogo publicado.
- Inscripciones.
- Sesiones.
- Asistencia.
- Materiales.
- Certificados.

Contrato QA: `CourseDto.teacherId` es nullable; `enrolledStudents` cuenta solo
inscripciones `ACTIVE` y `totalEnrollments` cuenta todas. Publicar exige estado valido,
docente asignado y reserva, pero no una matricula minima. El catalogo `GET /courses/published`
esta habilitado para `GUARDIAN`.

La asistencia masiva usa el contenedor `{ courseSessionId, date, records }`.
Cada registro se identifica por `enrollmentId + courseSessionId`, por lo que repetir
el envio actualiza la fila existente. La fecha debe coincidir con la sesion seleccionada;
`PRESENT` y `LATE` computan asistencia efectiva, mientras `JUSTIFIED` y licencias se
informan por separado. `studentName` se resuelve mediante `StudentProfileProvider`.

### Staff

Responsabilidades:

- Personal docente.
- Personal no docente.
- Asistencia laboral.
- Resolucion de docentes para otros modulos.

El alta de docente (`POST /staff/teaching`) crea en una transaccion una cuenta activa
`TEACHER` con BCrypt a partir de `username`/`initialPassword`, enlaza `linkedUserId` y
aplica `assignedCourseIds`. La edicion no acepta credenciales; puede cambiar datos
personales/laborales, vinculo y asignaciones. Las fotos se cargan por multipart y se
persisten en PostgreSQL.

### Exams

Responsabilidades:

- Examenes presenciales.
- Submissions.
- Calificaciones.
- Historial de cambios.
- Estadisticas.
- Performance docente.

Riesgo: respuesta HTTP no esta completamente normalizada con `ApiResponse`.

### Scheduling

Responsabilidades:

- Aulas.
- Slots por aula/dia/hora.
- Reservas disponibles/asignadas/inactivas.
- Asignacion a cursos o sesiones.

Este modulo usa providers para integrarse con dominios duenos de los targets.

### Tuition

Responsabilidades:

- Ciclos lectivos minimos para matriculacion.
- Niveles y progresiones.
- Planes de cuota y descuentos/becas.
- Solicitudes de matriculacion por guardian.
- Reserva temporal de vacante.
- Ledger mock para matricula inicial y cuotas mensuales.
- Aprobacion administrativa que confirma reserva, activa guardian si corresponde, crea estudiante nuevo y genera `Enrollment`.

Contrato QA adicional:

- `GUARDIAN` puede leer ciclos abiertos, niveles activos, planes vigentes y cursos publicados;
  la escritura de catalogos permanece exclusiva de `ADMIN`.
- `TuitionLevel.courseLevel` es el mapeo explicito al enum de cursos. Se mantienen los
  respaldos legacy `A1 -> BEGINNER` y `A2 -> ELEMENTARY` cuando el catalogo aun no tiene
  valor.
- `PASS_PREVIOUS_LEVEL` bloquea una progresion no aprobada; `ADMIN_APPROVAL` marca
  `requiresAdminOverride` y exige nota administrativa no vacia al aprobar.
- El ledger mock genera la matricula y cuotas de enero a diciembre del mismo ciclo lectivo
  (maximo 12 vencimientos), y nunca debe interpretarse como factura fiscal.

Limites de `tuition`:

- No factura ni emite comprobantes fiscales; esa responsabilidad pertenece a `payments`.
- No almacena datos de tarjeta ni procesa pagos reales.
- La cuenta `GUARDIAN` debe poder autenticarse para usar endpoints guardian; las cuentas `PENDING_APPROVAL` siguen sin login por regla global de auth.

### Payments/Billing

Estado: nucleo persistente implementado. `tuition` deja un ledger mock independiente y
`payments` aporta:

- `FiscalAuthorityPort` como frontera anti-corrupcion.
- Validacion de CUIT, receptor, fechas de servicio, importes y redondeo `HALF_EVEN`.
- `MockFiscalAuthorityAdapter` determinista con idempotencia, secuencia y resultado incierto.
- `mock-service` recorre el SOAP externo sin certificado; `arca` usa PKCS#12. Ambos proveedores
  remotos tienen cache de parametricas, circuit breaker, bulkhead y metricas de baja cardinalidad.
- Seleccion fail-closed: dev mock; QA/produccion disabled; ambos mocks prohibidos en produccion.
- `GET /api/v1/billing/provider/health`, exclusivo de `ADMIN` y sin secretos.
- Entidades y repositorios para pago, recibo X, factura, intentos, outbox y secuencia.
- Alta/confirmacion/factura idempotentes y `POST /api/v1/payments/register` como transaccion
  local de pocos clics.
- Bandeja, detalle, encolado `202 Accepted` y conciliacion de resultados `UNKNOWN`.
- Worker que serializa la numeracion por CUIT/punto de venta/tipo y no reintenta timeouts ambiguos.
- `ArcaFiscalAuthorityAdapter`: PKCS#12/CMS, WSAA, cache anticipada del TA, `FEDummy`,
  ultimo autorizado, `FECAESolicitar`, `FECompConsultar` y URL QR version 1.
- `ExternalMockFiscalAuthorityAdapterSmokeTest`: smoke opt-in de WSAA, catalogos, CAE y
  consulta contra `mock-billing-service`; se omite si `SIGEP_EXTERNAL_MOCK_SMOKE` no es `true`.

Siguiente alcance: ejecutar `ARCA_HOMOLOGATION_RUNBOOK.md` con credenciales reales y completar
la configuracion fiscal validada por la institucion.

Reglas:

- Angular nunca consume ARCA ni el mock externo directamente.
- Un timeout ambiguo no se reintenta: se consulta y concilia.
- No asumir condicion IVA, alicuota, tipo de comprobante ni aplicabilidad RG 3368.
- Las sumas de bases/IVA/tributos deben coincidir con los agregados; el preflight deja en
  `DRAFT` cualquier desglose inconsistente.
- Seguir `BILLING_ARCA_IMPLEMENTATION_GUIDE.md` antes de ampliar el modulo.

### Communications

Estado: planificado. Se espera que cubra:

- Notificaciones por aprobacion/rechazo.
- Avisos academicos.
- Avisos administrativos.
- SMTP y/o in-app.

### Reports

Estado: planificado. Se espera que cubra:

- Reportes academicos.
- Reportes operativos.
- Reportes financieros cuando billing exista.

## Buenas Practicas para Agentes

- Leer controladores y DTOs antes de actualizar contratos.
- No confiar solo en docs historicos: hay documentos antiguos con encoding roto y contenido duplicado.
- No revertir cambios del usuario.
- Mantener cambios de documentacion separados de cambios de codigo.
- Si se agrega endpoint, actualizar `API_CONTRACT.md`.
- Si se agrega integracion cross-module, preferir interfaz en `common`.
- Si se toca modulo planificado, dejar TODO explicito y contrato minimo.
- Si se modifica logica de negocio, agregar tests de servicio.
- Si se modifica seguridad, revisar roles y flujo JWT.
- Si se modifica scheduling, validar disponibilidad, asignacion, desasignacion y conflictos.
- Si se modifica tuition, validar estados, reserva de cupo, ledger mock, ownership de guardian y aprobacion admin.

## Skills Utiles para Agentes

Skills tecnicas que conviene aplicar en este proyecto:

- Exploracion de repositorio: usar `rg --files` y `rg` para encontrar controladores, DTOs y servicios.
- Auditoria de contrato API: comparar anotaciones Spring con `API_CONTRACT.md`.
- Refactor modular: identificar dependencias Gradle directas y reemplazar con providers en `common`.
- Seguridad Spring: revisar `SecurityConfig`, filtros JWT y anotaciones.
- Testing Kotlin/Spring: crear tests unitarios con JUnit 5 y MockK para servicios.
- Documentacion tecnica: mantener README, API contract y contexto de agentes sincronizados.
- Integracion frontend: pensar en interceptores, modelos TypeScript, paginacion, blobs y errores.

## Comandos de Trabajo

Windows:

```powershell
gradlew.bat projects
gradlew.bat :application:classes
gradlew.bat clean build
gradlew.bat :application:bootRun --args="--spring.profiles.active=dev"
```

Linux/macOS:

```bash
./gradlew projects
./gradlew :application:classes
./gradlew clean build
./gradlew :application:bootRun --args='--spring.profiles.active=dev'
```

Infraestructura:

```bash
docker-compose up -d
```

## Riesgos y Deuda Tecnica

- Mojibake historico en documentacion.
- `README.md` anterior estaba duplicado/desordenado.
- `exams` no usa wrapper uniforme en todos sus endpoints.
- `staff` y `exams` tienen dependencias directas que deberian reducirse.
- `payments`, `communications` y `reports` pueden inducir a error si se documentan como completos.
- `tuition` usa ledger mock; no tratarlo como facturacion real ni como integracion ARCA.
- Migraciones y `ddl-auto` deben consolidarse antes de produccion.
- Hay archivos nuevos/no trackeados en el workspace; no asumir que `git status` limpio.

## Criterios de Calidad

Una contribucion aceptable debe:

- Mantener compilacion.
- Mantener rutas bajo `/api/v1`.
- No romper contratos frontend sin documentarlo.
- Usar DTOs tipados.
- Centralizar errores.
- Respetar roles.
- Evitar acoplamiento nuevo entre modulos.
- Tener tests proporcionales al riesgo.
- Actualizar documentacion cuando cambie comportamiento visible.

