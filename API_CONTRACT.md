# API Contract - SiGEP Backend

Contrato REST para integracion del frontend Angular SiGEP con el backend. Este documento refleja el estado del workspace al 2026-08-18.

## Informacion General

- Base local: `http://localhost:8080`
- Base QA Render: `https://sigep-backend-qa.onrender.com`
- Base productiva esperada: `https://api.sigep.edu.mx`
- Prefijo API: `/api/v1`
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- Request/response JSON: `application/json`
- Fechas: ISO 8601. Para fechas sin hora se usa `YYYY-MM-DD`.
- Horas de scheduling: strings `HH:mm`.
- Timezone de negocio: `America/Argentina/Buenos_Aires`.
- Las fechas sin hora se interpretan literalmente como fecha local (`YYYY-MM-DD`), sin convertir a UTC.
- Las horas de reservas y sesiones usan `HH:mm`.
- QA CORS permite `https://sigep-ui-xi.vercel.app`, `https://sigep-qa.vercel.app` y el
  patron `https://*.vercel.app`; el preflight debe responder antes de probar el login.

## Cambios estabilizados del primer flujo manual

- La validacion de contrasena en login es obligatoria y compara el hash BCrypt almacenado.
- Los errores de autenticacion/autorizacion se exponen como `401`/`403` (`AuthorizationDeniedException`
  no debe terminar en `500`).
- Las lecturas idempotentes de sesiones pueden reintentarse una vez en el frontend cuando la red
  responde `status 0`; no cambia el contrato ni duplica escrituras.

## Autenticacion

La API usa JWT stateless. El frontend debe enviar:

```http
Authorization: Bearer {access_token}
```

Flujo esperado:

1. Registrar o crear usuario.
2. Si el registro es publico, esperar aprobacion administrativa.
3. Login para obtener `token` y `refreshToken`.
4. Enviar el access token en endpoints protegidos.
5. Ante expiracion, usar `POST /api/v1/auth/refresh-token`.
6. Si el refresh falla, limpiar sesion frontend y volver a login.
7. Si `user.mustChangePassword` es `true`, navegar a `/auth/change-password` y usar
   `PATCH /api/v1/users/me/password` antes de acceder a cualquier otra operacion protegida.

Mientras el cambio obligatorio esta pendiente, el backend responde `403` con codigo
`PASSWORD_CHANGE_REQUIRED` para los demas endpoints protegidos.

Roles:

- `ADMIN`
- `TEACHER`
- `GUARDIAN`

Estados de cuenta:

- `PENDING_APPROVAL`
- `ACTIVE`
- `REJECTED`

## Estructuras Compartidas

### ApiResponse

La mayoria de endpoints exitosos devuelve:

```ts
interface ApiResponse<T> {
  success: boolean;
  data?: T | null;
  message?: string | null;
  timestamp: string;
}
```

### PageResponse

```ts
interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
```

Cuando un endpoint pagina con wrapper comun, la forma usual es:

```ts
type PagedApiResponse<T> = ApiResponse<PageResponse<T>>;
```

### ErrorResponse

```ts
interface ErrorResponse {
  status: number;
  code: string;
  message: string;
  field?: string;
  details?: string;
  path: string;
  timestamp: string;
}
```

### Paginacion y Orden

Parametros frecuentes:

- `page`: numero de pagina, base 0.
- `limit`: tamano de pagina en la mayoria de modulos.
- `size`: tamano de pagina en endpoints legacy o en `exams`.
- `sort`: campo de orden.
- `order`: `ASC` o `DESC`.

Nota: algunos endpoints aceptan `limit` y `size`; si ambos existen, el backend suele preferir `limit`.

## Advertencias de Contrato

- `exams` y `exam-submissions` actualmente devuelven varios DTOs o `PageResponse<T>` directamente, sin `ApiResponse<T>`.
- `DELETE /api/v1/exams/{id}` devuelve `204 NO_CONTENT`; la mayoria de otros DELETE devuelve `200 OK` con wrapper.
- `payments` persiste pagos, recibos X, cargos, perfiles reutilizables, ejecuciones de
  preparacion y facturas; la autorizacion fiscal continua siendo individual y asincrona.
  `communications` y `reports` no exponen API funcional completa.
- `tuition` conserva su ledger academico y sincroniza cada deuda con un cargo de `payments`;
  no emite CAE ni almacena medios de pago.
- `GET /api/v1/students/{id}/payment-status` devuelve estado temporal/mock hasta completar facturacion/pagos.
- `GET /api/v1/students/{id}/photo` devuelve binario de imagen, no `ApiResponse`.

## Auth

Base: `/api/v1/auth`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| POST | `/login` | Publico | Inicia sesion si la cuenta esta `ACTIVE`. |
| POST | `/register` | Publico | Crea usuario `TEACHER` o `GUARDIAN` en `PENDING_APPROVAL`. |
| GET | `/registration-status?username=` | Publico | Consulta estado de cuenta para flujo pre-login. |
| POST | `/refresh-token` | Publico | Renueva token. |
| POST | `/logout` | Autenticado/cliente | Logout stateless; el cliente limpia tokens. |

### LoginRequest

```ts
interface LoginRequest {
  username: string;
  password: string;
}
```

### LoginResponse

```ts
interface LoginResponse {
  token: string;
  refreshToken: string;
  user: UserDto;
}
```

### RegisterRequest

```ts
interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  role: 'TEACHER' | 'GUARDIAN';
  phoneNumber?: string;
  address?: string;
  dateOfBirth?: string;
  documentNumber?: string;
  emergencyContact?: string;
}
```

### UserDto

```ts
interface UserDto {
  id: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  role: 'ADMIN' | 'TEACHER' | 'GUARDIAN';
  status: 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED';
  active: boolean;
  mustChangePassword: boolean;
}
```

## Perfil de usuario autenticado

Base: `/api/v1/users`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/me` | `ADMIN`, `TEACHER`, `GUARDIAN` | Obtiene el perfil autenticado. |
| PATCH | `/me/password` | `ADMIN`, `TEACHER`, `GUARDIAN` | Cambia la contrasena verificando primero la actual. |

El cambio de contrasena recibe:

```ts
interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string; // 12 a 100 caracteres
}
```

Errores de negocio especificos:

- `CURRENT_PASSWORD_INVALID`: la contrasena actual no coincide.
- `PASSWORD_UNCHANGED`: la nueva contrasena coincide con la actual.
- `PASSWORD_CHANGE_REQUIRED`: la cuenta debe reemplazar su clave temporal antes de continuar.

## Usuario Autenticado

Base: `/api/v1/users`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/me` | ADMIN, TEACHER, GUARDIAN | Perfil del usuario autenticado. |

## Administracion de Registros

