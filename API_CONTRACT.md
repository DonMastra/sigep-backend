# API Contract - SiGEP Backend

**Contrato de API para Integración Frontend (Angular)**

---

## 📋 Información General

### Base URL
- **Desarrollo**: `http://localhost:8080`
- **Producción**: `https://api.sigep.edu.mx`

### API Prefix
Todos los endpoints están bajo el prefijo: `/api/v1`

### Formatos
- **Request**: `application/json`
- **Response**: `application/json`
- **Date Format**: ISO 8601 (`YYYY-MM-DDTHH:mm:ssZ`)
- **Encoding**: UTF-8

---

## 🔐 Autenticación

### Sistema de Autenticación
La API utiliza **JWT (JSON Web Tokens)** para autenticación stateless.

### Header de Autorización
Todas las requests a endpoints protegidos deben incluir:

```http
Authorization: Bearer {access_token}
```

### Flujo de Autenticación

```
1. Login → Obtener access_token y refresh_token
2. Guardar tokens en localStorage/sessionStorage
3. Incluir access_token en header Authorization de cada request
4. Si access_token expira (401), usar refresh_token para renovar
5. Si refresh_token expira, redirigir a login
```

---

## 📦 Estructura de Respuestas

### Respuesta Exitosa

Todas las respuestas exitosas siguen este formato:

```typescript
interface ApiResponse<T> {
  success: true;
  data: T;
  message: string;
  timestamp: string;
}
```

