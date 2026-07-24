# SiGEP Backend

Backend REST para SiGEP, un sistema de gestion academica privada orientado a institutos de ensenanza de ingles. La API provee autenticacion, gestion academica, administracion operativa y contratos de integracion para el frontend Angular de SiGEP.

El proyecto esta implementado como monolito modular en Kotlin y Spring Boot, con submodulos Gradle por dominio para facilitar una futura extraccion a microservicios.

## Estado Actual

Estado del workspace inspeccionado: branch `feature010-billing-flow`, basada exactamente en
`develop`, con archivos locales de QA que no deben incluirse automaticamente en commits.
La documentacion refleja el nucleo persistente de facturacion validado al 2026-07-21.

### Funcional

- Seguridad JWT, roles, aprobacion administrativa de registros y catalogo admin de usuarios.
- Estudiantes, busqueda, foto de perfil, relacion con guardianes e historial academico via inscripciones.
- Cursos, inscripciones, sesiones, asistencia, materiales y certificados.
- Staff docente y no docente, asistencia de personal y resolucion batch de docentes.
- Examenes presenciales, submissions, calificaciones, historial de cambios y analitica de docentes.
- Scheduling operativo para aulas, slots y reservas.
- Flujo QA de matriculacion para `GUARDIAN` y `ADMIN`: catalogos, cursos publicados, reserva de
  vacante, pago mock, aprobacion y ledger de enero a diciembre del ciclo.
- Alta/edicion de docentes con cuenta `TEACHER` enlazada, asignacion exacta de cursos y foto
  multipart persistida en PostgreSQL.
- Asistencia por sesion real con nombres de estudiantes y payload bulk idempotente.
- Redis cache, Actuator, Prometheus y Swagger/OpenAPI.

### En Desarrollo o Planificado

- Pagos/facturacion: persiste pagos, recibos X, facturas, intentos, outbox y secuencias; ofrece
  un flujo idempotente y una bandeja `ADMIN`. Incluye mock embebido, provider SOAP
  `mock-service` y cliente WSAA/WSFEv1 con firma CMS, caches, circuit breaker, bulkhead,
  metricas, CAE/consulta, parametricas, detalle IVA/tributos y PDFs con QR. El smoke SOAP local
  paso; falta homologacion ARCA con credenciales, deuda/cuotas y configuracion fiscal validada.
  `GET /api/v1/students/{id}/payment-status` sigue devolviendo datos temporales/mock.
- Comunicaciones/notificaciones: existe entidad base `Notification`; falta flujo real de envio SMTP/in-app.
- Reportes avanzados: modulo incluido, pendiente de implementacion funcional.
- Migraciones formales: hay scripts SQL y migraciones parciales; el perfil dev usa `ddl-auto: update`.

El flujo academico anterior es estable para QA. El nucleo de pagos/facturacion es un incremento
en desarrollo y todavia no emite comprobantes ARCA reales; comunicaciones y reportes siguen
fuera de alcance. `tuition_ledger_entries` es una vista mock operativa, no un comprobante fiscal.

## Stack

- Kotlin 1.9.25
- Java 17
- Spring Boot 3.5.6
- Spring Web, Security, Data JPA, Validation, Redis, Cache, Actuator
- PostgreSQL 15
- Redis 7
- JWT con `jjwt` 0.12.3
- Bucket4j para rate limiting
- OpenAPI/Swagger con Springdoc
- Gradle Kotlin DSL
- JUnit 5 y MockK para tests

## Arquitectura

```text
common/          DTOs compartidos, excepciones, base DDD, providers cross-module
security/        JWT, Spring Security, roles, registro/aprobacion, usuarios
students/        Estudiantes, guardianes, fotos, estado de pago temporal
courses/         Cursos, inscripciones, sesiones, asistencia, materiales, certificados
staff/           Docentes, no docentes, asistencia de personal
exams/           Examenes, submissions, calificaciones, performance docente
scheduling/      Aulas, slots horarios, reservas y asignaciones
payments/        Pagos/facturacion: persistencia, casos de uso, outbox y frontera fiscal
communications/  Notificaciones planificadas
reports/         Reportes planificados
application/     Entry point, configuracion OpenAPI, Redis, cache, import de modulos
```

Cada modulo sigue una organizacion DDD por capas:

```text
domain/model/            Entidades JPA y enums
domain/repository/       Repositorios Spring Data
application/dto/         Request/response DTOs
application/service/     Casos de uso
infrastructure/config/   ComponentScan, EntityScan, repositorios por modulo
presentation/controller/ Controladores REST
```

Convencion de paquetes: `com.sigep.{module}.{layer}`.

## Integracion Entre Modulos

La regla objetivo es que los modulos se comuniquen por interfaces declaradas en `common`, implementadas por el modulo dueno de los datos y consumidas por otros modulos mediante inyeccion Spring.

Ejemplos actuales:

- `EnrollmentServiceProvider`: declarado en `common`, implementado en `courses`, consumido por `students`.
- `TeacherInfoProvider`: declarado en `common`, implementado en `staff`, usado por flujos que necesitan resolver docentes.
- `SchedulingTargetValidationProvider`, `ReservationInfoProvider` y `ReservationAssignmentProvider`: integran scheduling con cursos/sesiones sin acoplar `scheduling` directamente a esos modulos.

Nota de estado: `staff` y `exams` aun tienen dependencias directas con `courses` y `students` en Gradle. Documentar y preservar ese estado hasta que se refactorice con providers.

## API

- Base local: `http://localhost:8080`
- Base QA Render: `https://sigep-backend-qa.onrender.com`
- Prefijo: `/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Contrato frontend: [API_CONTRACT.md](API_CONTRACT.md)

La mayoria de endpoints devuelven `ApiResponse<T>`:

```json
{
  "success": true,
  "data": {},
  "message": "Operation successful",
  "timestamp": "2026-06-05T10:00:00Z"
}
```

Paginacion comun:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0
}
```

Advertencia: algunos controladores del modulo `exams` devuelven DTOs o `PageResponse<T>` directamente, sin wrapper `ApiResponse<T>`. Esta es una diferencia actual del contrato y debe tenerse en cuenta en el frontend.

## Seguridad

Roles principales:

- `ADMIN`
- `TEACHER`
- `GUARDIAN`

Flujo de autenticacion:

1. `POST /api/v1/auth/register` crea cuentas publicas `TEACHER` o `GUARDIAN` en estado `PENDING_APPROVAL`.
2. Un admin aprueba o rechaza desde `/api/v1/admin/registration-requests`.
3. `POST /api/v1/auth/login` solo permite cuentas `ACTIVE`.
4. El frontend envia `Authorization: Bearer {token}` en endpoints protegidos.
5. `POST /api/v1/auth/refresh-token` renueva access tokens.

Usuarios dev creados por el perfil `dev`:

| Usuario | Password | Rol |
|---|---|---|
| `admin` | `password123` | `ADMIN` |
| `teacher` | `password123` | `TEACHER` |
| `guardian` | `password123` | `GUARDIAN` |

## Configuracion

Archivos principales:

- `application/src/main/resources/application.yml`
- `application/src/main/resources/application-dev.yml`
- `application/src/main/resources/application-prod.yml`

Valores relevantes:

- PostgreSQL: `jdbc:postgresql://localhost:5432/sigep_db`
- Redis: `localhost:6379`
- Timezone Jackson: `America/Argentina/Buenos_Aires`
- CORS: `http://localhost:4200`, `https://sigep.edu.mx`
- QA CORS: `https://sigep-ui-xi.vercel.app`, `https://sigep-qa.vercel.app` y el patron
  `https://*.vercel.app` (ver `render.yaml`).
- Rate limit: 100 requests por minuto por cliente
- Cache Redis TTL: 10 minutos

Facturacion:

- `BILLING_FISCAL_PROVIDER=mock` habilita el simulador solo fuera de produccion.
- `BILLING_FISCAL_PROVIDER=mock-service` recorre WSAA/WSFE contra
  `BILLING_MOCK_SERVICE_BASE_URL` (default `http://localhost:8091`) sin certificado y tambien
  esta prohibido en produccion.
- `dev` usa `mock-service` por defecto; el adapter `mock` queda disponible para unitarias.
  QA/produccion quedan deshabilitados por defecto.
- `BILLING_ISSUER_CUIT` y `BILLING_ISSUER_POINT_OF_SALE` completan el preflight del emisor;
  sin ambos, la factura queda `DRAFT` y no se puede encolar.
- `BILLING_ISSUER_LEGAL_NAME`, `BILLING_ISSUER_BUSINESS_ADDRESS`,
  `BILLING_ISSUER_VAT_CONDITION`, `BILLING_ISSUER_GROSS_INCOME` y
  `BILLING_ISSUER_ACTIVITY_START` son obligatorios para descargar la factura PDF.
  En el perfil `dev` tienen valores locales explícitos de ambiente mock para que
  la descarga funcione sin configuración adicional; deben reemplazarse antes de
  homologación o producción.
- `BILLING_OUTBOX_POLL_DELAY_MS` controla el intervalo del worker (por defecto, 1000 ms).
- `BILLING_FISCAL_REFERENCE_DATA_CACHE_TTL`, `BILLING_FISCAL_REFERENCE_DATA_STALE_IF_ERROR`
  y las variables `BILLING_FISCAL_*` de resiliencia controlan cache, circuit breaker y
  bulkhead; ver `ARCA_HOMOLOGATION_RUNBOOK.md`.
- Para homologacion real: `BILLING_FISCAL_PROVIDER=arca`,
  `BILLING_ARCA_ENVIRONMENT=homologation`, `BILLING_ARCA_KEYSTORE_PATH`,
  `BILLING_ARCA_KEYSTORE_PASSWORD` y, opcionalmente, `BILLING_ARCA_KEYSTORE_ALIAS`.