Base: `/api/v1/admin/registration-requests`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/` | ADMIN | Lista solicitudes con filtros. |
| PUT | `/{requestId}/approve` | ADMIN | Aprueba solicitud y activa cuenta. |
| PUT | `/{requestId}/reject` | ADMIN | Rechaza solicitud. |

Query para listado:

- `status?: PENDING_APPROVAL | ACTIVE | REJECTED | ALL`
- `page?: number`
- `size?: number`
- `sort?: string` default `createdAt`
- `order?: ASC | DESC` default `DESC`

Decision request opcional:

```ts
interface RegistrationDecisionRequest {
  adminNotes?: string;
}
```

## Catalogo Admin de Usuarios

Base: `/api/v1/admin/users`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/` | ADMIN | Lista usuarios con filtros administrativos. |

Query:

- `role?: ADMIN | TEACHER | GUARDIAN | ALL`
- `status?: PENDING_APPROVAL | ACTIVE | REJECTED | ALL`
- `active?: boolean`
- `page?: number`
- `size?: number`
- `sort?: string` default `username`
- `order?: ASC | DESC` default `ASC`

## Students

Base: `/api/v1/students`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/` | ADMIN, TEACHER | Lista estudiantes paginados. |
| GET | `/{id}` | ADMIN, TEACHER, GUARDIAN | Detalle completo del estudiante. |
| GET | `/search?query=` | ADMIN, TEACHER | Busca por nombre, nombre completo, email, documento o matricula, con paginacion y orden. |
| GET | `/guardian/{guardianId}` | ADMIN, TEACHER, GUARDIAN | Estudiantes asociados a guardian. |
| POST | `/` | ADMIN | Crea estudiante. |
| POST | `/self-registration` | GUARDIAN | Crea estudiante vinculado al guardian autenticado. |
| POST | `/identity-match` | ADMIN, GUARDIAN | Detecta coincidencias antes de crear; para GUARDIAN no revela datos de un estudiante ajeno. |
| PUT | `/{id}` | ADMIN | Actualiza estudiante. |
| PUT | `/{id}/guardian` | ADMIN | Vincula o reasigna el unico tutor vigente; exige `guardianId` y `reason` y genera auditoria. |
| DELETE | `/{id}` | ADMIN | Elimina estudiante. |
| POST | `/{id}/photo` | ADMIN | Sube foto multipart con parte `file`. |
| GET | `/{id}/photo` | ADMIN, TEACHER, GUARDIAN | Descarga imagen. |
| GET | `/{id}/courses` | ADMIN, TEACHER, GUARDIAN | Respuesta de compatibilidad con redirect a historial. |
| GET | `/{id}/payment-status` | ADMIN, TEACHER, GUARDIAN | Estado temporal/mock de pagos. |

Parametros comunes de listado:

- `page?: number`
- `limit?: number`
- `sort?: id | lastName | firstName | studentNumber | email`
- `order?: ASC | DESC`

El listado y la busqueda aplican el orden en backend antes de paginar. Para `lastName` y
`firstName` se agregan criterios secundarios estables y `id` como desempate, evitando que un
alumno cambie de pagina entre consultas equivalentes.

### StudentDto

```ts
interface StudentDto {
  id: number;
  studentNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  documentType: 'DNI' | 'PASSPORT' | 'NATIONAL_ID' | 'NO_DOCUMENT' | 'IN_PROCESS';
  documentCountry: string;
  phoneNumber?: string;
  dateOfBirth?: string;
  address?: string;
  guardianName?: string;
  guardianPhone?: string;
  guardianEmail?: string;
  documentNumber?: string;
  currentCourseId?: number;
  currentCourseName?: string;
  currentCourses: EnrollmentSummaryDto[];
  currentLevel?: string;
  emergencyContact?: string;
  active: boolean;
  photoUrl?: string;
  createdAt?: string;
  updatedAt?: string;
}
```

`studentNumber` es el identificador de negocio inmutable del alumno. Para la migracion
legacy conserva `Matricula`; para altas nuevas el backend genera un valor `SIGEP-*`.
`currentCourses` contiene todas las inscripciones `ACTIVE`. Los campos singulares
`currentCourseId` y `currentCourseName` se conservan temporalmente por compatibilidad y
representan el primer curso activo en orden estable. Un alumno puede estar activo en varios
cursos distintos; solo se rechaza duplicar el mismo par alumno-curso.

La identidad documental se compara por `(documentCountry, documentType, normalizedDocumentNumber)`.
Para `AR + DNI` se eliminan separadores y se completa a 8 digitos; pasaporte y documento
nacional extranjero se comparan en mayusculas y sin separadores. `NO_DOCUMENT` e `IN_PROCESS`
no llevan numero ni participan de la unicidad documental. El email de un estudiante no es una
clave de identidad y puede repetirse, especialmente para menores.

`POST /identity-match` devuelve `NONE`, `OWNED`, `UNASSIGNED` o `VERIFICATION_REQUIRED`.
GUARDIAN solo recibe `studentId` y nombre para un estudiante ya vinculado a su propia cuenta;
cualquier coincidencia ajena se reduce a `VERIFICATION_REQUIRED`.

## Courses

Base: `/api/v1/courses`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/` | ADMIN, TEACHER, GUARDIAN | Lista cursos paginados. |
| GET | `/{id}` | ADMIN, TEACHER, GUARDIAN | Obtiene curso. |
| GET | `/search?query=` | ADMIN, TEACHER | Busca cursos. |
| GET | `/teacher/{teacherId}` | ADMIN, TEACHER | Cursos por docente. |
| POST | `/` | ADMIN | Crea curso. |
| PUT | `/{id}` | ADMIN | Actualiza curso. |
| DELETE | `/{id}` | ADMIN | Elimina curso. |
| POST | `/{id}/enroll` | ADMIN, TEACHER | Inscribe estudiante. |
| POST | `/filter` | ADMIN, TEACHER | Filtra cursos por criterios. |
| GET | `/published` | Publico | Cursos publicados para catalogo frontend. |
| GET | `/statistics` | ADMIN | Estadisticas de cursos. |
| PATCH | `/{id}/publish` | ADMIN | Publica curso. |
| PATCH | `/{id}/unpublish` | ADMIN | Despublica curso. |
| PATCH | `/{id}/activate` | ADMIN | Activa curso. |
| PATCH | `/{id}/deactivate` | ADMIN | Desactiva curso. |

Enums relevantes:

```ts
type CourseStatus = 'DRAFT' | 'PUBLISHED' | 'ACTIVE' | 'INACTIVE' | 'COMPLETED' | 'CANCELLED';
type CourseLevel = 'BEGINNER' | 'ELEMENTARY' | 'PRE_INTERMEDIATE' | 'INTERMEDIATE' | 'UPPER_INTERMEDIATE' | 'ADVANCED';
```

Nota: la asignacion de aula/horario se maneja con reservas en `scheduling`; los cursos pueden consumir providers de scheduling.

`CourseDto.teacherId` puede ser `null`. `enrolledStudents` representa exclusivamente
inscripciones `ACTIVE`; `totalEnrollments` incluye todas las inscripciones. El codigo de
curso admite de 1 a 50 caracteres alfanumericos, espacios, guion, guion bajo y punto, con
unicidad insensible a mayusculas. Publicar no exige una matricula minima, pero si docente,
reserva, estado valido y disponibilidad. `durationHours` es la carga horaria total planificada
del curso, no la duracion de cada clase.

