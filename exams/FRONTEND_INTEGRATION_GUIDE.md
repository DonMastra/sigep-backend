# 🎨 Guía de Integración Frontend - Información de Docentes en Módulo Exams

## Resumen de Cambios

Se ha actualizado el módulo de exámenes para incluir **información de los docentes** en todas las respuestas de la API, permitiendo que el frontend pueda mostrar esta información en grillas, formularios y reportes.

## 📊 DTOs Actualizados

### 1. ExamDto (Examen Completo)

**Ya existente, sin cambios:**
```typescript
interface ExamDto {
  id: string;
  courseId: string;
  title: string;
  description?: string;
  modality: ExamModality;
  status: ExamStatus;
  totalPoints: number;
  weight: number;
  timeLimitMinutes?: number;
  scheduledAt?: Date;
  visibilityStart?: Date;
  visibilityEnd?: Date;
  assignedTeachers?: string[];  // Lista de UUIDs de docentes
  notes?: string;
  roomInfo?: string;
  version: number;
  createdAt: Date;
  createdBy: string;
  updatedAt?: Date;
  updatedBy?: string;
}
```

**Uso en el frontend:**
- Formulario de creación/edición de examen
- Vista detalle de examen
- Asignación de docentes al examen

---

### 2. ExamSummaryDto (Examen Resumido para Listados)

**🆕 ACTUALIZADO - Nuevo campo: `assignedTeachers`**

```typescript
interface ExamSummaryDto {
  id: string;
  courseId: string;
  title: string;
  status: ExamStatus;
  scheduledAt?: Date;
  totalPoints: number;
  weight: number;
  assignedTeachers?: string[];  // 🆕 NUEVO - Lista de UUIDs de docentes
  totalSubmissions: number;
  gradedSubmissions: number;
  pendingSubmissions: number;
}
```

**Uso en el frontend:**
```typescript
// Ejemplo en una tabla de exámenes
<mat-table [dataSource]="exams">
  <!-- Columnas existentes -->
  <ng-container matColumnDef="title">
    <mat-header-cell *matHeaderCellDef>Examen</mat-header-cell>
    <mat-cell *matCellDef="let exam">{{ exam.title }}</mat-cell>
  </ng-container>
  
  <!-- 🆕 Nueva columna de docentes -->
  <ng-container matColumnDef="teachers">
    <mat-header-cell *matHeaderCellDef>Docentes</mat-header-cell>
    <mat-cell *matCellDef="let exam">
      <app-teacher-chips [teacherIds]="exam.assignedTeachers"></app-teacher-chips>
    </mat-cell>
  </ng-container>
  
  <ng-container matColumnDef="status">
    <mat-header-cell *matHeaderCellDef>Estado</mat-header-cell>
    <mat-cell *matCellDef="let exam">
      <mat-chip [color]="getStatusColor(exam.status)">
        {{ exam.status }}
      </mat-chip>
    </mat-cell>
  </ng-container>
</mat-table>
```

---

### 3. ExamStatisticsDto (Estadísticas de Examen)

**🆕 ACTUALIZADO - Nuevo campo: `assignedTeachers`**

```typescript
interface ExamStatisticsDto {
  examId: string;
  examTitle: string;
  assignedTeachers?: string[];  // 🆕 NUEVO - Docentes del examen
  totalStudents: number;
  submittedCount: number;
  gradedCount: number;
  pendingCount: number;
  averageScore?: number;
  highestScore?: number;
  lowestScore?: number;
  passRate?: number;
  scoreDistribution: Record<string, number>;
}
```

**Uso en el frontend:**
```typescript
// Componente de estadísticas
export class ExamStatisticsComponent {
  statistics$: Observable<ExamStatisticsDto>;
  teachers$: Observable<TeacherDto[]>;
  
  ngOnInit() {
    this.statistics$ = this.examService.getExamStatistics(this.examId);
    
    // Cargar información de docentes cuando se obtienen las estadísticas
    this.teachers$ = this.statistics$.pipe(
      switchMap(stats => {
        if (stats.assignedTeachers && stats.assignedTeachers.length > 0) {
          return this.staffService.getTeachersByIds(stats.assignedTeachers);
        }
        return of([]);
      })
    );
  }
}
```