**Ejemplo:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "John Doe"
  },
  "message": "Operation successful",
  "timestamp": "2025-11-03T10:00:00Z"
}
```

### Respuesta de Lista Paginada

```typescript
interface PageResponse<T> {
  success: true;
  data: {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
  };
  message: string;
  timestamp: string;
}
```

**Ejemplo:**
```json
{
  "success": true,
  "data": {
    "content": [
      { "id": 1, "name": "Item 1" },
      { "id": 2, "name": "Item 2" }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 25,
    "totalPages": 3,
    "last": false
  },
  "message": "Students retrieved successfully",
  "timestamp": "2025-11-03T10:00:00Z"
}
```

### Respuesta de Error

```typescript
interface ErrorResponse {
  success: false;
  message: string;
  errors?: string[];
  timestamp: string;
}
```

**Ejemplo:**
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": [
    "Email is required",
    "Password must be at least 6 characters"
  ],
  "timestamp": "2025-11-03T10:00:00Z"
}
```

---

## 🔑 Authentication Endpoints

### 1. Login

**Endpoint:** `POST /api/v1/auth/login`

**Request:**
```typescript
interface LoginRequest {
  username: string;
  password: string;
}
```

**Response (200 OK):**
```typescript
interface LoginResponse {
  token: string;           // Access token (24h expiration)
  refreshToken: string;    // Refresh token (7d expiration)
  user: UserDto;
}

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

**Errores:**
- `401 Unauthorized`: Credenciales inválidas
- `403 Forbidden`: Cuenta en `PENDING_APPROVAL` (`"Tu cuenta esta pendiente de aprobacion administrativa."`) o `REJECTED` (`"Tu cuenta fue rechazada. Contacta a administracion."`)
- `429 Too Many Requests`: Demasiados intentos

---

### 2. Register

**Endpoint:** `POST /api/v1/auth/register`

**Request:**
```typescript
interface RegisterRequest {
  username: string;      // min: 3, max: 50
  email: string;         // valid email format
  password: string;      // min: 6, max: 100
  firstName: string;
  lastName: string;
  role: 'TEACHER' | 'GUARDIAN'; // ADMIN no permitido en registro publico
}
```

**Response (201 Created):**
```typescript
{
  success: true;
  data: UserDto;
  message: "Registro creado. Pendiente de aprobacion administrativa.";
}
```

**Errores:**
- `400 Bad Request`: Validación fallida
- `400 Bad Request`: Rol `ADMIN` no permitido en registro público
- `409 Conflict`: Username o email ya existe

---

### 3. Registration Status

**Endpoint:** `GET /api/v1/auth/registration-status?username={username}`

**Response (200 OK):**
```typescript
interface RegistrationStatusResponseDto {
  username: string;
  status: 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED';
  adminNotes: string | null;
  reviewedAt: string | null; // ISO-8601
}
```

**Errores:**
- `404 Not Found`: Usuario no encontrado

---

### 4. Refresh Token

**Endpoint:** `POST /api/v1/auth/refresh-token`

**Request:**
```typescript
interface RefreshTokenRequest {
  refreshToken: string;
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: LoginResponse;
  message: "Token refreshed successfully";
}
```

**Errores:**
- `401 Unauthorized`: Refresh token inválido o expirado

---

### 5. Logout

**Endpoint:** `POST /api/v1/auth/logout`

**Headers:** `Authorization: Bearer {token}`

**Response (200 OK):**
```typescript
{
  success: true;
  data: null;
  message: "Logout successful";
}
```

> **Nota Frontend**: Eliminar tokens del storage al hacer logout.

---

### 6. Admin Registration Requests

#### `GET /api/v1/admin/registration-requests`

**Query Params:**
- `status`: `PENDING_APPROVAL | ACTIVE | REJECTED` (opcional)
- `page`, `size`, `sort`, `order`

**Valores recomendados para `sort`:**
- `createdAt` (default)
- `status`
- `username`
- `requestedRole`
- `reviewedAt`

> Compatibilidad: el backend también acepta `created_at`, `requested_role` y `reviewed_at`.

**Response (200 OK):**
```typescript
interface RegistrationRequestDto {
  id: string;
  userId: number;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  requestedRole: 'GUARDIAN' | 'TEACHER';
  status: 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED';
  createdAt: string;
  reviewedAt: string | null;
  reviewedBy: number | null;
  adminNotes: string | null;
}

interface RegistrationRequestPageDto {
  items: RegistrationRequestDto[];
  page: number;
  size: number;
  total: number;
}
```

**Autorizacion:** `ADMIN`

#### `PUT /api/v1/admin/registration-requests/{requestId}/approve`

**Body (opcional):**
```typescript
interface RegistrationDecisionRequest {
  adminNotes?: string;
}
```

Transicion permitida: `PENDING_APPROVAL -> ACTIVE`.

**Response (200 OK):** `ApiResponse<RegistrationRequestDto>`

**Errores:**
- `400 Bad Request`: solicitud ya revisada (estado distinto a `PENDING_APPROVAL`)
- `404 Not Found`: `requestId` no existe
- `401/403`: token inválido o sin permisos admin

#### `PUT /api/v1/admin/registration-requests/{requestId}/reject`

**Body (opcional):**
```typescript
interface RegistrationDecisionRequest {
  adminNotes?: string;
}
```

Transicion permitida: `PENDING_APPROVAL -> REJECTED`.

**Response (200 OK):** `ApiResponse<RegistrationRequestDto>`

**Errores:**
- `400 Bad Request`: solicitud ya revisada (estado distinto a `PENDING_APPROVAL`)
- `404 Not Found`: `requestId` no existe
- `401/403`: token inválido o sin permisos admin

**Autorizacion:**
- Todos los endpoints `/api/v1/admin/registration-requests/**` requieren rol `ADMIN`.

> Nota de alcance MVP: en esta fase no se envían emails (SMTP); la aprobación/rechazo solo persiste estado y metadatos de revisión.

---

## 👥 Students Endpoints

### 1. List Students (Paginated)

**Endpoint:** `GET /api/v1/students`

**Query Params:**
```typescript
interface StudentListParams {
  page?: number;        // Default: 0
  size?: number;        // Default: 10
  sort?: string;        // Default: 'id'
  order?: 'ASC' | 'DESC'; // Default: 'ASC'
  search?: string;      // Search by name, email, documentNumber
}
```

**Roles:** `ADMIN`, `TEACHER`

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: StudentDto[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
  };
  message: string;
  timestamp: string;
}

interface StudentDto {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  documentNumber: string;
  dateOfBirth: string;      // ISO date (YYYY-MM-DD)
  enrollmentDate: string;   // ISO date (YYYY-MM-DD)
  guardianId: number | null;
  currentCourseId: number | null;
  currentCourseName: string | null;
  active: boolean;
  phoneNumber: string;
  address: string;
  createdAt: string;        // ISO datetime
  updatedAt: string;        // ISO datetime
}
```

---

### 2. Get Student by ID

**Endpoint:** `GET /api/v1/students/{id}`

**Roles:** `ADMIN`, `TEACHER`, `GUARDIAN` (solo propios)

**Response (200 OK):**
```typescript
{
  success: true;
  data: StudentDetailDto;
  message: string;
  timestamp: string;
}

interface StudentDetailDto {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  documentNumber: string;
  dateOfBirth: string;         // ISO date (YYYY-MM-DD)
  address: string;
  phoneNumber: string;
  emergencyContact: string;
  enrollmentDate: string;      // ISO date (YYYY-MM-DD)
  guardianId: number | null;
  medicalNotes: string | null;
  currentCourseId: number | null;
  currentCourseName: string | null;
  active: boolean;
  courseHistory: EnrollmentSummaryDto[];  // Historial de inscripciones
  createdAt: string;           // ISO datetime
  updatedAt: string;           // ISO datetime
}

interface EnrollmentSummaryDto {
  id: number;
  studentId: number;
  courseId: number;
  courseName: string;
  courseLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  enrollmentDate: string;      // ISO date (YYYY-MM-DD)
  status: 'ACTIVE' | 'COMPLETED' | 'FAILED' | 'DROPPED' | 'SUSPENDED';
  finalGrade: number | null;   // Decimal (0.00 - 100.00)
  completionDate: string | null; // ISO date (YYYY-MM-DD)
}
```

**Errores:**
- `404 Not Found`: Estudiante no existe
- `403 Forbidden`: No tiene permisos para ver este estudiante

---

### 3. Create Student

**Endpoint:** `POST /api/v1/students`

**Roles:** `ADMIN`

**Request:**
```typescript
interface CreateStudentRequest {
  firstName: string;
  lastName: string;
  email: string;
  documentNumber: string;
  dateOfBirth: string;      // YYYY-MM-DD
  address: string;
  phoneNumber: string;
  emergencyContact: string;
  guardianId?: number;
  medicalNotes?: string;
}
```

**Response (201 Created):**
```typescript
{
  success: true;
  data: StudentDto;
  message: "Student created successfully";
}
```

**Errores:**
- `400 Bad Request`: Validación fallida
- `409 Conflict`: Email o documento ya existe

---

### 4. Update Student

**Endpoint:** `PUT /api/v1/students/{id}`

**Roles:** `ADMIN`

**Request:** Same as `CreateStudentRequest`

**Response (200 OK):**
```typescript
{
  success: true;
  data: StudentDto;
  message: "Student updated successfully";
}
```

---

### 5. Delete Student

**Endpoint:** `DELETE /api/v1/students/{id}`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: null;
  message: "Student deleted successfully";
}
```

---

### 6. Get Students by Guardian

**Endpoint:** `GET /api/v1/students/guardian/{guardianId}`

**Roles:** `ADMIN`, `TEACHER`, `GUARDIAN` (solo propio ID)

**Response (200 OK):**
```typescript
{
  success: true;
  data: StudentDto[];
}
```

---

### 7. Get Student Payment Status

**Endpoint:** `GET /api/v1/students/{id}/payment-status`

**Roles:** `ADMIN`, `TEACHER`, `GUARDIAN` (solo estudiantes propios)

**Response (200 OK):**
```typescript
{
  success: true;
  data: StudentPaymentStatusDto;
  message: string;
  timestamp: string;
}

interface StudentPaymentStatusDto {
  studentId: number;
  status: 'UP_TO_DATE' | 'PENDING' | 'OVERDUE';
  balance: number;             // Decimal - Saldo pendiente
  lastPaymentDate: string | null;  // ISO date (YYYY-MM-DD) - Último pago realizado
  nextDueDate: string | null;      // ISO date (YYYY-MM-DD) - Próximo vencimiento
}
```

**Nota**: Actualmente retorna datos mock. Se integrará con el módulo `payments` cuando esté implementado.

**Errores:**
- `404 Not Found`: Estudiante no existe
- `403 Forbidden`: No tiene permisos para ver este estudiante

---

## 📚 Courses Endpoints

### 1. List Courses (Paginated)

**Endpoint:** `GET /api/v1/courses`

**Query Params:**
```typescript
interface CourseListParams {
  page?: number;        // Default: 0
  size?: number;        // Default: 10
  sort?: string;        // Default: 'id'
  order?: 'ASC' | 'DESC'; // Default: 'ASC'
}
```

**Roles:** `ADMIN`, `TEACHER`, `GUARDIAN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: CourseDto[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
  };
  message: string;
  timestamp: string;
}

interface CourseDto {
  id: number;
  code: string;                      // Código único del curso (ej: ENG-BEG-01)
  name: string;
  description: string;
  level: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  duration: number;                  // Duración en horas
  maxStudents: number;               // Capacidad máxima
  minStudents: number;               // Mínimo de estudiantes para abrir
  teacherId: number;
  teacherName: string | null;        // Nombre del profesor asignado
  price: number;                     // Decimal - Precio del curso
  startDate: string | null;          // ISO date (YYYY-MM-DD)
  endDate: string | null;            // ISO date (YYYY-MM-DD)
  status: 'ACTIVE' | 'INACTIVE' | 'COMPLETED' | 'CANCELLED';
  isPublished: boolean;              // Si está publicado para inscripciones
  schedules: CourseScheduleDto[];    // Horarios del curso
  enrolledStudents: number;          // Cantidad de estudiantes inscriptos
  availableSeats: number;            // Cupos disponibles
  isEnrollmentOpen: boolean;         // Si está abierto a inscripciones
  createdAt: string;                 // ISO datetime
  updatedAt: string;                 // ISO datetime
}

interface CourseScheduleDto {
  id: number | null;
  dayOfWeek: 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
  startTime: string;                 // HH:mm format
  endTime: string;                   // HH:mm format
}
```

---

### 2. Get Course by ID

**Endpoint:** `GET /api/v1/courses/{id}`

**Roles:** `ADMIN`, `TEACHER`, `GUARDIAN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: CourseDto;
  message: string;
  timestamp: string;
}
```

**Errores:**
- `404 Not Found`: Curso no existe

---

### 3. Search Courses

**Endpoint:** `GET /api/v1/courses/search`

**Query Params:**
```typescript
interface SearchParams {
  query: string;         // Búsqueda por nombre, código o descripción
  page?: number;
  size?: number;
}
```

**Roles:** `ADMIN`, `TEACHER`

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: CourseDto[];
    // ...pagination
  };
  message: string;
  timestamp: string;
}
```

---

### 4. Get Courses by Teacher

**Endpoint:** `GET /api/v1/courses/teacher/{teacherId}`

**Query Params:**
```typescript
{
  page?: number;
  size?: number;
}
```

**Roles:** `ADMIN`, `TEACHER`

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: CourseDto[];
    // ...pagination
  };
  message: string;
  timestamp: string;
}
```

---

### 5. Create Course

**Endpoint:** `POST /api/v1/courses`

**Roles:** `ADMIN`

**Request:**
```typescript
interface CreateCourseRequest {
  code: string;                      // min: 3, max: 50, pattern: ^[A-Z0-9-]+$
  name: string;                      // min: 3, max: 200
  description: string;               // min: 10, max: 1000
  level: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  duration: number;                  // min: 1, max: 1000 hours
  maxStudents: number;               // min: 1, max: 100
  minStudents?: number;              // Default: 1
  teacherId: number;
  price: number;                     // Decimal, min: 0.0
  startDate?: string;                // ISO date (YYYY-MM-DD)
  endDate?: string;                  // ISO date (YYYY-MM-DD)
  isPublished?: boolean;             // Default: false
  schedules: CreateCourseScheduleRequest[]; // Al menos 1 horario
}

interface CreateCourseScheduleRequest {
  dayOfWeek: 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
  startTime: string;                 // HH:mm format
  endTime: string;                   // HH:mm format
}
```

**Response (201 Created):**
```typescript
{
  success: true;
  data: CourseDto;
  message: "Course created successfully";
  timestamp: string;
}
```

**Errores:**
- `400 Bad Request`: Validación fallida
- `409 Conflict`: Código de curso ya existe

---

### 6. Update Course

**Endpoint:** `PUT /api/v1/courses/{id}`

**Roles:** `ADMIN`

**Request:**
```typescript
interface UpdateCourseRequest {
  code?: string;
  name?: string;
  description?: string;
  level?: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  duration?: number;
  maxStudents?: number;
  minStudents?: number;
  teacherId?: number;
  price?: number;
  startDate?: string;
  endDate?: string;
  status?: 'ACTIVE' | 'INACTIVE' | 'COMPLETED' | 'CANCELLED';
  isPublished?: boolean;
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: CourseDto;
  message: "Course updated successfully";
  timestamp: string;
}
```

---

### 7. Delete Course

**Endpoint:** `DELETE /api/v1/courses/{id}`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: null;
  message: "Course deleted successfully";
  timestamp: string;
}
```

---

### 8. Enroll Student in Course

**Endpoint:** `POST /api/v1/courses/{id}/enroll`

**Roles:** `ADMIN`, `TEACHER` (solo cursos asignados)

**Request:**
```typescript
interface EnrollStudentRequest {
  studentId: number;
  notes?: string;
}
```

**Response (201 Created):**
```typescript
{
  success: true;
  data: EnrollmentDto;
  message: "Student enrolled successfully";
  timestamp: string;
}

interface EnrollmentDto {
  id: number;
  studentId: number;
  courseId: number;
  courseName: string;
  enrollmentDate: string;          // ISO date (YYYY-MM-DD)
  status: 'ACTIVE' | 'COMPLETED' | 'FAILED' | 'DROPPED' | 'SUSPENDED';
  finalGrade: number | null;       // Decimal (0.00 - 100.00)
  completionDate: string | null;   // ISO date (YYYY-MM-DD)
  notes: string | null;
  createdAt: string;               // ISO datetime
  updatedAt: string;               // ISO datetime
}
```

**Errores:**
- `409 Conflict`: Estudiante ya inscrito o curso lleno
- `404 Not Found`: Curso o estudiante no existe

---

### 9. Filter Courses

**Endpoint:** `POST /api/v1/courses/filter`

**Roles:** `ADMIN`, `TEACHER`

**Request:**
```typescript
interface CourseFilterRequest {
  level?: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  status?: 'ACTIVE' | 'INACTIVE' | 'COMPLETED' | 'CANCELLED';
  teacherId?: number;
  isPublished?: boolean;
  minPrice?: number;
  maxPrice?: number;
  hasAvailableSeats?: boolean;
}
```

**Query Params:**
```typescript
{
  page?: number;
  size?: number;
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: CourseDto[];
    // ...pagination
  };
  message: string;
  timestamp: string;
}
```

---

### 10. Get Published Courses (Public)

**Endpoint:** `GET /api/v1/courses/published`

**Roles:** Public (no authentication required)

**Query Params:**
```typescript
{
  page?: number;
  size?: number;
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: CourseSimpleDto[];
    // ...pagination
  };
  message: string;
  timestamp: string;
}

interface CourseSimpleDto {
  id: number;
  code: string;
  name: string;
  level: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  price: number;
  availableSeats: number;
  isEnrollmentOpen: boolean;
}
```

---

### 11. Get Course Statistics

**Endpoint:** `GET /api/v1/courses/statistics`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: CourseStatisticsDto;
  message: string;
  timestamp: string;
}

interface CourseStatisticsDto {
  totalCourses: number;
  activeCourses: number;
  publishedCourses: number;
  totalEnrollments: number;
  averageEnrollmentRate: number;   // Percentage
  coursesByLevel: {
    BEGINNER: number;
    INTERMEDIATE: number;
    ADVANCED: number;
  };
  coursesByStatus: {
    ACTIVE: number;
    INACTIVE: number;
    COMPLETED: number;
    CANCELLED: number;
  };
}
```

---

### 12. Publish Course

**Endpoint:** `PUT /api/v1/courses/{id}/publish`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: CourseDto;
  message: "Course published successfully";
  timestamp: string;
}
```

---

### 13. Unpublish Course

**Endpoint:** `PUT /api/v1/courses/{id}/unpublish`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: CourseDto;
  message: "Course unpublished successfully";
  timestamp: string;
}
```

---

### 14. Activate Course

**Endpoint:** `PUT /api/v1/courses/{id}/activate`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: CourseDto;
  message: "Course activated successfully";
  timestamp: string;
}
```

---

### 15. Deactivate Course

**Endpoint:** `PUT /api/v1/courses/{id}/deactivate`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: CourseDto;
  message: "Course deactivated successfully";
  timestamp: string;
}
```

---

## 📝 Enrollments Endpoints

### 1. Get Enrollment by ID

**Endpoint:** `GET /api/v1/enrollments/{id}`

**Roles:** `ADMIN`, `TEACHER`

**Response (200 OK):**
```typescript
{
  success: true;
  data: EnrollmentDto;
  message: string;
  timestamp: string;
}
```

**Errores:**
- `404 Not Found`: Enrollment no existe

---

### 2. Get Student Enrollments

**Endpoint:** `GET /api/v1/enrollments/student/{studentId}`

**Roles:** `ADMIN`, `TEACHER`, `GUARDIAN` (solo estudiantes propios)

**Query Params:**
```typescript
{
  page?: number;
  size?: number;
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: EnrollmentDto[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
  };
  message: string;
  timestamp: string;
}
```

---

### 3. Get Student Enrollment History

**Endpoint:** `GET /api/v1/enrollments/student/{studentId}/history`

**Roles:** `ADMIN`, `TEACHER`, `GUARDIAN` (solo estudiantes propios)

**Response (200 OK):**
```typescript
{
  success: true;
  data: StudentEnrollmentHistoryDto;
  message: string;
  timestamp: string;
}

interface StudentEnrollmentHistoryDto {
  studentId: number;
  enrollments: EnrollmentDto[];
  totalCourses: number;
  completedCourses: number;
  activeCourses: number;
}
```

---

### 4. Get Course Enrollments

**Endpoint:** `GET /api/v1/enrollments/course/{courseId}`

**Roles:** `ADMIN`, `TEACHER`

**Query Params:**
```typescript
{
  page?: number;
  size?: number;
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: EnrollmentDto[];
    // ...pagination
  };
  message: string;
  timestamp: string;
}
```

---

### 5. Update Enrollment

**Endpoint:** `PUT /api/v1/enrollments/{id}`

**Roles:** `ADMIN`, `TEACHER` (solo cursos asignados)

**Request:**
```typescript
interface UpdateEnrollmentRequest {
  status?: 'ACTIVE' | 'COMPLETED' | 'FAILED' | 'DROPPED' | 'SUSPENDED';
  finalGrade?: number;     // Decimal (0.00 - 100.00)
  notes?: string;
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: EnrollmentDto;
  message: "Enrollment updated successfully";
  timestamp: string;
}
```

---

### 6. Delete Enrollment

**Endpoint:** `DELETE /api/v1/enrollments/{id}`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: null;
  message: "Enrollment deleted successfully";
  timestamp: string;
}
```

---

## 📝 Exams Endpoints

> **Actualizado**: Mayo 2026 — alineado con backend real (`ExamController` + `ExamSubmissionController`).

### Tipos y enums relevantes

```typescript
type ExamId = string; // UUID
type SubmissionId = string; // UUID

type ExamStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'CANCELLED';
type ExamModality = 'OFFLINE' | 'ONLINE';
type SubmissionStatus = 'PENDING' | 'GRADED' | 'CANCELLED' | 'UNDER_REVIEW';

interface ExamDto {
  id: ExamId;
  courseId: number;
  title: string;
  description: string | null;
  modality: ExamModality;
  status: ExamStatus;
  totalPoints: number;
  weight: number;
  timeLimitMinutes: number | null;
  scheduledAt: string | null;
  visibilityStart: string | null;
  visibilityEnd: string | null;
  assignedTeachers: number[] | null;
  teacherNames: string[] | null;
  notes: string | null;
  roomInfo: string | null;
  version: number;
  createdAt: string;
  createdBy: number;
  updatedAt: string | null;
  updatedBy: number | null;
}

interface ExamSubmissionDto {
  id: SubmissionId;
  examId: ExamId;
  studentId: number;
  attemptNumber: number;
  status: SubmissionStatus;
  startedAt: string | null;
  submittedAt: string | null;
  score: number | null;
  gradedBy: number | null;
  gradedByName: string | null;
  gradedAt: string | null;
  feedback: string | null;
  scannedFilePath: string | null;
  notes: string | null;
  version: number;
  createdAt: string;
  createdBy: number;
}
```

---

### Exams (`/api/v1/exams`)

#### 1. Get Exam by ID
**Endpoint:** `GET /api/v1/exams/{id}`

#### 2. List Exams by Course
**Endpoint:** `GET /api/v1/exams/course/{courseId}`

**Query Params:**
```typescript
{
  status?: ExamStatus;
  page?: number;   // default 0
  size?: number;   // default 20
  sort?: string;   // default 'scheduledAt'
  order?: 'ASC' | 'DESC'; // default 'DESC'
}
```

#### 3. Get My Exams (docente autenticado)
**Endpoint:** `GET /api/v1/exams/my-exams`

**Query Params:**
```typescript
{
  courseIds: number[]; // requerido
  page?: number;
  size?: number;
}
```

#### 4. Get Visible Exams for Students
**Endpoint:** `GET /api/v1/exams/visible`

#### 5. Create Exam
**Endpoint:** `POST /api/v1/exams`

**Request:**
```typescript
interface CreateExamRequest {
  courseId: number;
  title: string;
  description?: string;
  totalPoints?: number;      // default 100.00
  weight?: number;           // default 1.00
  timeLimitMinutes?: number;
  scheduledAt?: string;
  visibilityStart?: string;
  visibilityEnd?: string;
  assignedTeachers?: number[];
  notes?: string;
  roomInfo?: string;
}
```

#### 6. Update Exam
**Endpoint:** `PUT /api/v1/exams/{id}`

**Request:**
```typescript
interface UpdateExamRequest {
  title?: string;
  description?: string;
  totalPoints?: number;
  weight?: number;
  timeLimitMinutes?: number;
  scheduledAt?: string;
  visibilityStart?: string;
  visibilityEnd?: string;
  assignedTeachers?: number[];
  notes?: string;
  roomInfo?: string;
}
```

#### 7. Publish Exam
**Endpoint:** `POST /api/v1/exams/{id}/publish`

#### 8. Close Exam
**Endpoint:** `POST /api/v1/exams/{id}/close`

#### 9. Cancel Exam
**Endpoint:** `POST /api/v1/exams/{id}/cancel`

#### 10. Delete Exam
**Endpoint:** `DELETE /api/v1/exams/{id}`

**Nota:** retorna `204 No Content`.

#### 11. Exam Statistics
**Endpoint:** `GET /api/v1/exams/{id}/statistics`

#### 12. Course Exam Statistics
**Endpoint:** `GET /api/v1/exams/course/{courseId}/statistics`

---

### Exam Submissions (`/api/v1/exam-submissions`)

#### 1. Get Submission by ID
**Endpoint:** `GET /api/v1/exam-submissions/{id}`

#### 2. List Submissions by Exam
**Endpoint:** `GET /api/v1/exam-submissions/exam/{examId}`

**Query Params:**
```typescript
{
  status?: SubmissionStatus;
  page?: number;   // default 0
  size?: number;   // default 50
  sort?: string;   // default 'createdAt'
  order?: 'ASC' | 'DESC'; // default 'ASC'
}
```

#### 3. List Submissions by Student
**Endpoint:** `GET /api/v1/exam-submissions/student/{studentId}`

#### 4. Student Exam History by Course
**Endpoint:** `GET /api/v1/exam-submissions/student/{studentId}/course/{courseId}/history`

#### 5. Create Submission
**Endpoint:** `POST /api/v1/exam-submissions`

```typescript
interface CreateSubmissionRequest {
  examId: ExamId;
  studentId: number;
  notes?: string;
}
```

#### 6. Grade Submission
**Endpoint:** `POST /api/v1/exam-submissions/{id}/grade`

```typescript
interface GradeSubmissionRequest {
  score: number;
  feedback?: string;
  notes?: string;
}
```

#### 7. Update Grade
**Endpoint:** `PUT /api/v1/exam-submissions/{id}/grade`

```typescript
interface UpdateGradeRequest {
  score: number;
  feedback?: string;
  reason: string;
}
```

#### 8. Attach Scanned File
**Endpoint:** `POST /api/v1/exam-submissions/{id}/attach-file?filePath={path}`

#### 9. Cancel Submission
**Endpoint:** `POST /api/v1/exam-submissions/{id}/cancel`

#### 10. Grade History
**Endpoint:** `GET /api/v1/exam-submissions/{id}/grade-history`

---

### Roles (resumen)

- `RequireAdminOrTeacher`: creación/edición/cierre/calificación.
- `RequireAdmin`: cancelar/eliminar en operaciones sensibles.
- Endpoints de consulta visibles (`/visible`, algunos listados) siguen autenticación JWT general del módulo.

---

### Teacher Performance (`/api/v1/teachers`)

```typescript
interface TeacherPerformanceDto {
  teacherId: number;
  fullName: string | null;
  totalExamCount: number;
  publishedExamCount: number;
  totalStudentsEvaluated: number;
  averageScore: number | null;
  passRate: number | null;
  courseExams: CourseExamSummaryDto[];
  recentExams: ExamSummaryDto[];
}

interface CourseExamSummaryDto {
  courseId: number;
  totalExams: number;
  averageScore: number | null;
  passRate: number | null;
  totalStudents: number;
}
```

#### 1. Teacher Performance by ID
**Endpoint:** `GET /api/v1/teachers/{teacherId}/performance`

#### 2. Teacher Exams
**Endpoint:** `GET /api/v1/teachers/{teacherId}/exams`

**Query Params:**
```typescript
{
  statuses?: ExamStatus[];
  page?: number;
  size?: number;
  sort?: string;
  order?: 'ASC' | 'DESC';
}
```

#### 3. Compare Teachers
**Endpoint:** `POST /api/v1/teachers/compare`

```typescript
interface CompareTeachersRequest {
  teacherIds: number[];
}
```

---

## 👔 Staff Endpoints

> **Actualizado**: Marzo 2026 — Integración completa con frontend (Angular).

---

### Personal Docente (`/api/v1/staff/teaching`)

---

#### 1. Listar Personal Docente

**Endpoint:** `GET /api/v1/staff/teaching`

**Roles:** `ADMIN`

**Query Params:**
```typescript
interface TeachingStaffListParams {
  page?: number;          // Default: 0
  limit?: number;         // Default: 10
  sort?: string;          // Default: 'lastName'
  order?: 'ASC' | 'DESC'; // Default: 'ASC'
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: TeachingStaffDto[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

interface TeachingStaffDto {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phoneNumber: string;
  documentNumber: string;
  birthDate: string;                // YYYY-MM-DD
  address: string;
  hireDate: string;                 // YYYY-MM-DD
  monthlySalary: number;
  paymentStatus: 'UP_TO_DATE' | 'PENDING' | 'OVERDUE' | 'PARTIALLY_PAID';
  status: 'ACTIVE' | 'INACTIVE';   // derivado de isActive
  assignedStudentsCount: number;
  assignedCourses: CourseAssignmentDto[] | null;
  specialization: string | null;
  observations: string | null;
  notes: string | null;
  emergencyContactName: string;
  emergencyContactPhone: string;
  attendanceStats: AttendanceStatsDto | null;
  totalWorkingDaysInMonth: number | null; // días laborales (L-V) del mes actual
  photoUrl: string | null;
  createdAt: string;                // ISO datetime
  updatedAt: string;                // ISO datetime
}

interface CourseAssignmentDto {
  courseId: number;
  courseName: string;
  level: string;
  enrolledStudents: number;
}

interface AttendanceStatsDto {
  totalDays: number;
  presentDays: number;
  absentDays: number;
  lateDays: number;
  attendanceRate: number;           // porcentaje (0-100) sobre días laborales del mes
}
```

---

#### 2. Obtener Detalle de Docente

**Endpoint:** `GET /api/v1/staff/teaching/{id}`

**Roles:** `ADMIN`, `TEACHER`

**Response (200 OK):**
```typescript
{
  success: true;
  data: TeachingStaffDto;           // incluye attendanceStats y totalWorkingDaysInMonth
}
```

---

#### 3. Buscar Personal Docente

**Endpoint:** `GET /api/v1/staff/teaching/search`

**Roles:** `ADMIN`

**Query Params:**
```typescript
{
  query: string;    // busca en firstName, lastName, email, documentNumber
  page?: number;
  limit?: number;
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: { content: TeachingStaffDto[]; /* ...paginación */ };
}
```

---

#### 4. Crear Docente

**Endpoint:** `POST /api/v1/staff/teaching`

**Roles:** `ADMIN`

**Request Body:**
```typescript
interface CreateTeachingStaffRequest {
  firstName: string;
  lastName: string;
  email: string;              // único
  phoneNumber: string;
  documentNumber: string;     // único
  birthDate: string;          // YYYY-MM-DD
  address: string;
  hireDate: string;           // YYYY-MM-DD
  monthlySalary: number;
  specialization?: string;
  observations?: string;
  notes?: string;
  qualifications?: string;    // alias de specialization