## Enrollments

Base: `/api/v1/enrollments`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/{id}` | ADMIN, TEACHER | Obtiene inscripcion. |
| GET | `/student/{studentId}` | ADMIN, TEACHER, GUARDIAN | Inscripciones del estudiante. |
| GET | `/student/{studentId}/history` | ADMIN, TEACHER, GUARDIAN | Historial academico del estudiante. |
| GET | `/course/{courseId}` | ADMIN, TEACHER | Inscripciones del curso. |
| PUT | `/{id}` | ADMIN, TEACHER | Actualiza inscripcion, estado o calificaciones. |
| DELETE | `/{id}` | ADMIN | Elimina inscripcion. |
| POST | `/bulk` | ADMIN, TEACHER | Crea inscripciones masivas. |

## Tuition

Base: `/api/v1/tuition`

Tuition separa la solicitud del tutor, el cobro de matricula, la nivelacion y la asignacion
academica final. El tutor no elige ciclo, nivel, curso ni plan. `Enrollment` y los cargos de
cuotas se crean juntos cuando ADMIN asigna una solicitud ya matriculada y nivelada.

### Flujo unificado ADMIN / GUARDIAN

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| POST | `/applications` | ADMIN, GUARDIAN | Resuelve o crea el estudiante y crea una solicitud `SUBMITTED`; exige `Idempotency-Key`. |
| GET | `/my-applications` | GUARDIAN | Lista solicitudes del guardian autenticado. |

### Admin flow

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| POST | `/applications` | ADMIN, GUARDIAN | ADMIN informa `actingForGuardianUserId`; ambos actores usan el mismo agregado y reglas. |
| GET | `/applications?status=&academicYearId=` | ADMIN | Lista solicitudes con filtros; las nuevas no tienen ciclo hasta la asignacion. |
| GET | `/applications/{id}` | ADMIN, TEACHER | Detalle con matricula, nivelacion, asignacion y ledger. |
| POST | `/applications/{id}/enrollment-charge` | ADMIN | Aplica una politica de matricula activa y crea el ledger/cargo idempotente. |
| PUT | `/applications/{id}/placement` | ADMIN, TEACHER | Registra nivelacion `COMPLETED` o `WAIVED`; exige matricula totalmente paga. |
| PUT | `/applications/{id}/assignment` | ADMIN | Asigna ciclo, nivel, curso y plan; valida pago, nivelacion, cupo y progresion, crea `Enrollment` y cuotas. |
| PUT | `/applications/{id}/reject` | ADMIN | Rechaza una solicitud sin pagos confirmados y cancela ledger/cargos abiertos. |

### Admin catalogs

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/academic-years` | ADMIN, GUARDIAN | Lista ciclos; `status=OPEN` permite el catalogo del tutor. |
| POST | `/academic-years` | ADMIN | Crea ciclo lectivo de matriculacion. |
| PUT/DELETE | `/academic-years/{id}` | ADMIN | Actualiza o elimina ciclo. |
| GET | `/levels` | ADMIN, GUARDIAN | Lista niveles; `activeOnly=true` filtra catalogo disponible. |
| POST | `/levels` | ADMIN | Crea nivel. |
| PUT/DELETE | `/levels/{id}` | ADMIN | Actualiza o elimina nivel. |
| GET/POST | `/level-progressions` | ADMIN | Lista o crea correlaciones de nivel. |
| PUT/DELETE | `/level-progressions/{id}` | ADMIN | Actualiza o elimina progresion. |
| GET | `/fee-plans` | ADMIN, GUARDIAN | Lista planes vigentes filtrables por ciclo/nivel/segmento. |
| POST | `/fee-plans` | ADMIN | Crea plan de cuota. |
| PUT/DELETE | `/fee-plans/{id}` | ADMIN | Actualiza o elimina plan. |
| GET/POST | `/enrollment-fee-policies` | ADMIN | Lista o crea politicas independientes para la matricula. |
| PUT/DELETE | `/enrollment-fee-policies/{id}` | ADMIN | Actualiza o elimina una politica; solo puede existir una predeterminada. |
| GET/POST | `/discounts` | ADMIN | Lista o crea descuentos/becas. |
| PUT/DELETE | `/discounts/{id}` | ADMIN | Actualiza o elimina descuento/beca. |

Enums principales:

```ts
type TuitionAcademicYearStatus = 'DRAFT' | 'OPEN' | 'CLOSED';
type TuitionSegment = 'CHILDREN' | 'TEENS' | 'ADULTS';
type TuitionApplicationType = 'NEW_STUDENT' | 'REGULAR_PROMOTION' | 'ADDITIONAL_STUDENT';
type TuitionStudentMode = 'EXISTING' | 'NEW';
type TuitionApplicationOrigin = 'ADMIN' | 'GUARDIAN';
type TuitionStudentResolution = 'EXISTING' | 'CREATED';
type TuitionApplicationStatus =
  | 'SUBMITTED'
  | 'PAYMENT_PENDING'
  | 'ENROLLED_PENDING_PLACEMENT'
  | 'READY_FOR_ACADEMIC_ASSIGNMENT'
  | 'WAITLISTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';
type TuitionLedgerConcept = 'TUITION_ENROLLMENT' | 'MONTHLY_FEE';
type TuitionLedgerStatus = 'PENDING' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';
type TuitionPlacementStatus = 'PENDING' | 'COMPLETED' | 'WAIVED';

type TuitionProgressionRule = 'PASS_PREVIOUS_LEVEL' | 'ADMIN_APPROVAL';
```

Solicitud:

```ts
interface CreateTuitionApplicationRequest {
  applicationType: TuitionApplicationType;
  actingForGuardianUserId?: number; // obligatorio para ADMIN
  studentMode?: TuitionStudentMode;
  studentId?: number;
  studentFirstName?: string;
  studentLastName?: string;
  studentEmail?: string;
  studentDocumentType?: 'DNI' | 'PASSPORT' | 'NATIONAL_ID' | 'NO_DOCUMENT' | 'IN_PROCESS';
  studentDocumentCountry?: string;
  studentDocumentNumber?: string;
  studentDateOfBirth?: string;
  studentAddress?: string;
  studentPhoneNumber?: string;
  studentEmergencyContact?: string;
  studentMedicalNotes?: string;
}
```

Notas:

- `Idempotency-Key` debe tener 8 a 128 caracteres. Repetir la misma clave, actor y payload
  devuelve la solicitud existente; reutilizarla para otro payload responde `409 IDEMPOTENCY_KEY_REUSED`.
- `REGULAR_PROMOTION` requiere `studentMode=EXISTING`. Los demas tipos permiten reutilizar
  `studentId` o enviar el perfil completo. La identidad y el unico vinculo tutor-estudiante se
  resuelven transaccionalmente antes de persistir la solicitud y antes de cualquier cargo.
- ADMIN opera en nombre de un tutor activo mediante `actingForGuardianUserId`; la solicitud
  conserva `actorUserId`, `guardianUserId`, `origin` y `studentResolution`.
