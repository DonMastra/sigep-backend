# Módulo de Exámenes (Exams)

## Descripción

Módulo para la gestión de exámenes presenciales y carga de calificaciones del instituto de inglés.

### Fase 1 - Funcionalidad Actual (Offline/Presencial)
- Gestión completa de exámenes presenciales
- Registro de estudiantes que rindieron exámenes
- Carga y gestión de calificaciones
- Historial de cambios de notas (auditoría)
- Adjuntar archivos escaneados de exámenes
- Estadísticas y reportes por examen y curso
- Control de acceso por roles (Admin, Teacher, Student)

### Fase 2 - Funcionalidad Futura (Online)
- Banco de preguntas reutilizable
- Exámenes online con auto-corrección
- Configuración de secciones y preguntas
- Temporizador y límites de intentos
- Aleatorización de preguntas
- Feedback automático

## Arquitectura

### Domain Layer (DDD)
- **Aggregates**: `Exam`, `ExamSubmission`, `ExamGradeHistory`
- **Value Objects**: `ExamStatus`, `ExamModality`, `SubmissionStatus`
- **Repositories**: Interfaces de persistencia

### Application Layer
- **Services**: Lógica de negocio y casos de uso
  - `ExamService`: Gestión de exámenes
  - `ExamSubmissionService`: Gestión de calificaciones
  - `ExamStatisticsService`: Estadísticas y reportes
- **DTOs**: Objetos de transferencia de datos

### Infrastructure Layer
- **Config**: Configuración del módulo
- **Migrations**: Scripts Flyway para base de datos

### Presentation Layer
- **Controllers**: Endpoints REST
  - `ExamController`: CRUD de exámenes
  - `ExamSubmissionController`: Gestión de calificaciones

## Modelo de Datos

### Exam (Examen)
- Información del examen
- Curso asociado
- Puntaje total y peso relativo
- Fecha programada
- Ventana de visibilidad para estudiantes
- Docentes asignados
- Estado del examen

### ExamSubmission (Intento de Examen)
- Estudiante y examen
- Número de intento
- Calificación
- Feedback del docente
- Archivo escaneado adjunto
- Estado del submission

### ExamGradeHistory (Historial de Calificación)
- Auditoría de cambios de notas
- Calificación anterior y nueva
- Razón del cambio
- Quién realizó el cambio

## Estados del Examen

1. **DRAFT** - Borrador (solo visible para docentes/admin)
2. **PUBLISHED** - Publicado (visible para estudiantes)
3. **CLOSED** - Cerrado (calificaciones finalizadas)
4. **CANCELLED** - Cancelado

## Estados del Submission

1. **PENDING** - Pendiente de calificación
2. **GRADED** - Calificado
3. **CANCELLED** - Cancelado
4. **UNDER_REVIEW** - En revisión (para apelaciones)

## API Endpoints

### Exámenes

#### GET `/api/v1/exams/{id}`
Obtener examen por ID

#### GET `/api/v1/exams/course/{courseId}`
Listar exámenes de un curso
- Query params: `status`, `page`, `size`, `sort`, `order`

#### GET `/api/v1/exams/my-exams`
Obtener exámenes del docente autenticado
- Requiere: `TEACHER` o `ADMIN`

#### GET `/api/v1/exams/visible`
Obtener exámenes visibles para estudiantes

#### POST `/api/v1/exams`
Crear nuevo examen
- Requiere: `TEACHER` o `ADMIN`
- Body: `CreateExamRequest`

#### PUT `/api/v1/exams/{id}`
Actualizar examen existente
- Requiere: `TEACHER` o `ADMIN`
- Body: `UpdateExamRequest`

#### POST `/api/v1/exams/{id}/publish`
Publicar examen
- Requiere: `TEACHER` o `ADMIN`

#### POST `/api/v1/exams/{id}/close`
Cerrar examen
- Requiere: `TEACHER` o `ADMIN`

#### POST `/api/v1/exams/{id}/cancel`
Cancelar examen
- Requiere: `ADMIN`

#### DELETE `/api/v1/exams/{id}`
Eliminar examen (solo borradores sin submissions)
- Requiere: `ADMIN`

#### GET `/api/v1/exams/{id}/statistics`
Obtener estadísticas del examen
- Requiere: `TEACHER` o `ADMIN`

#### GET `/api/v1/exams/course/{courseId}/statistics`
Obtener estadísticas de todos los exámenes del curso
- Requiere: `TEACHER` o `ADMIN`

### Submissions (Calificaciones)

#### GET `/api/v1/exam-submissions/{id}`
Obtener submission por ID

#### GET `/api/v1/exam-submissions/exam/{examId}`
Listar submissions de un examen
- Requiere: `TEACHER` o `ADMIN`
- Query params: `status`, `page`, `size`, `sort`, `order`

#### GET `/api/v1/exam-submissions/student/{studentId}`
Listar submissions de un estudiante

#### GET `/api/v1/exam-submissions/student/{studentId}/course/{courseId}/history`
Obtener historial de exámenes de un estudiante en un curso

#### POST `/api/v1/exam-submissions`
Registrar que un estudiante rindió el examen
- Requiere: `TEACHER` o `ADMIN`
- Body: `CreateSubmissionRequest`

