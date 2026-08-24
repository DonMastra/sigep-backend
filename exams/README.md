# Módulo de Exámenes (Exams)

> 📚 **[Ver Índice de Documentación](DOCUMENTATION_INDEX.md)** - Guía completa de todos los documentos disponibles

## Descripción

Módulo para la gestión de exámenes presenciales y carga de calificaciones del instituto de inglés.

### Fase 1 - Funcionalidad Actual (Offline/Presencial)
- Gestión completa de exámenes presenciales
- Registro de estudiantes que rindieron exámenes
- Carga y gestión de calificaciones
- Historial de cambios de notas (auditoría)
- Adjuntar archivos escaneados de exámenes
- Estadísticas y reportes por examen y curso
- **🆕 Análisis de rendimiento de docentes** - Métricas de performance y gestión
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
  - `TeacherPerformanceService`: **🆕 Análisis de rendimiento de docentes**
- **DTOs**: Objetos de transferencia de datos

### Infrastructure Layer
- **Config**: Configuración del módulo
- **Migrations**: Scripts Flyway para base de datos

### Presentation Layer
- **Controllers**: Endpoints REST
  - `ExamController`: CRUD de exámenes
  - `ExamSubmissionController`: Gestión de calificaciones
  - `TeacherPerformanceController`: **🆕 Análisis de rendimiento de docentes**

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

Un recuperatorio se crea enviando `sourceExamId` de un examen regular `PUBLISHED` o `CLOSED`
del mismo curso. Su grilla incorpora solo estudiantes calificados con al menos una categoría
menor a 60; las categorías aprobadas se conservan y no pueden modificarse.

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

### 🆕 Análisis de Rendimiento de Docentes

#### GET `/api/v1/teachers/{teacherId}/performance`
Obtener estadísticas completas de rendimiento de un docente
- Requiere: `ADMIN`
- Retorna:
  - Total de exámenes creados, publicados y cerrados
  - Total de estudiantes evaluados
  - Promedio general de calificaciones
  - Tasa de aprobación general
  - Distribución de exámenes por estado
  - Estadísticas por curso
  - Lista de exámenes recientes

#### GET `/api/v1/teachers/{teacherId}/exams`
Obtener lista de exámenes de un docente
- Requiere: `ADMIN` o `TEACHER` (propio)
- Query params: `statuses`, `page`, `size`, `sort`, `order`

#### POST `/api/v1/teachers/compare`
Comparar rendimiento de múltiples docentes
- Requiere: `ADMIN`
- Body: Array de IDs de docentes
- Retorna: Mapa con estadísticas de cada docente

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
6. Las nuevas grillas incluyen Reading, Writing, Listening y Speaking
7. Las notas históricas de tres categorías se conservan sin recálculo
8. Los resultados históricos sin categorías no se asignan automáticamente a recuperatorios

## Integración con Otros Módulos

- **Courses**: Los exámenes pertenecen a cursos
- **Students**: Las calificaciones se asocian a estudiantes
- **Staff**: **🆕 Relación con docentes (TeachingStaff)** - Los exámenes tienen docentes asignados para análisis de rendimiento y gestión
- **Security**: Control de acceso por roles
- **Communications**: Notificaciones de exámenes publicados/calificados (futuro)
- **Scheduling**: Integración con calendario (futuro)
- **Reports**: Exportación de estadísticas (futuro)

## Análisis de Rendimiento de Docentes

### Métricas Disponibles

El módulo ahora permite realizar un **análisis integral del rendimiento de los docentes** basándose en:

1. **Gestión de Exámenes**
   - Cantidad de exámenes creados
   - Exámenes publicados vs. en borrador
   - Exámenes cerrados (completados)

2. **Resultados Académicos**
   - Promedio de calificaciones de los estudiantes
   - Tasa de aprobación en sus cursos
   - Distribución de calificaciones

3. **Indicadores por Curso**
   - Desempeño en cada curso asignado
   - Cantidad de estudiantes evaluados
   - Promedios y tasas de aprobación por curso

4. **Tendencias Temporales**
   - Exámenes recientes
   - Evolución del rendimiento

### Casos de Uso

- **Gestión de RRHH**: Evaluar objetivamente el desempeño docente
- **Identificación de necesidades**: Detectar docentes que requieren capacitación
- **Reconocimientos**: Identificar docentes con excelentes resultados
- **Toma de decisiones**: Asignación de cursos basada en performance
- **Auditoría académica**: Análisis de calidad educativa

### Frontend Integration

Para el frontend Angular, estos endpoints permiten:
- Dashboard de métricas por docente
- Comparación visual entre docentes
- Gráficos de tendencias y distribuciones
- Reportes exportables de rendimiento


## Caché

Se utiliza Spring Cache para:
- `exams`: Caché de exámenes individuales
- `submissions`: Caché de submissions individuales

Las operaciones de escritura invalidan el caché correspondiente.

## Base de Datos

### Migraciones
- `V5__create_exams_module.sql`: Creación de tablas y estructura inicial
- `V34__add_speaking_and_recovery_exam_support.sql`: Speaking y vínculo auditable de recuperatorios

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

