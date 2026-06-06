# SiGEP Backend

Backend REST para SiGEP, un sistema de gestion academica privada orientado a institutos de ensenanza de ingles. La API provee autenticacion, gestion academica, administracion operativa y contratos de integracion para el frontend Angular de SiGEP.

El proyecto esta implementado como monolito modular en Kotlin y Spring Boot, con submodulos Gradle por dominio para facilitar una futura extraccion a microservicios.

## Estado Actual

Estado del workspace inspeccionado: branch `feature008-courses-flow`, con cambios locales sin commit y archivos nuevos en desarrollo. La documentacion refleja el estado del checkout, incluyendo modulos no trackeados como `scheduling/src`.

### Funcional

- Seguridad JWT, roles, aprobacion administrativa de registros y catalogo admin de usuarios.
- Estudiantes, busqueda, foto de perfil, relacion con guardianes e historial academico via inscripciones.
- Cursos, inscripciones, sesiones, asistencia, materiales y certificados.
- Staff docente y no docente, asistencia de personal y resolucion batch de docentes.
- Examenes presenciales, submissions, calificaciones, historial de cambios y analitica de docentes.
- Scheduling operativo para aulas, slots y reservas.
- Redis cache, Actuator, Prometheus y Swagger/OpenAPI.

### En Desarrollo o Planificado

- Pagos/facturacion: existe entidad base `Payment`, pero no hay API funcional completa. `GET /api/v1/students/{id}/payment-status` devuelve datos temporales/mock.
- Comunicaciones/notificaciones: existe entidad base `Notification`; falta flujo real de envio SMTP/in-app.
- Reportes avanzados: modulo incluido, pendiente de implementacion funcional.
- Migraciones formales: hay scripts SQL y migraciones parciales; el perfil dev usa `ddl-auto: update`.

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
payments/        Pagos/facturacion planificado
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
- Rate limit: 100 requests por minuto por cliente
- Cache Redis TTL: 10 minutos

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

## Documentacion Relacionada

- [API_CONTRACT.md](API_CONTRACT.md): contrato para frontend.
- [AGENTS.md](AGENTS.md): instrucciones operativas para agentes.
- [AGENT_CONTEXT.md](AGENT_CONTEXT.md): contexto detallado de arquitectura, patrones y buenas practicas.
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
- No implementar features sobre `payments`, `communications` o `reports` sin definir primero interfaces y contratos.
- Mantener endpoints nuevos bajo `/api/v1`.
- Documentar cualquier diferencia real del contrato, especialmente wrappers de respuesta y parametros de paginacion.
- Agregar tests unitarios de servicios cuando se modifique logica de negocio.

## Roadmap

- Completar modulo de pagos/facturacion para cuotas, comprobantes, estado de deuda e integracion con estudiantes.
- Activar comunicaciones para aprobacion/rechazo de registros, avisos academicos y notificaciones operativas.
- Consolidar reportes administrativos y academicos.
- Normalizar `exams` para devolver wrappers consistentes.
- Reducir dependencias directas entre modulos mediante providers de `common`.
- Migrar de `ddl-auto: update` a migraciones versionadas validadas en ambientes productivos.

## Soporte

- Swagger local: `http://localhost:8080/swagger-ui.html`
- Aplicacion frontend esperada: Angular en `http://localhost:4200`
- Proyecto privado: SiGEP, Sistema de Gestion de Ensenanza Privada

Ultima actualizacion documental: 2026-06-05.