  // Contacto de emergencia — enviar de dos formas (se acepta cualquiera):
  emergencyContactName?: string;   // Forma 1: dos campos separados
  emergencyContactPhone?: string;
  emergencyContact?: string;       // Forma 2: "Nombre / Teléfono" (string único)
}
```

**Response (201 Created):**
```typescript
{
  success: true;
  data: TeachingStaffDto;
  message: "Teaching staff created successfully";
}
```

**Errores:**
- `400 Bad Request`: Validación fallida
- `409 Conflict` / `400`: Email o documentNumber ya existe

---

#### 5. Actualizar Docente

**Endpoint:** `PUT /api/v1/staff/teaching/{id}`

**Roles:** `ADMIN`

**Request Body:** (todos los campos son opcionales — solo se actualizan los enviados)
```typescript
interface UpdateTeachingStaffRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNumber?: string;
  address?: string;
  monthlySalary?: number;
  paymentStatus?: 'UP_TO_DATE' | 'PENDING' | 'OVERDUE' | 'PARTIALLY_PAID';
  specialization?: string;
  qualifications?: string;
  observations?: string;
  notes?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  emergencyContact?: string;      // "Nombre / Teléfono"
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: TeachingStaffDto;
  message: "Teaching staff updated successfully";
}
```

---

#### 6. Eliminar Docente (Soft Delete)

**Endpoint:** `DELETE /api/v1/staff/teaching/{id}`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  message: "Teaching staff deleted successfully";
}
```

