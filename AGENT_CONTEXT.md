# AGENT_CONTEXT.md - Contexto para Agentes SiGEP Backend

## Objetivo del Sistema

SiGEP Backend es la API REST que provee datos y reglas de negocio a la aplicacion web Angular SiGEP. El dominio es un instituto privado de ensenanza de ingles que necesita gestionar estudiantes, guardianes, cursos, inscripciones, asistencia, materiales, certificados, personal, examenes, horarios y, en el roadmap, pagos/facturacion, comunicaciones y reportes.

El objetivo tecnico es mantener un monolito modular con limites de dominio claros, preparado para una futura extraccion a microservicios sin romper contratos frontend.

## Orientacion de Producto

La API debe soportar una gestion academica privada:

- Administracion de usuarios, docentes, guardianes y estudiantes.
- Oferta academica con cursos publicados y cursos administrados.
- Inscripcion y seguimiento academico de estudiantes.
- Asistencia a clases y sesiones.
- Gestion de materiales por curso.
- Emision y verificacion de certificados.
- Evaluaciones presenciales, notas e historial de calificaciones.
- Gestion operativa de aulas, horarios y reservas.
- Gestion de personal docente/no docente.
- Facturacion/pagos planificados para cuotas, comprobantes y estado de deuda.
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
payments/        dominio facturacion/pagos planificado
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

## API y Contrato Frontend

La API esta bajo `/api/v1`. El frontend Angular deberia:

- Usar un interceptor para JWT.
- Usar modelos genericos `ApiResponse<T>` y `PageResponse<T>`.
- Tener adaptadores para endpoints de `exams` que devuelven DTOs directos.
- Manejar `ErrorResponse` centralmente.
- Tratar imagenes de estudiantes como `Blob`.
- Usar `limit` como paginacion principal salvo endpoints con `size`.
- No asumir pagos/facturacion como completo.

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

### Staff

Responsabilidades:

- Personal docente.
- Personal no docente.
- Asistencia laboral.
- Resolucion de docentes para otros modulos.

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

### Payments/Billing

Estado: planificado. Se espera que cubra:

- Cuentas corrientes de estudiantes.
- Cuotas.
- Pagos.
- Comprobantes.
- Metodos de pago.
- Estado de deuda.
- Integracion con dashboards y notificaciones.

No implementar consumo real sin contratos definidos.

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

