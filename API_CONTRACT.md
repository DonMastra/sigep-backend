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
  active: boolean;
}
```

**Errores:**
- `401 Unauthorized`: Credenciales inválidas
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
  role: 'ADMIN' | 'TEACHER' | 'GUARDIAN';
}
```

**Response (201 Created):**
```typescript
{
  success: true;
  data: UserDto;
  message: "User registered successfully";
}
```

**Errores:**
- `400 Bad Request`: Validación fallida
- `409 Conflict`: Username o email ya existe

---

### 3. Refresh Token

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

### 4. Logout

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

### 1. List Exams

**Endpoint:** `GET /api/v1/exams`

**Query Params:**
```typescript
interface ExamListParams {
  page?: number;
  size?: number;
  courseId?: number;
  status?: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
}
```

**Roles:** `ADMIN`, `TEACHER`

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: ExamDto[];
    // ...pagination
  };
}

interface ExamDto {
  id: number;
  title: string;
  description: string;
  courseId: number;
  courseName: string;
  examType: 'WRITTEN' | 'ORAL' | 'PRACTICAL' | 'FINAL';
  scheduledDate: string;
  duration: number;        // minutes
  totalPoints: number;
  passingScore: number;
  status: 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  createdBy: number;
  createdByName: string;
  createdAt: string;
}
```

---

### 2. Get Exam with Grades

**Endpoint:** `GET /api/v1/exams/{id}/grades`

**Roles:** `ADMIN`, `TEACHER`, `GUARDIAN` (solo estudiantes propios)

**Response (200 OK):**
```typescript
{
  success: true;
  data: ExamWithGradesDto;
}

interface ExamWithGradesDto extends ExamDto {
  grades: ExamGradeDto[];
  averageScore: number;
  passRate: number;
}

interface ExamGradeDto {
  id: number;
  studentId: number;
  studentName: string;
  score: number;
  maxScore: number;
  percentage: number;
  passed: boolean;
  feedback: string | null;
  gradedAt: string;
  gradedBy: string;
}
```

---

### 3. Create Exam

**Endpoint:** `POST /api/v1/exams`

**Roles:** `ADMIN`, `TEACHER`

**Request:**
```typescript
interface CreateExamRequest {
  title: string;
  description: string;
  courseId: number;
  examType: 'WRITTEN' | 'ORAL' | 'PRACTICAL' | 'FINAL';
  scheduledDate: string;   // ISO datetime
  duration: number;
  totalPoints: number;
  passingScore: number;
}
```

**Response (201 Created):**
```typescript
{
  success: true;
  data: ExamDto;
  message: "Exam created successfully";
}
```

---

### 4. Submit Grade

**Endpoint:** `POST /api/v1/exams/{examId}/grades`

**Roles:** `ADMIN`, `TEACHER` (solo exámenes de cursos asignados)

**Request:**
```typescript
interface SubmitGradeRequest {
  studentId: number;
  score: number;
  feedback?: string;
}
```

**Response (201 Created):**
```typescript
{
  success: true;
  data: ExamGradeDto;
  message: "Grade submitted successfully";
}
```

---

## 👔 Staff Endpoints

### 1. List Teaching Staff

**Endpoint:** `GET /api/v1/staff/teaching`

**Query Params:**
```typescript
interface TeachingStaffParams {
  page?: number;
  size?: number;
  status?: 'ACTIVE' | 'INACTIVE' | 'ON_LEAVE';
}
```

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: TeachingStaffDto[];
    // ...pagination
  };
}

interface TeachingStaffDto {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  specialization: string;
  hireDate: string;
  status: 'ACTIVE' | 'INACTIVE' | 'ON_LEAVE';
  assignedStudentsCount: number;
  assignedCoursesCount: number;
  monthlySalary: number;
  paymentStatus: 'UP_TO_DATE' | 'PENDING' | 'OVERDUE';
  createdAt: string;
  updatedAt: string;
}
```

---

### 2. Get Teaching Staff Details

**Endpoint:** `GET /api/v1/staff/teaching/{id}`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: TeachingStaffDetailDto;
}

interface TeachingStaffDetailDto extends TeachingStaffDto {
  documentNumber: string;
  address: string;
  emergencyContact: string;
  qualifications: string;
  assignedCourses: CourseDto[];
  assignedStudents: StudentDto[];
  attendanceRecords: AttendanceRecordDto[];
  notes: string | null;
}

interface AttendanceRecordDto {
  id: number;
  date: string;
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED';
  notes: string | null;
}
```

---

### 3. List Non-Teaching Staff

**Endpoint:** `GET /api/v1/staff/non-teaching`

**Roles:** `ADMIN`

**Response (200 OK):**
```typescript
{
  success: true;
  data: {
    content: NonTeachingStaffDto[];
    // ...pagination
  };
}

interface NonTeachingStaffDto {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  position: 'CLEANING' | 'MAINTENANCE' | 'IT' | 'ADMINISTRATION' | 'OTHER';
  company: string;
  hourlyRate: number;
  assignedTasks: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
}
```

---

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