- GUARDIAN no puede vincularse a una coincidencia ajena: recibe `422 STUDENT_MATCH_REQUIRES_VERIFICATION`.
- Un pago parcial mantiene `PAYMENT_PENDING`. Al completar la matricula, el observer solo marca
  el ledger y pasa a `ENROLLED_PENDING_PLACEMENT`; nunca crea tarde una segunda fila `students`.
- El pago se registra por `POST /api/v1/billing/charges/{chargeId}/payments` y actualiza importes
  y `billingReference=PAYMENT-{paymentId}`. Una reversion previa a la asignacion vuelve a
  `PAYMENT_PENDING`; si el alumno ya fue asignado conserva su historial y deja una advertencia.
- La asignacion exige nivelacion terminada o dispensada. Si el nivel final difiere del recomendado
  o se fuerza una progresion, ADMIN debe justificarlo en `adminNotes`.
- La validacion de cupo y la creacion de `Enrollment` ocurren en la asignacion. Sin cupo la
  solicitud queda `WAITLISTED` y no genera inscripcion ni cuotas.
- Las entradas `MONTHLY_FEE` se generan recien al asignar y comienzan en el mes de esa
  asignacion, nunca antes del inicio del plan o del ciclo. Se detienen en el primer limite entre
  fin del plan, fin del ciclo y `installments`, que representa la cantidad maxima por estudiante.
  El primer vencimiento nunca puede ser anterior a la fecha de asignacion. Su debito proviene
  del plan mensual; la matricula usa su politica independiente.

### Alta e invitacion administrativa de tutores

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| POST | `/api/v1/admin/guardians` | ADMIN | Crea tutor `ACTIVE` con clave inicial o `INVITE` con token de 48 horas. |
| POST | `/api/v1/auth/guardian-invitations/accept` | Publico | Consume una vez el token y define una clave de al menos 12 caracteres. |

El token de invitacion se devuelve una sola vez al ADMIN; en base solo se guarda SHA-256.
Este camino no reemplaza `POST /auth/register` ni el circuito existente de aprobacion.

## Course Sessions

Base: `/api/v1/sessions`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/{id}` | ADMIN, TEACHER | Obtiene sesion. |
| GET | `/course/{courseId}` | ADMIN, TEACHER | Sesiones de un curso. |
| GET | `/course/{courseId}/range?startDate=&endDate=` | ADMIN, TEACHER | Sesiones por rango. |
| POST | `/` | ADMIN, TEACHER | Crea sesion. |
| POST | `/recurring` | ADMIN, TEACHER | Genera sesiones recurrentes. |
| PUT | `/{id}` | ADMIN, TEACHER | Actualiza sesion. |
| DELETE | `/{id}` | ADMIN | Elimina sesion. |
| POST | `/exceptions` | ADMIN, TEACHER | Crea excepcion de sesion. |
| POST | `/check-conflicts` | ADMIN, TEACHER | Verifica conflictos. |
| GET | `/{id}/attendance-summary` | ADMIN, TEACHER | Resumen de asistencia de sesion. |
| GET | `/calendar?startDate=&endDate=&courseId=` | ADMIN, TEACHER | Calendario de sesiones. |

## Attendance

Base: `/api/v1/attendance`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/{id}` | ADMIN, TEACHER | Obtiene asistencia. |
| GET | `/enrollment/{enrollmentId}` | ADMIN, TEACHER | Asistencia por inscripcion. |
| GET | `/course/{courseId}` | ADMIN, TEACHER | Asistencia por curso. |
| GET | `/student/{studentId}` | ADMIN, TEACHER, GUARDIAN | Asistencia por estudiante. |
| GET | `/course/{courseId}/date/{date}` | ADMIN, TEACHER | Asistencia del curso en fecha. |
| POST | `/` | ADMIN, TEACHER | Registra asistencia. |
| POST | `/bulk` | ADMIN, TEACHER | Registra asistencia masiva. |
| PUT | `/{id}` | ADMIN, TEACHER | Actualiza asistencia. |
| DELETE | `/{id}` | ADMIN | Elimina asistencia. |
| GET | `/enrollment/{enrollmentId}/statistics` | ADMIN, TEACHER | Estadisticas de asistencia. |
| GET | `/course/{courseId}/report/{date}` | ADMIN, TEACHER | Reporte diario de curso. |
| POST | `/enrollment/{enrollmentId}/range` | ADMIN, TEACHER | Consulta por rango desde body. |

### BulkAttendanceRequest

El body es un objeto (no un array raiz):

```json
{
  "courseSessionId": 42,
  "date": "2026-07-20",
  "records": [
    { "enrollmentId": 13, "status": "PRESENT", "notes": null },
    { "enrollmentId": 14, "status": "LATE", "notes": "10 minutos" }
  ]
}
```

`attendanceDate` es un alias de `date` y `attendances` un alias de `records` para
compatibilidad. La fecha debe coincidir con `courseSessionId`; la clave idempotente
es `(enrollmentId, courseSessionId)`. Las respuestas exponen `courseSessionId`,
`attendanceDate` y `studentName` cuando existe el estudiante.

## Course Materials

Base: `/api/v1/materials`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/{id}` | ADMIN, TEACHER | Obtiene material. |
| GET | `/course/{courseId}` | ADMIN, TEACHER | Materiales por curso, con `visibleOnly`. |
| GET | `/course/{courseId}/type/{type}` | ADMIN, TEACHER | Materiales por tipo. |
| POST | `/` | ADMIN, TEACHER | Crea material. |
| PUT | `/{id}` | ADMIN, TEACHER | Actualiza material. |
| DELETE | `/{id}` | ADMIN, TEACHER | Elimina material. |
| PUT | `/course/{courseId}/reorder` | ADMIN, TEACHER | Reordena materiales. |
| PUT | `/{id}/toggle-visibility` | ADMIN, TEACHER | Cambia visibilidad. |
| GET | `/course/{courseId}/statistics` | ADMIN, TEACHER | Estadisticas de materiales. |

Enums esperados:

```ts
type MaterialType = 'DOCUMENT' | 'VIDEO' | 'AUDIO' | 'LINK' | 'IMAGE' | 'PRESENTATION' | 'EXERCISE' | 'OTHER';
```

## Certificates

Base: `/api/v1/certificates`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/{id}` | ADMIN, TEACHER | Obtiene certificado. |
| GET | `/code/{code}` | Publico | Obtiene certificado por codigo. |
| GET | `/student/{studentId}` | ADMIN, TEACHER | Certificados por estudiante. |
| GET | `/course/{courseId}` | ADMIN, TEACHER | Certificados por curso. |
| POST | `/` | ADMIN, TEACHER | Emite certificado. |
| PUT | `/{id}` | ADMIN, TEACHER | Actualiza certificado. |
| POST | `/{id}/revoke` | ADMIN | Revoca certificado. |
| GET | `/verify/{code}` | Publico | Verifica certificado. |
| GET | `/statistics` | ADMIN | Estadisticas. |
| POST | `/process-expired` | ADMIN | Procesa certificados expirados. |