#### POST `/api/v1/exam-submissions/{id}/grade`
Calificar un submission
- Requiere: `TEACHER` o `ADMIN`
- Body: `GradeSubmissionRequest`

#### PUT `/api/v1/exam-submissions/{id}/grade`
Actualizar calificación existente
- Requiere: `TEACHER` o `ADMIN`
- Body: `UpdateGradeRequest`

#### POST `/api/v1/exam-submissions/{id}/attach-file`
Adjuntar archivo escaneado del examen
- Requiere: `TEACHER` o `ADMIN`
- Query param: `filePath`

#### POST `/api/v1/exam-submissions/{id}/cancel`
Cancelar un submission
- Requiere: `ADMIN`

#### GET `/api/v1/exam-submissions/{id}/grade-history`
Obtener historial de cambios de calificación
- Requiere: `TEACHER` o `ADMIN`

## Reglas de Negocio

### Exámenes
1. Solo se pueden publicar exámenes en estado `DRAFT`
2. Solo se pueden cerrar exámenes en estado `PUBLISHED`
3. No se pueden editar exámenes cerrados
4. Solo se pueden eliminar exámenes en `DRAFT` sin submissions
5. Los exámenes se muestran a estudiantes según ventana de visibilidad
6. Los docentes solo pueden gestionar exámenes de cursos asignados

### Calificaciones
1. El puntaje no puede superar el total de puntos del examen
2. Se guarda historial automático al cambiar una calificación
3. No se pueden calificar submissions cancelados
4. Solo se pueden cancelar submissions no calificados
5. Cada estudiante puede tener múltiples intentos (registrados con `attempt_number`)

## Integración con Otros Módulos

- **Courses**: Los exámenes pertenecen a cursos
- **Students**: Las calificaciones se asocian a estudiantes
- **Security**: Control de acceso por roles
- **Communications**: Notificaciones de exámenes publicados/calificados (futuro)
- **Scheduling**: Integración con calendario (futuro)
- **Reports**: Exportación de estadísticas (futuro)

## Caché

Se utiliza Spring Cache para:
- `exams`: Caché de exámenes individuales
- `submissions`: Caché de submissions individuales

Las operaciones de escritura invalidan el caché correspondiente.

## Base de Datos

### Migraciones
- `V5__create_exams_module.sql`: Creación de tablas y estructura inicial

### Tablas
- `exams`: Información de exámenes
- `exam_submissions`: Intentos y calificaciones
- `exam_grade_history`: Historial de cambios de notas

### Índices
- Por curso, estado, fecha programada
- Por estudiante, examen
- Unique constraint: exam_id + student_id + attempt_number

## Seguridad

### Roles y Permisos

**ADMIN**
- CRUD completo de exámenes
- Gestión completa de calificaciones
- Acceso a todas las estadísticas
- Puede cancelar y eliminar

**TEACHER**
- CRUD de exámenes en cursos asignados
- Calificar estudiantes de sus cursos
- Ver estadísticas de sus exámenes
- Publicar y cerrar exámenes

**STUDENT**
- Ver exámenes publicados y visibles
- Ver sus propias calificaciones
- Ver historial de exámenes del curso

## Estadísticas Disponibles

### Por Examen
- Total de estudiantes
- Cantidad de submissions
- Cantidad calificados/pendientes
- Promedio de notas
- Nota más alta/baja
- Tasa de aprobación
- Distribución de notas por rangos

### Por Curso
- Total de exámenes
- Exámenes publicados/cerrados
- Promedio general del curso
- Estadísticas de cada examen

## Ejemplo de Uso

### 1. Crear un examen
```bash
POST /api/v1/exams
{
  "courseId": "uuid-del-curso",
  "title": "Examen Parcial 1",
  "description": "Primer parcial - Unidades 1 a 3",
  "totalPoints": 100,
  "weight": 0.30,
  "scheduledAt": "2025-11-15T10:00:00",
  "visibilityStart": "2025-11-01T00:00:00",
  "assignedTeachers": ["uuid-docente-1"],
  "roomInfo": "Aula 205"
}
```

### 2. Publicar el examen
```bash
POST /api/v1/exams/{exam-id}/publish
```

### 3. Registrar estudiante que rindió
```bash
POST /api/v1/exam-submissions
{
  "examId": "uuid-del-examen",
  "studentId": "uuid-del-estudiante",
  "notes": "Presente"
}
```

### 4. Calificar examen
```bash
POST /api/v1/exam-submissions/{submission-id}/grade
{
  "score": 85,
  "feedback": "Muy buen trabajo. Revisar el punto 5."
}
```

### 5. Ver estadísticas
```bash
GET /api/v1/exams/{exam-id}/statistics
```

## Testing

Para probar el módulo:
1. Crear un examen para un curso existente
2. Publicar el examen
3. Registrar estudiantes que rindieron
4. Cargar calificaciones
5. Ver estadísticas
6. Verificar historial de cambios de notas

## Próximos Pasos (Fase 2)

1. Implementar banco de preguntas
2. Agregar modalidad ONLINE
3. Auto-corrección de preguntas objetivas
4. Temporizador para exámenes online
5. Aleatorización de preguntas
6. Integración con módulo de notificaciones
7. Exportación de resultados a PDF/Excel
8. Sistema de apelaciones