---

#### 6.1 Resolver IDs de Docentes (Batch)

**Endpoint:** `POST /api/v1/staff/teaching/resolve`

**Roles:** `ADMIN`, `TEACHER`

**Request Body:**
```typescript
interface ResolveTeachersRequest {
  ids: number[];
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: TeacherResolutionDto[];
}

interface TeacherResolutionDto {
  id: number;
  fullName: string;
}
```

---

### Personal No Docente (`/api/v1/staff/non-teaching`)

---

#### 7. Listar Personal No Docente

**Endpoint:** `GET /api/v1/staff/non-teaching`

**Roles:** `ADMIN`

**Query Params:**
```typescript
{
  page?: number;
  limit?: number;
  sort?: string;
  order?: 'ASC' | 'DESC';
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: NonTeachingStaffDto[];
    /* ...paginación */
  };
}

interface NonTeachingStaffDto {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phoneNumber: string;
  documentNumber: string;
  birthDate: string;              // YYYY-MM-DD
  address: string;
  hireDate: string;               // YYYY-MM-DD
  hourlyRate: number;
  role: 'CLEANING' | 'MAINTENANCE' | 'IT_SUPPORT' | 'IT' | 'SECURITY' | 'ADMINISTRATION' | 'OTHER';
  position: string;               // alias de role.name — para compatibilidad frontend
  companyName: string;
  company: string;                // alias de companyName — para compatibilidad frontend
  assignedTasks: string | null;
  observations: string | null;
  emergencyContactName: string;
  emergencyContactPhone: string;
  status: 'ACTIVE' | 'INACTIVE';
  attendanceStats: AttendanceStatsDto | null;
  hoursWorkedThisMonth: number | null;
  estimatedEarningsThisMonth: number | null;
  photoUrl: string | null;
  createdAt: string;
  updatedAt: string;
}
```

