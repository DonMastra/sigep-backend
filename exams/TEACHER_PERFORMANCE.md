# 📊 Análisis de Rendimiento de Docentes - Módulo Exams

## Descripción General

Nueva funcionalidad agregada al módulo de **Exams** que permite realizar un análisis integral del rendimiento de los docentes basándose en la gestión de exámenes y los resultados obtenidos por sus estudiantes.

## 🎯 Objetivo

Proporcionar métricas objetivas y cuantificables sobre el desempeño de los docentes para:
- Gestión de recursos humanos
- Identificación de necesidades de capacitación
- Reconocimiento de excelencia académica
- Toma de decisiones sobre asignación de cursos
- Auditoría y control de calidad educativa

## 🆕 Componentes Implementados

### 1. DTOs Nuevos

#### `TeacherPerformanceDto`
Contiene las métricas completas de rendimiento de un docente:
```kotlin
data class TeacherPerformanceDto(
    val teacherId: Long,
    val fullName: String?,
    val totalExamCount: Int,
    val publishedExamCount: Int,
    val totalStudentsEvaluated: Int,
    val averageScore: BigDecimal?,
    val passRate: BigDecimal?,
    val courseExams: List<CourseExamSummaryDto>,
    val recentExams: List<ExamSummaryDto>
)
```

#### `CourseExamSummaryDto`
Resume las estadísticas de exámenes por curso:
```kotlin
data class CourseExamSummaryDto(
    val courseId: Long,
    val totalExams: Int,
    val averageScore: BigDecimal?,
    val passRate: BigDecimal?,
    val totalStudents: Int
)
```

### 2. Servicio de Análisis

**`TeacherPerformanceService`**

Servicios especializados:
- `getTeacherPerformance(teacherId)`: Obtiene métricas completas de un docente
- `getTeacherExams(teacherId, filters)`: Lista exámenes con filtros y paginación
- `compareTeachersPerformance(teacherIds)`: Compara múltiples docentes

### 3. Repositorio Extendido

**`ExamRepository`**

Nuevas consultas agregadas:
```kotlin
// Buscar exámenes por docente
fun findByTeacherId(teacherId: Long, pageable: Pageable): Page<Exam>

// Buscar por docente y estados
fun findByTeacherIdAndStatusIn(teacherId: Long, statuses: List<ExamStatus>, pageable: Pageable): Page<Exam>

// Contar exámenes del docente
fun countByTeacherId(teacherId: Long): Long

// Buscar en rango de fechas
fun findByTeacherIdAndScheduledBetween(teacherId: Long, start: LocalDateTime, end: LocalDateTime): List<Exam>
```

### 4. Controller REST

**`TeacherPerformanceController`**

Endpoints disponibles:

#### `GET /api/v1/teachers/{teacherId}/performance`
- **Descripción**: Obtener estadísticas completas de rendimiento
- **Autorización**: Solo ADMIN
- **Respuesta**: `TeacherPerformanceDto`

#### `GET /api/v1/teachers/{teacherId}/exams`
- **Descripción**: Listar exámenes del docente
- **Autorización**: ADMIN o TEACHER (propio)
- **Parámetros**: `statuses`, `page`, `size`, `sort`, `order`
- **Respuesta**: Lista de `ExamDto`

#### `POST /api/v1/teachers/compare`
- **Descripción**: Comparar rendimiento entre docentes
- **Autorización**: Solo ADMIN
- **Body**: `{ teacherIds: number[] }`
- **Respuesta**: `Map<Long, TeacherPerformanceDto>`

## 📈 Métricas Calculadas

### Métricas de Gestión
- **Total de Exámenes Creados**: Cantidad de exámenes que el docente ha creado
- **Exámenes Publicados**: Exámenes activos/disponibles
- **Exámenes Cerrados**: Exámenes finalizados
- **Distribución por Estado**: Cantidad en DRAFT, PUBLISHED, CLOSED, CANCELLED

### Métricas de Resultados
- **Total de Estudiantes Evaluados**: Cantidad de estudiantes que rindieron exámenes
- **Promedio General**: Promedio de calificaciones obtenidas por todos los estudiantes
- **Tasa de Aprobación**: Porcentaje de estudiantes que aprobaron (≥60%)

### Métricas por Curso
Para cada curso asignado:
- Total de exámenes
- Promedio de calificaciones
- Tasa de aprobación
- Total de estudiantes evaluados

### Actividad Reciente
- Lista de los últimos 10 exámenes creados/programados

## 🔍 Casos de Uso

### 1. Dashboard de Gestión (ADMIN)
El administrador puede ver un dashboard con:
- Ranking de docentes por tasa de aprobación
- Promedio de calificaciones por docente
- Productividad (exámenes creados/publicados)

### 2. Autoevaluación (TEACHER)
El docente puede consultar sus propias estadísticas:
- Ver su rendimiento histórico
- Comparar resultados entre diferentes cursos
- Identificar áreas de mejora

