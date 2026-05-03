# 🚀 Frontend Migration Note - v1.2.0
**Últimas actualizaciones del Backend SiGEP - Información de Docentes**

---

## 📌 Resumen Ejecutivo

El módulo **Exams** ha sido enhancido para incluir **información de docentes** en todas las respuestas. Esto permite al frontend mostrar de forma completa y contextualizada quién dicta e imparte cada examen.

**Impacto Frontend**: ⚠️ **IMPORTANTE** - DTOs actualizados, nuevos endpoints, nuevos campos opcionales

---

## 🔄 DTOs Actualizados (Con nuevos campos)

### 1. **ExamSummaryDto** - Listados de exámenes
```typescript
// NUEVO campo
assignedTeachers?: number[]  // IDs numéricos de docentes (teaching_staff.id)
teacherNames?: string[]      // Nombres resueltos en el mismo orden
```
**Dónde se usa**: Tablas de exámenes por curso

### 2. **ExamStatisticsDto** - Estadísticas
```typescript
// NUEVO campo
assignedTeachers?: number[]  // IDs numéricos de docentes
teacherNames?: string[]      // Nombres resueltos
```
**Dónde se usa**: Reportes y dashboards de estadísticas

### 3. **ExamResultSummary** - Historial del estudiante
```typescript
// NUEVOS campos
assignedTeachers?: number[]  // IDs numéricos de docentes
teacherNames?: string[]      // Nombres de docentes asignados
gradedBy?: number            // ID numérico de docente que calificó
gradedByName?: string        // Nombre del docente que calificó
gradedAt?: Date              // Fecha de calificación
```
**Dónde se usa**: Historial de exámenes del estudiante

### 4. **SubmissionWithStudentDto** - Submissions
```typescript
// NUEVO campo
examAssignedTeachers?: number[]  // IDs numéricos de docentes
examTeacherNames?: string[]      // Nombres de docentes del examen
gradedByName?: string            // Nombre de quien calificó
```
**Dónde se usa**: Grilla de calificaciones

---

## 🆕 Nuevos Endpoints de Docentes

### 1. GET `/api/v1/teachers/{teacherId}/performance`
**Descripción**: Obtener estadísticas completas de un docente  
**Roles**: ADMIN  
**Respuesta**: `TeacherPerformanceDto` con métricas globales
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
```

### 2. GET `/api/v1/teachers/{teacherId}/exams`
**Descripción**: Listar exámenes de un docente (con filtros)  
**Roles**: ADMIN, TEACHER (propio)  
**Query params**: `statuses`, `page`, `size`, `sort`, `order`  
**Respuesta**: Lista paginada de exámenes

### 3. POST `/api/v1/teachers/compare`
**Descripción**: Comparar rendimiento entre múltiples docentes  
**Roles**: ADMIN  
**Body**: `{ teacherIds: number[] }`  
**Respuesta**: Map de `TeacherPerformanceDto` por docente

### 4. POST `/api/v1/staff/teaching/resolve`
**Descripción**: Resolver IDs de docentes a nombre completo (batch)  
**Roles**: ADMIN, TEACHER  
**Body**: `{ ids: number[] }`  
**Respuesta**: `TeacherResolutionDto[]`

---

## ✨ Casos de Uso Principales

### 📊 Grilla de Exámenes (Mostrar Docentes)
```typescript
displayedColumns = [
  'title', 
  'status', 
  'teachers',     // ← NUEVA COLUMNA
  'submissions', 
  'actions'
];
```

### 👨‍🏫 Dashboard de Docente
```typescript
// Obtener performance del docente actual
this.examService.getTeacherPerformance(teacherId).subscribe(
  performance => {
    // Mostrar KPIs: total exámenes, promedio, tasa de aprobación
  }
);
```

### 📋 Historial de Estudiante
```html
<!-- Agregar dos nuevas columnas -->
<ng-container matColumnDef="teachers">
  <mat-header-cell>Docentes</mat-header-cell>
  <mat-cell>{{ result.assignedTeachers | teachers }}</mat-cell>
</ng-container>

<ng-container matColumnDef="gradedBy">
  <mat-header-cell>Calificado por</mat-header-cell>
  <mat-cell>{{ result.gradedBy | teacherName }}</mat-cell>
</ng-container>
```

---

## 🔧 Tareas para el Frontend

### ✅ Prioridad ALTA (Esta Sprint)
- [ ] Actualizar interfaces TypeScript con nuevos campos opcionales
- [ ] Agregar columna "Docentes" en tablas de exámenes
- [ ] Implementar pipe/componente para mostrar nombres de docentes desde IDs
- [ ] Consumir `teacherNames` / `gradedByName` cuando estén presentes
- [ ] Actualizar servicio para consumir nuevos campos

### ✅ Prioridad MEDIA (Próxima Sprint)
- [ ] Crear componente reutilizable `TeacherChipsComponent`
- [ ] Implementar `StaffService.resolveTeachers(ids: number[])` para fallback/batch
- [ ] Agregar sección de docentes en formulario de exámenes
- [ ] Dashboard de rendimiento de docente

### ✅ Prioridad BAJA (Backlog)
- [ ] Comparación de docentes
- [ ] Exportación de reportes con información de docentes
- [ ] Filtro por docente en listados

---

## ⚠️ Consideraciones Técnicas

### Compatibilidad Hacia Atrás
✅ **Todos los nuevos campos son opcionales** (`?`)  
✅ **No rompe existentes implementaciones**  
✅ **Retrocompatible con versión anterior**

### Performance
- Considera **caché** para nombres de docentes (cambian raramente)
- Usa **lazy loading** para estadísticas de docentes
- Implementa **batch requests** para resolver múltiples IDs

### Datos Faltantes
```typescript
// Siempre proporciona fallback cuando assignedTeachers sea null/undefined
assignedTeachers?.length > 0 ? teachers : 'Sin asignar'
```

---

## 📚 Recursos

| Documento | Ubicación | Propósito |
|-----------|-----------|----------|
| **API Contract** | `API_CONTRACT.md` | Especificación completa de endpoints |
| **Integration Guide** | `exams/FRONTEND_INTEGRATION_GUIDE.md` | Ejemplos detallados de integración |
| **Teacher Performance** | `exams/TEACHER_PERFORMANCE.md` | Documentación técnica de métricas |

---

## 🤝 Integración con Backend

**URL de Desarrollo**: `http://localhost:8080/api/v1`  
**Prefix**: `/api/v1`  
**Header Auth**: `Authorization: Bearer {token}`  
**Formato DTOs**: Todas respuestas envueltas en `ApiResponse<T>`

**Test Users (dev mode)**:
```
username: teacher | password: password123 | role: TEACHER
username: admin   | password: password123 | role: ADMIN
```

---

## ✅ QA Checklist

- [ ] Nuevos campos opcionales no causan errores
- [ ] Fallbacks funcionan cuando datos faltantes
- [ ] Componente de docentes se renderiza correctamente
- [ ] API resuelve IDs a nombres en tiempo real
- [ ] Permisos por rol funcionan correctamente
- [ ] Paginación de exámenes funciona con nuevos campos

---

**Versión**: 1.2.0  
**Fecha**: 3 de Mayo, 2026  
**Responsable Backend**: Backend Team  
**Próxima Revisión**: 17 de Mayo, 2026

> 💡 **Contacta al backend team si tienes dudas sobre los nuevos DTOs o endpoints**