> **Nota de nomenclatura**: El frontend usa `position` y `company`. El backend expone ambos nombres en la respuesta para máxima compatibilidad.

---

#### 8. Obtener Detalle de No Docente

**Endpoint:** `GET /api/v1/staff/non-teaching/{id}`

**Roles:** `ADMIN`, `TEACHER`

**Response (200 OK):**
```typescript
{
  success: true;
  data: NonTeachingStaffDto;    // incluye hoursWorkedThisMonth, estimatedEarningsThisMonth y attendanceStats
}
```

---

#### 9. Filtrar No Docentes por Rol

**Endpoint:** `GET /api/v1/staff/non-teaching/by-role/{role}`

**Roles:** `ADMIN`

**Path Param:** `role` — uno de: `CLEANING | MAINTENANCE | IT_SUPPORT | IT | SECURITY | ADMINISTRATION | OTHER`

**Query Params:** `page`, `limit`

**Response (200 OK):** Lista paginada de `NonTeachingStaffDto`

---

#### 10. Buscar Personal No Docente

**Endpoint:** `GET /api/v1/staff/non-teaching/search`

**Roles:** `ADMIN`

**Query Params:**
```typescript
{
  query: string;    // busca en firstName, lastName, email, documentNumber, companyName
  page?: number;
  limit?: number;
}
```

