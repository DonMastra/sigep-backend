# SiGEP Backend - Quickstart

Guia de arranque local y validacion del primer flujo manual QA (actualizada 2026-07-28).

## Prerequisitos

- JDK 17 o superior.
- Docker y Docker Compose.
- Git.

## Arranque local

Desde la raiz del backend:

```powershell
docker-compose up -d
docker-compose ps
gradlew.bat :application:bootRun --args="--spring.profiles.active=dev"
```

Servicios esperados:

- PostgreSQL: `localhost:5432` (`sigep_db`, usuario `sigep_user`).
- Redis: `localhost:6379`.
- API: `http://localhost:8080`.
- Swagger: `http://localhost:8080/swagger-ui.html`.
- OpenAPI: `http://localhost:8080/v3/api-docs`.
- Health: `http://localhost:8080/actuator/health`.

El frontend local se ejecuta en `http://localhost:4200`, usa `environment.ts` con
`apiUrl=/api/v1` y el proxy `proxy.conf.cjs`.

## Build y tests

```powershell
gradlew.bat clean build
gradlew.bat :security:test
gradlew.bat :staff:test
gradlew.bat :courses:test
gradlew.bat :students:test
gradlew.bat :tuition:test
```

Para ejecutar el JAR:

```powershell
gradlew.bat :application:bootJar
java -jar application\build\libs\sigep-backend.jar
```

## Flujo manual QA (pasos 1-9)

Antes de iniciar, carga por API admin un ciclo `OPEN`, niveles activos con `courseLevel`,
planes vigentes y un curso con docente y reserva.

1. Crear docente con `username` e `initialPassword`; comprobar login con la cuenta `TEACHER`.
2. Crear curso con codigo de 1-50 caracteres permitidos, docente y reserva; publicarlo sin
   exigir una cantidad minima de alumnos.
3. Como `GUARDIAN`, leer catalogos y seleccionar ciclo, nivel, curso y plan.
4. Crear solicitud y reservar vacante. Como `ADMIN`, abrir facturacion, completar el perfil
   fiscal si esta incompleto y registrar el pago del cargo de matricula.
5. Aprobar como `ADMIN`; comprobar estudiante, `currentLevel`, enrollment y contadores
   `enrolledStudents` (activos) / `totalEnrollments` (total).
6. Crear/editar sesiones en `/api/v1/sessions` y verificar conflictos.
7. Registrar asistencia con el body `{courseSessionId, date, records}`; la fecha debe ser la
   fecha de la sesion y el envio repetido actualiza la misma combinacion alumno/sesion.
8. Revisar nombres de estudiante, presentes/tardanzas y justificadas/licencias en estadisticas.
9. Revisar el ledger y los cargos: matricula y cuotas vencen de enero a diciembre del mismo
   ciclo. Previsualizar facturas de forma individual, seleccionada o filtrada; la preparacion
   no autoriza automaticamente en ARCA.

## Diagnostico rapido

- `401 Invalid credentials`: la cuenta debe estar `ACTIVE` y la contraseña debe coincidir con
  el hash BCrypt almacenado. V15 solo repara el hash legacy de los usuarios de prueba conocidos.
- `400 Requested tuition level is not mapped...`: completar `tuition_levels.course_level`;
  se mantienen `A1 -> BEGINNER` y `A2 -> ELEMENTARY` como compatibilidad.
- `400 Course is not open for tuition enrollment`: el curso necesita estado publicado/activo,
  docente, reserva y cupo disponible.
- `400 Attendance date must match...`: enviar `date` igual a la fecha de `courseSessionId`.
- `403`: `GUARDIAN` puede leer catalogos de tuition, pero solo `ADMIN` puede crearlos o editarlos.
- `Failed to fetch` en navegador local: revisar que el backend escuche en 8080 y que Angular
  use el proxy; no diagnosticarlo como CORS de Render.

## Base de datos y migraciones

```powershell
docker exec -it sigep-postgres psql -U sigep_user -d sigep_db
docker exec -t sigep-postgres pg_dump -U sigep_user sigep_db > backup.sql
```

`scripts/migrations/V14__fix_first_manual_flow.sql` agrega vinculo/foto docente, docente
nullable, codigo case-insensitive, `course_level`, progresiones y asistencia por sesion.
`V15__repair_legacy_test_password_hash.sql` corrige solo el hash BCrypt legacy conocido.

Validar ambas migraciones en una base descartable o dentro de una transaccion revertida antes
de aplicarlas al contenedor actual. No se ejecutan automaticamente durante esta implementacion.

## Infraestructura auxiliar

`docker-compose.yml` tambien levanta pgAdmin (`localhost:5050`) y Redis Commander
(`localhost:8081`). Los logs se consultan con `docker-compose logs -f`.
