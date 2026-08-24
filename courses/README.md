# Sistema de Gestión de Cursos - Módulo Courses

## 📋 Resumen de Funcionalidades Implementadas

Este módulo proporciona un sistema completo de gestión de cursos para un instituto de enseñanza de inglés, incluyendo:

### 1. ✅ Gestión de Cursos (Refactorizada)
- Cursos con código único, niveles estandarizados y precios
- Control de publicación y capacidad (mínima y máxima)
- Filtros avanzados y búsquedas
- Estadísticas completas
- Endpoints de activación/desactivación

### 2. ✅ Sistema de Asistencia
- Registro individual y masivo de asistencia
- Estados: PRESENT, ABSENT, LATE, EXCUSED_ABSENCE, SICK_LEAVE
- Estadísticas de asistencia por estudiante
- Reportes por curso y fecha
- Integración con sesiones de curso

### 3. ✅ Materiales del Curso
- Gestión de materiales (PDF, VIDEO, AUDIO, DOCUMENT, etc.)
- Control de visibilidad para estudiantes
- Ordenamiento personalizable
- Estadísticas por tipo de material

### 4. ✅ Sistema de Certificados
- Emisión automática de certificados con código único
- Determinación automática de honores según calificación
- Verificación pública de certificados (endpoint sin autenticación)
- Revocación de certificados con razón
- Manejo de expiración de certificados

### 5. ✅ Sistema de Notificaciones (Eventos)
- Eventos publicados cuando:
  - Se emite un certificado
  - Se sube nuevo material
  - Se publica un curso
  - Se registra asistencia
- Arquitectura de eventos lista para integración con módulo de comunicaciones

### 6. ✅ Sistema de Horarios y Aulas (CourseSession)
- Gestión completa de sesiones de curso
- Generación de sesiones recurrentes
- Asignación de aulas
- **Detección de conflictos** para:
  - Docentes (no pueden tener 2 clases al mismo tiempo)
  - Estudiantes (no pueden estar en 2 cursos simultáneos)
  - Aulas (no pueden ser asignadas a 2 cursos simultáneamente)
- Excepciones de sesión (cancelaciones, reprogramaciones, cambios de aula)
- Integración con asistencia (resumen de asistencia por sesión)
- Calendario de sesiones

---

## 🎯 Endpoints Principales

### Cursos
- `GET /api/v1/courses` - Listar cursos (paginado)
- `GET /api/v1/courses/{id}` - Obtener curso por ID
- `POST /api/v1/courses` - Crear curso
- `PUT /api/v1/courses/{id}` - Actualizar curso
- `DELETE /api/v1/courses/{id}` - Eliminar curso
- `POST /api/v1/courses/filter` - Filtrado avanzado
- `GET /api/v1/courses/published` - Cursos publicados
- `GET /api/v1/courses/statistics` - Estadísticas
- `PUT /api/v1/courses/{id}/publish` - Publicar curso
- `PUT /api/v1/courses/{id}/activate` - Activar curso

### Asistencia
- `POST /api/v1/attendance` - Registrar asistencia individual
- `POST /api/v1/attendance/bulk` - Registro masivo por curso y fecha; resuelve o crea internamente la sesión desde el horario asignado
- `GET /api/v1/attendance/enrollment/{enrollmentId}/statistics` - Estadísticas
- `GET /api/v1/attendance/course/{courseId}/report/{date}` - Reporte detallado
- `GET /api/v1/attendance/course/{courseId}/statistics` - Acumulado del curso y porcentajes por estudiante

La sesión se conserva como ancla de trazabilidad para horarios, aula, cancelaciones,
reprogramaciones e idempotencia. El usuario solo debe elegirla cuando un curso tiene
más de una clase en la misma fecha.

### Materiales
- `POST /api/v1/materials` - Subir material
- `GET /api/v1/materials/course/{courseId}` - Materiales por curso
- `PUT /api/v1/materials/course/{courseId}/reorder` - Reordenar
- `PUT /api/v1/materials/{id}/toggle-visibility` - Cambiar visibilidad

### Certificados
- `POST /api/v1/certificates` - Emitir certificado
- `GET /api/v1/certificates/verify/{code}` - **Verificación pública** ⚠️ Sin autenticación
- `POST /api/v1/certificates/{id}/revoke` - Revocar certificado
- `GET /api/v1/certificates/statistics` - Estadísticas

### Sesiones y Horarios
- `POST /api/v1/sessions` - Crear sesión
- `POST /api/v1/sessions/recurring` - Generar sesiones recurrentes
- `POST /api/v1/sessions/check-conflicts` - Verificar conflictos
- `GET /api/v1/sessions/{id}/attendance-summary` - Resumen de asistencia
- `GET /api/v1/sessions/calendar` - Calendario de sesiones
- `POST /api/v1/sessions/exceptions` - Crear excepción

---

## 🔐 Seguridad y Permisos

### Roles requeridos por endpoint:

**ADMIN:**
- Estadísticas generales
- Eliminaciones
- Revocación de certificados
- Procesamiento de certificados expirados

**ADMIN o TEACHER:**
- Gestión de cursos, materiales, asistencia
- Emisión de certificados
- Gestión de sesiones

**Público (sin autenticación):**
- `GET /api/v1/certificates/verify/{code}` - Verificación de certificados

---

## 📊 Modelo de Datos

### Tablas Principales:

1. **courses** - Información de cursos
2. **course_schedules** - Horarios semanales recurrentes
3. **enrollments** - Inscripciones de estudiantes
4. **course_attendance** - Registros de asistencia
5. **course_materials** - Materiales del curso
6. **course_certificates** - Certificados emitidos
7. **course_sessions** - Sesiones individuales con aula asignada
8. **session_exceptions** - Excepciones a sesiones recurrentes

### Relaciones Clave:

```
Course 1----* Enrollment ----* Student
Course 1----* CourseSchedule
Course 1----* CourseSession
Course 1----* CourseMaterial
Enrollment 1----1 Certificate
Enrollment 1----* Attendance
CourseSession 1----* SessionException
```

---

## 🚀 Funcionalidades Destacadas

### 1. Detección de Conflictos de Horario

El sistema detecta automáticamente conflictos cuando se intenta crear o actualizar una sesión:

```kotlin
// Ejemplo: Verificar conflictos antes de crear sesión
POST /api/v1/sessions/check-conflicts
{
  "teacherId": 1,
  "classroomId": 5,
  "date": "2025-01-25",
  "startTime": "09:00",
  "endTime": "11:00"
}

// Respuesta:
{
  "hasConflict": true,
  "conflictType": "TEACHER, CLASSROOM",
  "conflicts": [...],
  "message": "Found 2 conflict(s) for TEACHER, CLASSROOM"
}
```

### 2. Generación de Sesiones Recurrentes

Genera múltiples sesiones automáticamente:

```kotlin
POST /api/v1/sessions/recurring
{
  "courseId": 1,
  "startDate": "2025-02-01",
  "endDate": "2025-04-30",
  "startTime": "09:00",
  "endTime": "11:00",
  "daysOfWeek": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "classroomId": 5
}
```

### 3. Sistema de Eventos para Notificaciones

Eventos publicados automáticamente:
- `CertificateIssuedEvent` - Cuando se emite un certificado
- `CourseMaterialUploadedEvent` - Cuando se sube nuevo material
- `CoursePublishedEvent` - Cuando se publica un curso
- `AttendanceRecordedEvent` - Cuando se registra asistencia

Estos eventos pueden ser consumidos por el módulo de comunicaciones para enviar notificaciones.

### 4. Integración Asistencia-Sesiones

Cada sesión muestra automáticamente:
- Cantidad de estudiantes que asistieron
- Cantidad esperada de estudiantes
- Tasa de asistencia

```kotlin
GET /api/v1/sessions/{id}/attendance-summary
```

### 5. Certificados con Código Único Verificable

Formato: `CERT-2025-XXXXXXXX`
- Código único generado automáticamente
- Verificación pública sin autenticación
- Honores automáticos según calificación:
  - 95+: "With Highest Honors"
  - 90+: "With High Honors"
  - 85+: "With Honors"

---

## 📝 Scripts de Migración

Los scripts SQL están disponibles en:
- `scripts/migrations/V1__create_courses_module.sql` - Tablas principales
- `scripts/migrations/V2__create_sessions_module.sql` - Sistema de sesiones

---

## 🔄 Próximas Mejoras Sugeridas

1. **Generación de PDFs para certificados** - Integrar iText o Apache PDFBox
2. **Almacenamiento de archivos** - Integrar AWS S3 o MinIO para materiales
3. **Exportación ICS** - Exportar calendario en formato iCalendar
4. **Integración con módulo de comunicaciones** - Consumir eventos para enviar emails/SMS
5. **Dashboard con métricas** - Gráficos de asistencia, certificados emitidos, etc.
6. **Gestión de aulas** - Módulo dedicado para gestión de aulas/salones

---

## 🛠️ Tecnologías Utilizadas

- **Kotlin** - Lenguaje principal
- **Spring Boot 3.x** - Framework
- **Spring Data JPA** - Persistencia
- **PostgreSQL** - Base de datos
- **Spring Events** - Sistema de eventos
- **Swagger/OpenAPI** - Documentación API
- **Hibernate** - ORM
- **Spring Security** - Seguridad

---

## 📚 Documentación Adicional

Para más detalles sobre la arquitectura y diseño, consultar:
- `ARCHITECTURE.md` - Arquitectura general del proyecto
- `AUTHENTICATION_GUIDE.md` - Guía de autenticación y autorización
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

---

## ✅ Estado del Módulo

**BUILD SUCCESSFUL** ✓

Todas las funcionalidades están implementadas, probadas y listas para uso.