## Staff

### Teaching Staff

Base: `/api/v1/staff/teaching`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/` | ADMIN | Lista docentes. |
| GET | `/{id}` | ADMIN, TEACHER | Detalle de docente. |
| GET | `/search?query=` | ADMIN | Busca docentes. |
| GET | `/assignable` | ADMIN | Lista personal activo enlazado a cuentas activas `TEACHER` o `ADMIN`; `id` pertenece a `users`. |
| POST | `/resolve` | ADMIN, TEACHER | Resuelve ids a nombres. |
| POST | `/` | ADMIN | Crea docente. |
| PUT | `/{id}` | ADMIN | Actualiza docente. |
| POST | `/{id}/photo` | ADMIN | Sube foto multipart (`file`); valida tipo y tamaño. |
| GET | `/{id}/photo` | ADMIN, TEACHER | Descarga foto binaria si existe. |
| DELETE | `/{id}` | ADMIN | Soft delete. |

`CreateTeachingStaffRequest` requiere `username` e `initialPassword` (minimo 8 caracteres)
y puede recibir `assignedCourseIds`; crea una cuenta activa `TEACHER` y enlaza el docente
transaccionalmente. `UpdateTeachingStaffRequest` no acepta credenciales: recibe solo datos
personales/laborales, `linkedUserId`, `assignedCourseIds`, `confirmCourseReassignments` e
`isActive`. Las asignaciones son exactas: cursos quitados quedan sin docente y las
reasignaciones requieren confirmacion.

`courses.teacher_id` y `AssignableTeacherDto.id` usan siempre el identificador de `users`,
nunca el identificador de `teaching_staff`. Una cuenta `ADMIN` puede ser asignable cuando
tambien esta enlazada a un legajo docente activo.

### Non-Teaching Staff

Base: `/api/v1/staff/non-teaching`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/` | ADMIN | Lista personal no docente. |
| GET | `/{id}` | ADMIN, TEACHER | Detalle. |
| GET | `/by-role/{role}` | ADMIN | Filtra por rol. |
| GET | `/search?query=` | ADMIN | Busca no docentes. |
| POST | `/` | ADMIN | Crea no docente. |
| PUT | `/{id}` | ADMIN | Actualiza no docente. |
| DELETE | `/{id}` | ADMIN | Soft delete. |

### Staff Attendance

Base: `/api/v1/staff/attendance`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| POST | `/` | ADMIN | Registra asistencia de personal. |
| PUT | `/{id}` | ADMIN | Actualiza asistencia. |
| GET | `/teaching/{staffId}?startDate=&endDate=` | ADMIN, TEACHER | Asistencia docente. |
| GET | `/non-teaching/{staffId}?startDate=&endDate=` | ADMIN, TEACHER | Asistencia no docente. |
| DELETE | `/{id}` | ADMIN | Elimina asistencia. |

Enums relevantes:

```ts
type StaffType = 'TEACHING' | 'NON_TEACHING';
type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'JUSTIFIED';
type PaymentStatus = 'PENDING' | 'PAID' | 'PARTIAL' | 'OVERDUE';
```

## Exams

Base: `/api/v1/exams`

Importante: estos endpoints no siempre usan `ApiResponse<T>`.

| Metodo | Ruta | Roles | Descripcion | Respuesta actual |
|---|---|---|---|---|
| GET | `/` | ADMIN, TEACHER | Lista examenes del actor; ADMIN ve todos y TEACHER solo cursos asignados. | `PageResponse<ExamSummaryDto>` |
| GET | `/{id}` | Autenticado | Obtiene examen. | `ExamDto` |
| GET | `/course/{courseId}` | Autenticado | Lista examenes por curso. | `PageResponse<ExamDto>` |
| GET | `/my-exams?courseIds=` | ADMIN, TEACHER | Examenes del docente autenticado. | `PageResponse<ExamDto>` |
| GET | `/visible` | Autenticado | Examenes visibles para estudiantes. | `PageResponse<ExamDto>` |
| POST | `/` | ADMIN, TEACHER | Crea examen. | `ExamDto`, `201` |
| PUT | `/{id}` | ADMIN, TEACHER | Actualiza examen. | `ExamDto` |
| POST | `/{id}/publish` | ADMIN, TEACHER | Publica examen. | `ExamDto` |
| POST | `/{id}/close` | ADMIN, TEACHER | Cierra examen. | `ExamDto` |
| POST | `/{id}/cancel` | ADMIN | Cancela examen. | `ExamDto` |
| DELETE | `/{id}` | ADMIN | Elimina examen draft sin submissions. | `204` |
| GET | `/{id}/statistics` | ADMIN, TEACHER | Estadisticas de examen. | `ExamStatisticsDto` |
| GET | `/{id}/gradebook` | ADMIN, TEACHER | Sincroniza alumnos activos y devuelve la grilla de notas. Un TEACHER debe estar asignado al curso. | `ExamGradebookDto` |
| PATCH | `/{id}/grades` | ADMIN, TEACHER | Guarda hasta 200 filas por lote con control de version. Un TEACHER debe estar asignado al curso. | `ExamGradebookDto` |
| GET | `/course/{courseId}/statistics` | ADMIN, TEACHER | Estadisticas por curso. | `CourseExamStatisticsDto` |

Enums relevantes:

```ts
type ExamStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'CANCELLED';
type ExamModality = 'OFFLINE' | 'ONLINE';
```

Calificaciones por categorias:

- `readingScore`, `writingScore` y `listeningScore` son enteros opcionales entre `0` y `100`.
- `finalScore`/`score` es de solo lectura para clientes: el backend calcula el promedio simple de
  las tres categorias y redondea a entero con `HALF_UP`.
- La aprobacion se determina con nota final mayor o igual a `60`.
- Una carga parcial permanece `PENDING` y no tiene nota final. Si ya existe una nota final, las
  tres categorias deben enviarse completas para reemplazarla.
- Cada item de `PATCH /{id}/grades` envia `submissionId`, `expectedVersion`, las tres categorias,
  `feedback?` y `reason?`. El motivo es obligatorio al modificar una nota ya existente.
- Una version desactualizada responde conflicto `409` con codigo `GRADE_VERSION_CONFLICT`; el lote
  es transaccional y no se aplican cambios parciales.

## Exam Submissions

Base: `/api/v1/exam-submissions`

Importante: estos endpoints no siempre usan `ApiResponse<T>`.