- Los endpoints WSAA/WSFE y timeouts tienen variables independientes; ver
  `ARCA_HOMOLOGATION_RUNBOOK.md`. QA/produccion siguen `disabled` por defecto.

## Comandos

Windows:

```powershell
# Infraestructura local
docker-compose up -d

# Build completo
gradlew.bat clean build

# Ejecutar la aplicacion en dev
gradlew.bat :application:bootRun --args="--spring.profiles.active=dev"

# Tests por modulo
gradlew.bat :students:test
gradlew.bat :exams:test

# Empaquetar JAR
gradlew.bat :application:bootJar
java -jar application/build/libs/sigep-backend.jar
```

Linux/macOS:

```bash
docker-compose up -d
./gradlew clean build
./gradlew :application:bootRun --args='--spring.profiles.active=dev'
```

## Infraestructura Local

`docker-compose.yml` levanta:

- PostgreSQL
- Redis
- pgAdmin
- Redis Commander

Scripts utiles:

- `scripts/setup-database.sql`
- `scripts/seed-test-users.sql`
- `scripts/validate-db-schema.sql`
- `scripts/migrations/`

Migraciones del cierre QA:

- `V14__fix_first_manual_flow.sql`: vinculo/foto docente, docente nullable, codigo de curso
  case-insensitive, mapeo `course_level`, reglas de progresion y asistencia por sesion.
- `V15__repair_legacy_test_password_hash.sql`: reparacion acotada del hash BCrypt de datos
  legacy de prueba.
- `V16__create_billing_persistence.sql`: compatibilidad de `payments` legacy y tablas
  `payment_receipts`, `fiscal_invoices`, `fiscal_invoice_attempts`, `billing_outbox` y
  `voucher_sequences`.
- `V17__add_fiscal_tax_breakdown.sql`: domicilio fiscal del receptor y detalle ordenado de
  alicuotas IVA/tributos para WSFE.

Validarlas en una base descartable o transaccion revertida antes de aplicarlas al contenedor
actual; no se ejecutan automaticamente durante esta implementacion.

## Documentacion Relacionada

- [API_CONTRACT.md](API_CONTRACT.md): contrato para frontend.
- [AGENTS.md](AGENTS.md): instrucciones operativas para agentes.
- [AGENT_CONTEXT.md](AGENT_CONTEXT.md): contexto detallado de arquitectura, patrones y buenas practicas.
- [BILLING_ARCA_IMPLEMENTATION_GUIDE.md](BILLING_ARCA_IMPLEMENTATION_GUIDE.md): decisiones y contrato fiscal.
- [ARCA_HOMOLOGATION_RUNBOOK.md](ARCA_HOMOLOGATION_RUNBOOK.md): alta, secretos, configuracion y smoke test.
- [ARCHITECTURE.md](ARCHITECTURE.md): arquitectura historica del sistema.
- [DATABASE_STRUCTURE.md](DATABASE_STRUCTURE.md): estructura de base de datos.
- [security/SECURITY.md](security/SECURITY.md): seguridad.
- [security/AUTHENTICATION_GUIDE.md](security/AUTHENTICATION_GUIDE.md): guia de autenticacion.
- [courses/README.md](courses/README.md), [staff/README.md](staff/README.md), [exams/README.md](exams/README.md): documentacion por modulo.

## Buenas Practicas del Proyecto

- Mantener controladores delgados y casos de uso en `application/service`.
- Usar DTOs tipados para todo contrato REST.
- Centralizar excepciones en `common`.
- Usar providers en `common` para integracion entre dominios.
- Extender `payments` a traves de `FiscalAuthorityPort` y sus casos de uso; no filtrar SOAP,
  certificados ni secretos hacia controladores o Angular.
- Mantener endpoints nuevos bajo `/api/v1`.
- Documentar cualquier diferencia real del contrato, especialmente wrappers de respuesta y parametros de paginacion.
- Agregar tests unitarios de servicios cuando se modifique logica de negocio.

## Roadmap

- Ejecutar WSAA/WSFEv1 con credenciales de homologacion; agregar cuotas, estado de deuda e
  integracion con estudiantes.
- Activar comunicaciones para aprobacion/rechazo de registros, avisos academicos y notificaciones operativas.
- Consolidar reportes administrativos y academicos.
- Normalizar `exams` para devolver wrappers consistentes.
- Reducir dependencias directas entre modulos mediante providers de `common`.
- Migrar de `ddl-auto: update` a migraciones versionadas validadas en ambientes productivos.

## Soporte

- Swagger local: `http://localhost:8080/swagger-ui.html`
- Aplicacion frontend esperada: Angular en `http://localhost:4200`
- Proyecto privado: SiGEP, Sistema de Gestion de Ensenanza Privada

Ultima actualizacion documental: 2026-07-21.
