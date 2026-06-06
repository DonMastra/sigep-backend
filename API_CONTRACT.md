# API Contract - SiGEP Backend

Contrato REST para integracion del frontend Angular SiGEP con el backend. Este documento refleja el estado actual del workspace al 2026-06-05.

## Informacion General

- Base local: `http://localhost:8080`
- Base productiva esperada: `https://api.sigep.edu.mx`
- Prefijo API: `/api/v1`
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`
- Request/response JSON: `application/json`
- Fechas: ISO 8601. Para fechas sin hora se usa `YYYY-MM-DD`.
- Horas de scheduling: strings `HH:mm`.
- Timezone de negocio: `America/Argentina/Buenos_Aires`.

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
- `payments`, `communications` y `reports` no exponen API funcional completa.
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
}
```

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
| GET | `/search?query=` | ADMIN, TEACHER | Busca por nombre, email o documento. |
| GET | `/guardian/{guardianId}` | ADMIN, TEACHER, GUARDIAN | Estudiantes asociados a guardian. |
| POST | `/` | ADMIN | Crea estudiante. |
| POST | `/self-registration` | GUARDIAN | Crea estudiante vinculado al guardian autenticado. |
| PUT | `/{id}` | ADMIN | Actualiza estudiante. |
| DELETE | `/{id}` | ADMIN | Elimina estudiante. |
| POST | `/{id}/photo` | ADMIN | Sube foto multipart con parte `file`. |
| GET | `/{id}/photo` | ADMIN, TEACHER, GUARDIAN | Descarga imagen. |
| GET | `/{id}/courses` | ADMIN, TEACHER, GUARDIAN | Respuesta de compatibilidad con redirect a historial. |
| GET | `/{id}/payment-status` | ADMIN, TEACHER, GUARDIAN | Estado temporal/mock de pagos. |

Parametros comunes de listado:

- `page?: number`
- `limit?: number`
- `sort?: string`
- `order?: ASC | DESC`

### StudentDto

```ts
interface StudentDto {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber?: string;
  dateOfBirth?: string;
  address?: string;
  guardianName?: string;
  guardianPhone?: string;
  guardianEmail?: string;
  documentNumber?: string;
  emergencyContact?: string;
  active: boolean;
  photoUrl?: string;
  createdAt?: string;
  updatedAt?: string;
}
```

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
| POST | `/resolve` | ADMIN, TEACHER | Resuelve ids a nombres. |
| POST | `/` | ADMIN | Crea docente. |
| PUT | `/{id}` | ADMIN | Actualiza docente. |
| DELETE | `/{id}` | ADMIN | Soft delete. |

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
| GET | `/course/{courseId}/statistics` | ADMIN, TEACHER | Estadisticas por curso. | `CourseExamStatisticsDto` |

Enums relevantes:

```ts
type ExamStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'CANCELLED';
type ExamModality = 'WRITTEN' | 'ORAL' | 'PRACTICAL' | 'MIXED';
```

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
type SubmissionStatus = 'PENDING' | 'GRADED' | 'CANCELLED';
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

Estado: planificado/en desarrollo. Existe entidad `Payment`, pero no hay controlador REST funcional completo.

Capacidades esperadas:

- Estado de cuenta de estudiante.
- Cuotas, pagos, comprobantes y metodos de pago.
- Integracion con facturacion/recibos.
- Exposicion de deuda para dashboards y alertas.

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
- No depender de pagos/facturacion como modulo listo; usar `payment-status` solo como placeholder visible.
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