| Metodo | Ruta | Roles | Descripcion | Respuesta actual |
|---|---|---|---|---|
| GET | `/{id}` | Autenticado | Obtiene submission. | `ExamSubmissionDto` |
| GET | `/exam/{examId}` | ADMIN, TEACHER | Submissions por examen. | `PageResponse<ExamSubmissionDto>` |
| GET | `/student/{studentId}` | Autenticado | Submissions por estudiante. | `PageResponse<ExamSubmissionDto>` |
| GET | `/student/{studentId}/course/{courseId}/history` | Autenticado | Historial de examenes. | `ExamResultSummary[]` |
| POST | `/` | ADMIN, TEACHER | Crea submission. | `ExamSubmissionDto`, `201` |
| POST | `/{id}/grade` | ADMIN, TEACHER | Califica. | `ExamSubmissionDto` |
| PUT | `/{id}/grade` | ADMIN, TEACHER | Actualiza nota. | `ExamSubmissionDto` |
| POST | `/{id}/attach-file?filePath=` | ADMIN, TEACHER | Adjunta ruta de archivo escaneado. | `ExamSubmissionDto` |
| POST | `/{id}/cancel` | ADMIN | Cancela submission. | `ExamSubmissionDto` |
| GET | `/{id}/grade-history` | ADMIN, TEACHER | Historial de cambios de nota. | `GradeHistoryDto[]` |

```ts
type SubmissionStatus = 'PENDING' | 'GRADED' | 'CANCELLED' | 'UNDER_REVIEW';
type GradeCompletionStatus = 'NOT_STARTED' | 'INCOMPLETE' | 'COMPLETE' | 'LEGACY_FINAL_ONLY';
```

## Teacher Performance

Base: `/api/v1/teachers`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/{teacherId}/performance` | ADMIN | Metricas completas de rendimiento docente. |
| GET | `/{teacherId}/exams` | ADMIN, TEACHER | Examenes del docente con filtros. |
| POST | `/compare` | ADMIN | Compara rendimiento de docentes. |

Query para examenes de docente:

- `statuses?: ExamStatus[]`
- `page?: number`
- `size?: number`
- `sort?: string`
- `order?: ASC | DESC`

## Scheduling

Scheduling esta separado de `sessions`: administra aulas, slots y reservas. Las reservas pueden apuntar a cursos, sesiones o quedar libres.

### Classrooms

Base: `/api/v1/classrooms`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/` | ADMIN, TEACHER | Lista aulas; `activeOnly=true` devuelve lista simple activa. |
| GET | `/{id}` | ADMIN, TEACHER | Obtiene aula. |
| POST | `/` | ADMIN | Crea aula. |
| PUT | `/{id}` | ADMIN | Actualiza aula. |
| DELETE | `/{id}` | ADMIN | Desactiva aula. |

Query:

- `page?: number`
- `limit?: number`
- `size?: number`
- `activeOnly?: boolean`

### Schedule Slots

Base: `/api/v1/scheduling/slots`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/` | ADMIN, TEACHER | Lista slots; filtro opcional por aula. |
| GET | `/{id}` | ADMIN, TEACHER | Obtiene slot. |
| POST | `/` | ADMIN | Crea slot. |
| PUT | `/{id}` | ADMIN | Actualiza slot. |
| DELETE | `/{id}` | ADMIN | Desactiva slot. |

Query:

- `page?: number`
- `limit?: number`
- `size?: number`
- `classroomId?: number`

```ts
type SlotDayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
```

### Reservations

Base: `/api/v1/scheduling/reservations`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/` | ADMIN, TEACHER | Lista reservas con filtros operativos. |
| GET | `/available` | ADMIN, TEACHER | Lista reservas disponibles para combos del frontend. |
| GET | `/{id}` | ADMIN, TEACHER | Obtiene reserva. |
| POST | `/` | ADMIN | Crea reserva. |
| PATCH | `/{id}/assign` | ADMIN | Asigna reserva a target. |
| PATCH | `/{id}/unassign` | ADMIN | Libera reserva. |
| DELETE | `/{id}` | ADMIN | Inactiva reserva. |

Filtros:

- `page?: number`
- `limit?: number`
- `size?: number`
- `status?: AVAILABLE | ASSIGNED | INACTIVE`
- `targetType?: COURSE | SESSION | NONE`
- `classroomId?: number`
- `dayOfWeek?: SlotDayOfWeek`
- `startTimeFrom?: string`
- `endTimeTo?: string`

Request de asignacion:

```ts
interface AssignReservationRequest {
  targetType: 'COURSE' | 'SESSION' | 'NONE';
  targetId?: number;
}
```

## Admin Cache

Base: `/api/v1/admin/cache`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| DELETE | `/clear` | ADMIN | Limpia todo el cache Redis administrado por la aplicacion. |

## Payments, Communications and Reports

### Payments

Estado: nucleo persistente en desarrollo. Pago, recibo X, perfiles reutilizables, cargos,
imputaciones, ejecuciones manuales, factura, intentos, outbox y secuencia estan implementados.
Dev usa mock; el cliente WSAA/WSFEv1, catalogos, detalle impositivo, URL QR y PDFs tambien existen,
pero aun falta probarlos con credenciales reales de homologacion.

Todas las operaciones requieren `ADMIN`. Las escrituras indicadas exigen el header
`Idempotency-Key`; repetir la misma key y payload devuelve el recurso existente, mientras que
reutilizar la key con otro payload es un conflicto.

Base de pagos: `/api/v1/payments`

| Metodo | Ruta | Idempotency-Key | Respuesta | Descripcion |
|---|---|---|---|---|
| POST | `/register` | Si | `201 ApiResponse<BillingWorkflowDto>` | Crea pago confirmado, recibo X y borrador fiscal en una transaccion local. |
| POST | `/receipts` | Si | `201 ApiResponse<PaymentDetailDto>` | Registra atomicamente pago confirmado y recibo X; no crea factura, outbox ni llama al proveedor fiscal. |
| POST | `/` | Si | `201 ApiResponse<PaymentDetailDto>` | Crea un pago `PENDING`. |
| GET | `/` | No | `ApiResponse<PageResponse<PaymentDto>>` | Lista por `page` y `limit`. |
| GET | `/{id}` | No | `ApiResponse<PaymentDetailDto>` | Devuelve pago, recibo y factura vinculada. |
| POST | `/{id}/confirm` | Si | `ApiResponse<PaymentDetailDto>` | Marca `PAID` y emite los datos del recibo X. |
| GET | `/{id}/receipt` | No | `ApiResponse<PaymentReceiptDto>` | Devuelve los datos estructurados del recibo. |
| GET | `/{id}/receipt/document` | No | `application/pdf` | Descarga el recibo X no fiscal. |
| POST | `/{id}/fiscal-invoice` | Si | `201 ApiResponse<FiscalInvoiceDetailDto>` | Crea el borrador fiscal de un pago confirmado. |

Requests principales:

```ts
type PaymentMethod = 'CASH' | 'CREDIT_CARD' | 'DEBIT_CARD' | 'BANK_TRANSFER' | 'CHECK' | 'AUTOMATIC_DEBIT';

interface CreatePaymentRequest {
  studentId: number | null; // nullable durante la matriculacion previa al alta del estudiante
  amount: number;
  currency: string; // ISO 4217, por defecto ARS
  concept: string;
  dueDate: string;
  externalReference?: string | null;
  notes?: string | null;
}

interface ConfirmPaymentRequest {
  paymentDate: string;
  paymentMethod: PaymentMethod;
  payerName: string;
}

interface CreateFiscalInvoiceRequest {
  voucherType: number;
  concept: 1 | 2 | 3;
  receiverName: string;
  receiverAddress: string;
  receiverDocumentType: number;
  receiverDocumentNumber: string;
  receiverVatConditionId: number;
  issueDate: string;
  serviceFrom?: string | null;
  serviceTo?: string | null;
  paymentDueDate?: string | null;
  currency: string; // codigo WSFE, por defecto PES
  exchangeRate: number;
  nonTaxedAmount: number;
  netAmount: number;
  exemptAmount: number;
  vatAmount: number;
  otherTaxesAmount: number;
  vatSubtotals: FiscalVatSubtotalRequest[];
  taxes: FiscalOtherTaxRequest[];
}

interface FiscalVatSubtotalRequest {
  id: number;
  baseAmount: number;
  amount: number;
}

interface FiscalOtherTaxRequest {
  id: number;
  description: string;
  baseAmount: number;
  rate: number;
  amount: number;
}

interface RegisterPaymentAndInvoiceRequest {
  payment: CreatePaymentRequest;
  confirmation: ConfirmPaymentRequest;
  invoice: CreateFiscalInvoiceRequest;
}
```

El total fiscal (`nonTaxedAmount + netAmount + exemptAmount + vatAmount + otherTaxesAmount`)
debe coincidir con el monto del pago a dos decimales con redondeo `HALF_EVEN`. Las sumas de
`vatSubtotals`/`taxes` deben coincidir con neto, IVA y tributos agregados. Para conceptos
2/3 se requieren las tres fechas de servicio. CUIT emisor y punto de venta provienen de
configuracion segura del backend, no del request.

Base de facturas: `/api/v1/billing/invoices`

| Metodo | Ruta | Idempotency-Key | Respuesta | Descripcion |
|---|---|---|---|---|
| GET | `/` | No | `ApiResponse<PageResponse<FiscalInvoiceDto>>` | Bandeja filtrable por `status`, `page` y `limit`. |
| GET | `/{id}` | No | `ApiResponse<FiscalInvoiceDetailDto>` | Factura e intentos sanitizados. |
| GET | `/{id}/document` | No | `application/pdf` | Descarga factura `AUTHORIZED*`; exige CAE/QR y datos legales completos. |
| POST | `/{id}/authorize` | Si | `202 ApiResponse<FiscalInvoiceDetailDto>` | Encola una factura `READY`; el worker la procesa. |
| POST | `/{id}/reconcile` | No | `ApiResponse<FiscalInvoiceDetailDto>` | Consulta una factura `UNKNOWN`; nunca la reemite. |

Estados de factura:

```ts
type FiscalInvoiceStatus =
  | 'DRAFT' | 'READY' | 'QUEUED' | 'AUTHORIZING'
  | 'AUTHORIZED' | 'AUTHORIZED_WITH_OBSERVATIONS'
  | 'REJECTED' | 'UNKNOWN';
```

`DRAFT` incluye `preflightErrors`; `AUTHORIZED*` incluye numero, CAE, vencimiento y `qrUrl`;
`FiscalInvoiceDetailDto.attempts` conserva intentos de autorizacion/consulta sin XML ni secretos.

Base operativa: `/api/v1/billing`

| Metodo | Ruta | Idempotency-Key | Respuesta | Descripcion |
|---|---|---|---|---|
| GET | `/charges` | No | `ApiResponse<PageResponse<BillingChargeDto>>` | Lista por `status`, estudiante, perfil, `fiscalDisposition`, `overdue`, `automaticDebitStatus`, `collectionChannel`, `page` y `limit`. |
| GET | `/charges/{chargeId}` | No | `ApiResponse<BillingChargeDto>` | Detalle con capital, recargo, pagado, saldo, pagos/recibos y ultimo intento de debito. |
| GET | `/accounts/{accountId}/profile` | No | `ApiResponse<BillingProfileDto>` | Perfil fiscal reutilizable y campos faltantes. |
| PUT | `/accounts/{accountId}/profile` | No | `ApiResponse<BillingProfileDto>` | Valida y completa receptor, documento, IVA, comprobante, concepto y moneda. |
| POST | `/charges/{chargeId}/payments` | Si | `201 ApiResponse<ChargePaymentResultDto>` | Imputa `amount` total o parcial y emite un recibo X. Al cancelar saldo exige `fiscalClosure`; `EXCLUDE_CHARGE` exige motivo y feature flag. |
| POST | `/charges/{chargeId}/fiscal-decisions` | Si | `ApiResponse<BillingChargeDto>` | Rectifica el tratamiento fiscal mediante una nueva decision auditada. |
| POST | `/charges/{chargeId}/late-fee/reversal` | No | `ApiResponse<BillingChargeDto>` | Anula el recargo activo con motivo; se bloquea si existe factura o el recargo ya fue cobrado. |
| POST | `/runs/preview` | No | `ApiResponse<BillingRunPreviewDto>` | Resuelve seleccion individual, explicita o filtrada; revalida y puede persistir un recargo vencido, pero no crea facturas. |
| POST | `/runs` | Si | `201 ApiResponse<BillingRunDto>` | Crea en servidor las facturas del conjunto validado y audita la ejecucion. |
| GET | `/runs/{runId}` | No | `ApiResponse<BillingRunDto>` | Devuelve ejecucion e items creados. |

```ts
type BillingSelectionMode = 'INDIVIDUAL' | 'SELECTED' | 'FILTERED';
type BillingChargeStatus = 'OPEN' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';
type BillingProfileStatus = 'INCOMPLETE' | 'READY';
type BillingCollectionChannel = 'REGULAR' | 'AUTOMATIC_DEBIT';
type AutomaticDebitInstructionStatus =
  | 'READY_FOR_PROCESSING' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'UNKNOWN'
  | 'ACCOUNTING_RESOLUTION_REQUIRED' | 'CREDIT_NOTE_REQUIRED' | 'REVERSED' | 'CANCELLED';
type FiscalAmountTreatment = 'NON_TAXED' | 'EXEMPT';

interface PrepareBillingRunRequest {
  selectionMode: BillingSelectionMode;
  chargeIds: number[]; // uno para INDIVIDUAL; uno o mas para SELECTED
  filters: {
    status?: BillingChargeStatus | null;
    studentId?: number | null;
    profileStatus?: BillingProfileStatus | null;
    fiscalDisposition?: 'PENDING' | 'EXCLUDED' | null;
    overdue?: boolean | null;
    automaticDebitStatus?: AutomaticDebitInstructionStatus | null;
    collectionChannel?: BillingCollectionChannel | null; // default REGULAR
  };
  issueDate: string;
  amountTreatment: FiscalAmountTreatment;
}
```

`BillingChargeDto.amount` conserva compatibilidad y representa el total (`baseAmount + lateFeeAmount`).
Tambien expone `paidAmount`, `outstandingAmount`, `overdue`, `fiscalDisposition`, elegibilidad y
`payments[]`. Cada parcial debe ser mayor que cero y no superar el saldo; la fila del cargo se bloquea
transaccionalmente. Los parciales siempre usan `KEEP_PENDING`. El pago no invoca `FiscalAuthorityPort`.