```html
<!-- Template de estadísticas -->
<mat-card>
  <mat-card-header>
    <mat-card-title>{{ (statistics$ | async)?.examTitle }}</mat-card-title>
    <mat-card-subtitle>
      Docentes: 
      <span *ngFor="let teacher of (teachers$ | async)">
        {{ teacher.fullName }}
      </span>
    </mat-card-subtitle>
  </mat-card-header>
  
  <mat-card-content>
    <!-- KPIs y gráficos -->
  </mat-card-content>
</mat-card>
```

---

### 4. ExamResultSummary (Historial de Exámenes del Estudiante)

**🆕 ACTUALIZADO - Nuevos campos: `assignedTeachers` y `gradedBy`**

```typescript
interface ExamResultSummary {
  examId: string;
  examTitle: string;
  scheduledAt?: Date;
  totalPoints: number;
  assignedTeachers?: string[];  // 🆕 NUEVO - Docentes del examen
  score?: number;
  status: SubmissionStatus;
  gradedBy?: string;           // 🆕 ACTUALIZADO - Docente que calificó
  gradedAt?: Date;
  feedback?: string;
}
```

**Uso en el frontend:**
```typescript
// Componente de historial del estudiante
export class StudentHistoryComponent {
  history$: Observable<ExamResultSummary[]>;
  
  ngOnInit() {
    this.history$ = this.examService.getStudentHistory(
      this.studentId,
      this.courseId
    );
  }
  
  getTeacherName(teacherId: string): Observable<string> {
    return this.staffService.getTeacher(teacherId).pipe(
      map(teacher => teacher.fullName)
    );
  }
}
```

```html
<!-- Template de historial -->
<mat-table [dataSource]="history$ | async">
  <ng-container matColumnDef="examTitle">
    <mat-header-cell *matHeaderCellDef>Examen</mat-header-cell>
    <mat-cell *matCellDef="let result">{{ result.examTitle }}</mat-cell>
  </ng-container>
  
  <ng-container matColumnDef="score">
    <mat-header-cell *matHeaderCellDef>Nota</mat-header-cell>
    <mat-cell *matCellDef="let result">
      {{ result.score || 'Pendiente' }} / {{ result.totalPoints }}
    </mat-cell>
  </ng-container>
  
  <!-- 🆕 Nueva columna: Docente que calificó -->
  <ng-container matColumnDef="gradedBy">
    <mat-header-cell *matHeaderCellDef>Calificado por</mat-header-cell>
    <mat-cell *matCellDef="let result">
      <span *ngIf="result.gradedBy">
        {{ getTeacherName(result.gradedBy) | async }}
      </span>
      <span *ngIf="!result.gradedBy" class="text-muted">
        N/A
      </span>
    </mat-cell>
  </ng-container>
  
  <!-- 🆕 Nueva columna: Docentes del examen -->
  <ng-container matColumnDef="assignedTeachers">
    <mat-header-cell *matHeaderCellDef>Docentes</mat-header-cell>
    <mat-cell *matCellDef="let result">
      <app-teacher-chips 
        [teacherIds]="result.assignedTeachers"
        [size]="'small'">
      </app-teacher-chips>
    </mat-cell>
  </ng-container>
</mat-table>
```

---

### 5. SubmissionWithStudentDto (Submission con Info de Estudiante)

**🆕 ACTUALIZADO - Nuevo campo: `examAssignedTeachers`**

```typescript
interface SubmissionWithStudentDto {
  id: string;
  examId: string;
  examTitle: string;
  examAssignedTeachers?: string[];  // 🆕 NUEVO - Docentes del examen
  studentId: string;
  studentName: string;
  studentEmail: string;
  attemptNumber: number;
  status: SubmissionStatus;
  score?: number;
  gradedBy?: string;
  gradedAt?: Date;
  feedback?: string;
  scannedFilePath?: string;
}
```