---

#### 11. Crear No Docente

**Endpoint:** `POST /api/v1/staff/non-teaching`

**Roles:** `ADMIN`

**Request Body:**
```typescript
interface CreateNonTeachingStaffRequest {
  firstName: string;
  lastName: string;
  email: string;            // único
  phoneNumber: string;
  documentNumber: string;   // único
  birthDate: string;        // YYYY-MM-DD
  address: string;
  hireDate: string;         // YYYY-MM-DD
  hourlyRate: number;

  // Rol — enviar 'role' O 'position' (se acepta cualquiera):
  role?: 'CLEANING' | 'MAINTENANCE' | 'IT_SUPPORT' | 'IT' | 'SECURITY' | 'ADMINISTRATION' | 'OTHER';
  position?: 'CLEANING' | 'MAINTENANCE' | 'IT_SUPPORT' | 'IT' | 'SECURITY' | 'ADMINISTRATION' | 'OTHER';

  // Empresa — enviar 'companyName' O 'company':
  companyName?: string;
  company?: string;

  assignedTasks?: string;
  observations?: string;

  // Contacto de emergencia:
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  emergencyContact?: string;     // "Nombre / Teléfono"
}
```

**Response (201 Created):**
```typescript
{
  success: true;
  data: NonTeachingStaffDto;
  message: "Non-teaching staff created successfully";
}
```