Base de adhesion Tutor: `/api/v1/billing/me/debit-mandates` (`GUARDIAN`). Esta autogestion
solo esta disponible con el autorizador mock de desarrollo/QA y se identifica como simulada.

| Metodo | Ruta | Descripcion |
|---|---|---|
| GET | `/` | Lista solamente los mandatos de la cuenta autenticada. |
| POST | `/` | Registra consentimiento y referencia opaca; nunca recibe PAN, CVV ni CBU completo. |
| PATCH | `/{mandateId}` | Activa, pausa o cancela un mandato propio; un mandato ajeno no se expone. |

Base ADMIN: `/api/v1/billing/automatic-debit`.

| Metodo | Ruta | Idempotency-Key | Descripcion |
|---|---|---|---|
| GET | `/mandates` | No | Consola paginada de mandatos. |
| GET | `/instructions` | No | Consola paginada de instrucciones. |
| POST | `/mandates` | No | Adhiere manualmente una cuenta, procesadora, instrumento enmascarado, alcance y vigencia. |
| PATCH | `/mandates/{id}` | No | Activa, pausa o cancela una adhesion. |
| POST | `/instructions` | Si | Prepara la tarjeta de copia desde una factura fiscal ya autorizada; no envia ni cobra. |
| POST | `/instructions/{id}/submission` | Si | Registra que ADMIN presento el dato a la procesadora y su referencia externa. |
| POST | `/instructions/{id}/results` | Si | Registra `APPROVED`, `REJECTED` o `UNKNOWN`; solo `APPROVED` crea pago y recibo X. |
| POST | `/instructions/{id}/resolution` | Si | Resuelve un rechazo conservando la factura o marcando `CREDIT_NOTE_REQUIRED`, siempre con motivo. |
| POST | `/instructions/{id}/cancellation` | Si | Cancela una preparacion que aun no fue presentada, con motivo. |
| POST | `/instructions/{id}/reversal` | No | Revierte un aprobado, marca el pago `REVERSED` y reabre el saldo. |

La adhesion vive en la cuenta de facturacion y enruta solo cargos futuros elegibles al
`collectionChannel=AUTOMATIC_DEBIT`; la matricula se incluye unicamente si el alcance lo indica.
Los lotes de facturacion no mezclan los canales regular y debito. Para preparar el procesamiento,
la factura debe estar autorizada y tener punto de venta y numero de comprobante. La respuesta incluye
cliente, estudiante, comprobante completo, ultimos tres digitos, importe, fecha, procesadora e
instrumento enmascarado para copia en pantalla; no existe exportador ni persistencia de PAN, CVV o CBU.

Una instruccion `READY_FOR_PROCESSING`, `SUBMITTED`, `UNKNOWN`, `ACCOUNTING_RESOLUTION_REQUIRED` o
`CREDIT_NOTE_REQUIRED` bloquea el cobro manual para evitar duplicados. `REJECTED` abre una decision
contable manual: `KEEP_INVOICE` conserva la factura y libera el circuito de cobranza, mientras
`REQUEST_CREDIT_NOTE` crea una tarea `CREDIT_NOTE_REQUIRED`; no emite una nota de credito
automaticamente. Solo `APPROVED` crea pago `AUTOMATIC_DEBIT`, imputacion y recibo X, siempre
`KEEP_PENDING`. El autorizador mock se limita al alta Tutor en dev/QA y falla al arrancar en produccion;
no simula el envio ni el resultado de la procesadora.

`FILTERED` se resuelve completamente en backend (maximo 1000 cargos); Angular no itera creando
facturas una por una. El preview bloquea perfiles incompletos, cargos cancelados, montos no
positivos y cargos que ya tienen factura. La ejecucion crea `DRAFT` o `READY` segun el preflight,
pero no encola autorizacion fiscal. Para el primer cliente `rg5866Applicable` es siempre `false`,
la restriccion esta persistida y no se recopilan ni envian datos de RG 5866.

Base de diagnostico: `/api/v1/billing/provider`

| Metodo | Ruta | Roles | Descripcion |
|---|---|---|---|
| GET | `/health` | ADMIN | Estado sanitizado del proveedor fiscal configurado. |
| GET | `/reference-data` | ADMIN | Catalogos de comprobantes, documentos, condiciones IVA y monedas. |

Respuesta `data`:

```ts
interface FiscalProviderHealthDto {
  provider: string;
  environment: 'MOCK' | 'HOMOLOGATION' | 'PRODUCTION';
  configured: boolean;
  available: boolean;
  checkedAt: string;
  message?: string | null;
}
```

No incluye certificado, Token, Sign, CUIT ni endpoints internos. El smoke WSAA/WSFE con
credenciales reales sigue pendiente. Los PDFs de mock y
homologacion incluyen marcas visibles que los identifican sin validez fiscal.

### Communications

Estado: planificado/en desarrollo. Existe entidad `Notification`, pero no hay delivery completo.

Capacidades esperadas:

- Notificaciones de aprobacion/rechazo de registros.
- Avisos academicos y operativos.
- Integracion SMTP y/o in-app.

### Reports

Estado: planificado/en desarrollo.

Capacidades esperadas:

- Reportes academicos.
- Reportes administrativos.
- Reportes financieros cuando pagos/facturacion este disponible.

## Recomendaciones para Frontend Angular

- Crear un `ApiResponse<T>` generico, pero permitir adaptadores por modulo para `exams`.
- Centralizar `Authorization` y refresh token en un interceptor HTTP.
- Centralizar manejo de `ErrorResponse`.
- Usar `limit` como parametro principal de paginacion salvo endpoints de `exams`, donde `size` es el parametro documentado.
- Tratar `GET /students/{id}/photo` como blob.
- Usar `/billing/charges` y `/billing/runs` para cobranza/preparacion; no generar lotes con
  bucles de requests desde Angular.
- En matriculacion, mostrar `ledgerEntries` como estado del cargo sincronizado. El tutor no
  marca pagos; ADMIN los registra desde facturacion.
- Consumir `GET /courses/published` para catalogos publicos sin autenticacion.
- Usar `/scheduling/reservations/available` para combos de asignacion horaria.

## Codigos de Error Esperados

Codigos comunes:

- `VALIDATION_ERROR`
- `RESOURCE_NOT_FOUND`
- `DUPLICATE_RESOURCE`
- `BUSINESS_ERROR`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `RATE_LIMIT_EXCEEDED`
- `INTERNAL_ERROR`

HTTP comunes:

- `400`: validacion o regla de negocio.
- `401`: sin token o token invalido.
- `403`: rol insuficiente o cuenta no activa.
- `404`: recurso inexistente.
- `409`: duplicado/conflicto de negocio.
- `429`: rate limit.
- `500`: error inesperado.

## Versionado

La version publica actual del contrato vive bajo `/api/v1`. Los cambios incompatibles deben introducirse con version nueva o adaptadores de compatibilidad frontend/backend.