**Uso en el frontend:**
```typescript
// Componente de calificaciones del examen
export class ExamGradingComponent {
  submissions$: Observable<SubmissionWithStudentDto[]>;
  
  ngOnInit() {
    this.submissions$ = this.examService.getExamSubmissions(this.examId);
  }
}
```

```html
<!-- Grilla de estudiantes para calificar -->
<mat-table [dataSource]="submissions$ | async">
  <ng-container matColumnDef="studentName">
    <mat-header-cell *matHeaderCellDef>Estudiante</mat-header-cell>
    <mat-cell *matCellDef="let submission">
      {{ submission.studentName }}
    </mat-cell>
  </ng-container>
  
  <ng-container matColumnDef="score">
    <mat-header-cell *matHeaderCellDef>Calificación</mat-header-cell>
    <mat-cell *matCellDef="let submission">
      <input 
        type="number" 
        [(ngModel)]="submission.score"
        [disabled]="!canGrade(submission)">
    </mat-cell>
  </ng-container>
  
  <ng-container matColumnDef="status">
    <mat-header-cell *matHeaderCellDef>Estado</mat-header-cell>
    <mat-cell *matCellDef="let submission">
      <mat-chip [color]="getStatusColor(submission.status)">
        {{ submission.status }}
      </mat-chip>
    </mat-cell>
  </ng-container>
</mat-table>
```

---

## 🎨 Componente Reutilizable Sugerido

### TeacherChipsComponent

Crea un componente reutilizable para mostrar los docentes de forma consistente:

```typescript
// teacher-chips.component.ts
@Component({
  selector: 'app-teacher-chips',
  template: `
    <mat-chip-listbox>
      <mat-chip 
        *ngFor="let teacher of teachers$ | async"
        [highlighted]="true">
        <mat-icon matChipAvatar>person</mat-icon>
        {{ teacher.fullName }}
      </mat-chip>
    </mat-chip-listbox>
  `,
  styles: [`
    mat-chip {
      font-size: 0.875rem;
    }
  `]
})
export class TeacherChipsComponent implements OnInit {
  @Input() teacherIds?: string[];
  @Input() size: 'small' | 'medium' | 'large' = 'medium';
  
  teachers$: Observable<TeacherDto[]>;
  
  constructor(private staffService: StaffService) {}
  
  ngOnInit() {
    if (this.teacherIds && this.teacherIds.length > 0) {
      this.teachers$ = this.staffService.getTeachersByIds(this.teacherIds);
    } else {
      this.teachers$ = of([]);
    }
  }
}
```

---

## 📡 Servicios Angular Actualizados

```typescript
// exam.service.ts
@Injectable()
export class ExamService {
  
  // Método existente - sin cambios necesarios
  getExam(examId: string): Observable<ApiResponse<ExamDto>> {
    return this.http.get<ApiResponse<ExamDto>>(
      `${this.apiUrl}/exams/${examId}`
    );
  }
  
  // Método existente - ahora retorna assignedTeachers en cada item
  getExamsByCourse(
    courseId: string,
    params?: ExamQueryParams
  ): Observable<ApiResponse<PageResponse<ExamSummaryDto>>> {
    return this.http.get<ApiResponse<PageResponse<ExamSummaryDto>>>(
      `${this.apiUrl}/exams/course/${courseId}`,
      { params: this.buildQueryParams(params) }
    );
  }
  
  // Método existente - ahora incluye assignedTeachers
  getExamStatistics(examId: string): Observable<ApiResponse<ExamStatisticsDto>> {
    return this.http.get<ApiResponse<ExamStatisticsDto>>(
      `${this.apiUrl}/exams/${examId}/statistics`
    );
  }
  
  // Método existente - ahora incluye assignedTeachers y gradedBy
  getStudentHistory(
    studentId: string, 
    courseId: string
  ): Observable<ApiResponse<ExamResultSummary[]>> {
    return this.http.get<ApiResponse<ExamResultSummary[]>>(
      `${this.apiUrl}/exam-submissions/student/${studentId}/course/${courseId}/history`
    );
  }
  
  // 🆕 NUEVO - Obtener rendimiento de docente
  getTeacherPerformance(teacherId: string): Observable<ApiResponse<TeacherPerformanceDto>> {
    return this.http.get<ApiResponse<TeacherPerformanceDto>>(
      `${this.apiUrl}/teachers/${teacherId}/performance`
    );
  }
}
```