---

#### 12. Actualizar No Docente

**Endpoint:** `PUT /api/v1/staff/non-teaching/{id}`

**Roles:** `ADMIN`

**Request Body:** (todos opcionales)
```typescript
interface UpdateNonTeachingStaffRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNumber?: string;
  address?: string;
  hourlyRate?: number;
  role?: NonTeachingRole;
  position?: NonTeachingRole;   // alias de role
  companyName?: string;
  company?: string;             // alias de companyName
  assignedTasks?: string;
  observations?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  emergencyContact?: string;
}
```

**Response (200 OK):**
```typescript
{
  success: true;
  data: NonTeachingStaffDto;
  message: "Non-teaching staff updated successfully";
}
```

---

#### 13. Eliminar No Docente (Soft Delete)

**Endpoint:** `DELETE /api/v1/staff/non-teaching/{id}`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  message: "Non-teaching staff deleted successfully";
}
```

---

### Asistencia de Personal (`/api/v1/staff/attendance`)

---

#### 14. Registrar Asistencia

**Endpoint:** `POST /api/v1/staff/attendance`

**Roles:** `ADMIN`

**Request Body:**
```typescript
interface CreateAttendanceRequest {
  teachingStaffId?: number;       // uno de los dos es requerido
  nonTeachingStaffId?: number;
  attendanceDate: string;         // YYYY-MM-DD
  checkInTime?: string;           // HH:mm
  checkOutTime?: string;          // HH:mm
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED' | 'SICK_LEAVE' | 'VACATION';
  notes?: string;
  hoursWorked?: number;
}
```

**Response (201 Created):** `StaffAttendanceDto`

---

#### 15. Actualizar Asistencia

**Endpoint:** `PUT /api/v1/staff/attendance/{id}`

**Roles:** `ADMIN`

---

#### 16. Obtener Asistencia de Docente

**Endpoint:** `GET /api/v1/staff/attendance/teaching/{staffId}`

**Query Params:** `startDate`, `endDate` (YYYY-MM-DD), `page`, `limit`

---

#### 17. Obtener Asistencia de No Docente

**Endpoint:** `GET /api/v1/staff/attendance/non-teaching/{staffId}`

**Query Params:** `startDate`, `endDate` (YYYY-MM-DD), `page`, `limit`

---

#### 18. Eliminar Registro de Asistencia

**Endpoint:** `DELETE /api/v1/staff/attendance/{id}`

**Roles:** `ADMIN`

---

### Enumeraciones del Módulo Staff

```typescript
// Roles de personal no docente
type NonTeachingRole =
  | 'CLEANING'        // Personal de limpieza
  | 'MAINTENANCE'     // Mantenimiento
  | 'IT_SUPPORT'      // Soporte IT (también acepta 'IT' como alias)
  | 'IT'              // Alias de IT_SUPPORT
  | 'SECURITY'        // Seguridad
  | 'ADMINISTRATION'  // Administrativo
  | 'OTHER';          // Otro