### 3. Comparación de Rendimiento (ADMIN)
Permite comparar objetivamente entre docentes:
- Identificar mejores prácticas
- Detectar inconsistencias
- Tomar decisiones de asignación

### 4. Reportes de Auditoría (ADMIN)
Generar reportes para:
- Control de calidad educativa
- Cumplimiento de estándares
- Evaluación de desempeño

## 🔗 Integración con Frontend Angular

### Componentes Sugeridos

```typescript
// teacher-performance.component.ts
export class TeacherPerformanceComponent {
  performance$: Observable<TeacherPerformanceDto>;
  
  loadTeacherPerformance(teacherId: string) {
    this.performance$ = this.examService.getTeacherPerformance(teacherId);
  }
}

// teacher-comparison.component.ts
export class TeacherComparisonComponent {
  comparison$: Observable<Map<number, TeacherPerformanceDto>>;
  
  compareTeachers(teacherIds: number[]) {
    this.comparison$ = this.examService.compareTeachers(teacherIds);
  }
}
```

### Visualizaciones Recomendadas

1. **Gráfico de Barras**: Comparación de tasas de aprobación
2. **Gráfico de Líneas**: Evolución temporal de promedios
3. **Gráfico de Torta**: Distribución de exámenes por estado
4. **Tabla de Rankings**: Top docentes por diferentes métricas
5. **Tarjetas de KPI**: Métricas principales destacadas

### Servicios Angular

```typescript
// exam.service.ts
@Injectable()
export class ExamService {
  
  getTeacherPerformance(teacherId: number): Observable<ApiResponse<TeacherPerformanceDto>> {
    return this.http.get<ApiResponse<TeacherPerformanceDto>>(
      `${this.apiUrl}/teachers/${teacherId}/performance`
    );
  }
  
  getTeacherExams(teacherId: number, params?: ExamQueryParams): Observable<ApiResponse<ExamDto[]>> {
    return this.http.get<ApiResponse<ExamDto[]>>(
      `${this.apiUrl}/teachers/${teacherId}/exams`,
      { params }
    );
  }
  
  compareTeachers(teacherIds: number[]): Observable<ApiResponse<Map<number, TeacherPerformanceDto>>> {
    return this.http.post<ApiResponse<Map<number, TeacherPerformanceDto>>>(
      `${this.apiUrl}/teachers/compare`,
      { teacherIds }
    );
  }
}
```

## 🔐 Seguridad

### Control de Acceso

- **ADMIN**: Acceso completo a todas las estadísticas de todos los docentes
- **TEACHER**: Solo puede ver sus propias estadísticas (endpoint `/teachers/{teacherId}/exams`)
- **STUDENT**: Sin acceso a estas métricas

### Validaciones

- Se valida que el teacherId corresponda a un docente existente en el módulo Staff
- Se verifica que el usuario autenticado tenga permisos para acceder a los datos
- Los datos se filtran según los cursos asignados al docente

## 📊 Ejemplo de Respuesta

```json
{
  "status": "success",
  "message": "Estadísticas de rendimiento obtenidas exitosamente",
  "data": {
    "teacherId": 10,
    "fullName": "Ana Gomez",
    "totalExamCount": 45,
    "publishedExamCount": 38,
    "totalStudentsEvaluated": 450,
    "averageScore": 78.5,
    "passRate": 85.3,
    "courseExams": [
      {
        "courseId": 101,
        "totalExams": 15,
        "averageScore": 80.2,
        "passRate": 88.5,
        "totalStudents": 150
      }
    ],
    "recentExams": [...]
  }
}
```

## 🚀 Próximos Pasos

### Mejoras Futuras

1. **Análisis Predictivo**: ML para predecir rendimiento futuro
2. **Alertas Automáticas**: Notificaciones cuando las métricas bajan
3. **Benchmarking**: Comparación con promedios institucionales
4. **Reportes Exportables**: PDF/Excel de estadísticas
5. **Filtros Temporales**: Análisis por períodos específicos
6. **Feedback de Estudiantes**: Integrar encuestas de satisfacción

### Optimizaciones

1. **Caché de Estadísticas**: Cachear métricas calculadas
2. **Cálculo Asíncrono**: Procesamiento en background
3. **Vistas Materializadas**: Para consultas frecuentes
4. **Indexación**: Índices adicionales para consultas complejas

## 📝 Notas Técnicas

### Consideraciones de Rendimiento

- Las estadísticas se calculan en tiempo real, lo que puede ser costoso
- Recomendable implementar caché con TTL de 1 hora
- Para grandes volúmenes, considerar procesamiento batch nocturno

### Limitaciones Actuales

- No incluye análisis temporal/histórico
- No tiene en cuenta factores externos (complejidad del curso, nivel de estudiantes)
- Asume 60% como nota de aprobación (puede configurarse)

### Testing

Se recomienda crear tests para:
- Cálculo correcto de promedios
- Manejo de casos sin datos
- Permisos de acceso
- Paginación y filtros

---

**Creado**: Octubre 2025  
**Módulo**: Exams  
**Versión**: 1.0.0