```typescript
// staff.service.ts
@Injectable()
export class StaffService {
  
  // Método para obtener múltiples docentes por IDs
  getTeachersByIds(teacherIds: string[]): Observable<TeacherDto[]> {
    return this.http.post<ApiResponse<TeacherDto[]>>(
      `${this.apiUrl}/staff/teachers/bulk`,
      { ids: teacherIds }
    ).pipe(
      map(response => response.data)
    );
  }
  
  // Método para obtener un docente individual
  getTeacher(teacherId: string): Observable<TeacherDto> {
    return this.http.get<ApiResponse<TeacherDto>>(
      `${this.apiUrl}/staff/teachers/${teacherId}`
    ).pipe(
      map(response => response.data)
    );
  }
}
```

---

## 🎯 Casos de Uso en el Frontend

### 1. Grilla de Exámenes con Docentes

```typescript
export class ExamListComponent implements OnInit {
  displayedColumns = [
    'title', 
    'scheduledAt', 
    'status', 
    'teachers',  // 🆕 Nueva columna
    'submissions', 
    'actions'
  ];
  
  dataSource: MatTableDataSource<ExamSummaryDto>;
  
  ngOnInit() {
    this.loadExams();
  }
  
  loadExams() {
    this.examService.getExamsByCourse(this.courseId).subscribe(
      response => {
        this.dataSource = new MatTableDataSource(response.data.content);
      }
    );
  }
}
```

### 2. Dashboard de Docente

```typescript
export class TeacherDashboardComponent implements OnInit {
  performance$: Observable<TeacherPerformanceDto>;
  teacherId: string;
  
  ngOnInit() {
    this.teacherId = this.authService.getCurrentUserId();
    this.performance$ = this.examService.getTeacherPerformance(this.teacherId);
  }
}
```

### 3. Historial de Estudiante con Docentes

```typescript
export class StudentProfileComponent implements OnInit {
  examHistory$: Observable<ExamResultSummary[]>;
  
  ngOnInit() {
    this.examHistory$ = this.examService.getStudentHistory(
      this.studentId,
      this.courseId
    );
  }
  
  // Helper para mostrar nombres de docentes
  getTeacherNames(teacherIds?: string[]): Observable<string> {
    if (!teacherIds || teacherIds.length === 0) {
      return of('Sin asignar');
    }
    
    return this.staffService.getTeachersByIds(teacherIds).pipe(
      map(teachers => teachers.map(t => t.fullName).join(', '))
    );
  }
}
```

---

## ✅ Checklist de Migración Frontend

- [ ] Actualizar interfaces TypeScript con los nuevos campos
- [ ] Crear componente `TeacherChipsComponent` reutilizable
- [ ] Actualizar grillas de exámenes para mostrar docentes
- [ ] Actualizar vista de estadísticas con información de docentes
- [ ] Actualizar historial de estudiantes con docentes y calificadores
- [ ] Implementar servicio `StaffService.getTeachersByIds()`
- [ ] Agregar columna "Docentes" en tablas relevantes
- [ ] Actualizar formularios de creación/edición de exámenes
- [ ] Implementar dashboard de rendimiento de docentes
- [ ] Testing de componentes actualizados

---

## 📝 Notas Importantes

1. **Compatibilidad hacia atrás**: Los campos nuevos son opcionales (`?`), por lo que no rompen la compatibilidad con versiones anteriores.

2. **Performance**: Considera implementar caché para la información de docentes si hay muchas consultas.

3. **Lazy Loading**: Carga la información de docentes solo cuando se necesita mostrar.

4. **Fallbacks**: Siempre proporciona valores por defecto cuando `assignedTeachers` sea `null` o `undefined`.

---

**Fecha de Actualización**: 30 de Octubre, 2025  
**Versión del Backend**: 1.0.0