// Estado de pago de docentes
type PaymentStatus =
  | 'UP_TO_DATE'      // Al día
  | 'PENDING'         // Pendiente
  | 'OVERDUE'         // Vencido
  | 'PARTIALLY_PAID'; // Pago parcial

// Estado de asistencia
type AttendanceStatus =
  | 'PRESENT'
  | 'ABSENT'
  | 'LATE'
  | 'EXCUSED'
  | 'SICK_LEAVE'
  | 'VACATION';
```

---

### Notas de Compatibilidad Frontend ↔ Backend

| Campo frontend | Campo backend | Resolución |
|---|---|---|
| `position` | `role` | Ambos se aceptan en requests; ambos se devuelven en responses |
| `company` | `companyName` | Idem |
| `emergencyContact` (string único) | `emergencyContactName` + `emergencyContactPhone` | El backend acepta ambas formas; split automático por `/` |
| `IT` | `IT_SUPPORT` | Ambos valores se aceptan; la respuesta incluye el valor exacto del enum |
| `status: 'ACTIVE'/'INACTIVE'` | `isActive: boolean` | El backend convierte automáticamente en la respuesta |
| `totalWorkingDaysInMonth` | — | Calculado por el backend (días L-V del mes actual) |



## 💳 Payments Endpoints

> **Nota**: Este módulo está en desarrollo. Los endpoints serán documentados próximamente.

---

## 📊 Reports Endpoints

> **Nota**: Este módulo está en desarrollo. Los endpoints serán documentados próximamente.

---

## 🔔 Communications/Notifications Endpoints

> **Nota**: Este módulo está en desarrollo. Los endpoints serán documentados próximamente.

---

## ⚠️ Error Codes

| HTTP Status | Código | Descripción |
|-------------|--------|-------------|
| 400 | Bad Request | Validación de entrada fallida |
| 401 | Unauthorized | No autenticado o token inválido |
| 403 | Forbidden | No tiene permisos para esta operación |
| 404 | Not Found | Recurso no encontrado |
| 409 | Conflict | Conflicto (ej: recurso duplicado) |
| 422 | Unprocessable Entity | Error de lógica de negocio |
| 429 | Too Many Requests | Rate limit excedido |
| 500 | Internal Server Error | Error del servidor |

---

## 📝 Notas para Integración Frontend

### 1. Manejo de Tokens

```typescript
// Servicio de autenticación (ejemplo Angular)
export class AuthService {
  private readonly TOKEN_KEY = 'access_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';

  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', credentials)
      .pipe(
        tap(response => {
          localStorage.setItem(this.TOKEN_KEY, response.data.token);
          localStorage.setItem(this.REFRESH_TOKEN_KEY, response.data.refreshToken);
        })
      );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }
}
```

### 2. HTTP Interceptor

```typescript
// Interceptor para agregar token automáticamente
export class AuthInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.authService.getToken();
    
    if (token) {
      req = req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }
    
    return next.handle(req).pipe(
      catchError(error => {
        if (error.status === 401) {
          // Token expirado, intentar refresh
          return this.handle401Error(req, next);
        }
        return throwError(error);
      })
    );
  }
}
```

### 3. Paginación

```typescript
// Ejemplo de uso de paginación
interface PaginationParams {
  page: number;
  size: number;
  sort?: string;
  order?: 'ASC' | 'DESC';
}

getStudents(params: PaginationParams): Observable<PageResponse<StudentDto>> {
  const httpParams = new HttpParams()
    .set('page', params.page.toString())
    .set('size', params.size.toString())
    .set('sort', params.sort || 'id')
    .set('order', params.order || 'ASC');
    
  return this.http.get<PageResponse<StudentDto>>('/api/v1/students', { params: httpParams });
}
```

### 4. Manejo de Errores

```typescript
// Ejemplo de manejo centralizado de errores
export class ErrorHandlerService {
  handleError(error: HttpErrorResponse): string {
    if (error.error?.errors && Array.isArray(error.error.errors)) {
      return error.error.errors.join(', ');
    }
    return error.error?.message || 'An unexpected error occurred';
  }
}
```

---

## 🔄 Versionado de API

- **Versión actual**: `v1`
- Los cambios breaking se comunicarán con anticipación
- Se mantendrá compatibilidad con versiones anteriores cuando sea posible
- Nuevas versiones se introducirán bajo nuevos prefijos (`/api/v2`, etc.)

---

## 📞 Soporte

Para dudas o reportes de errores en la API:
- **Email**: dev@sigep.edu.mx
- **Documentación Swagger**: `http://localhost:8080/swagger-ui/index.html`

---

**Última actualización**: Noviembre 2025  
**Versión del contrato**: 1.0.0

