# Módulo de Gestión de Personal (Staff)

## Descripción

El módulo **Staff** proporciona funcionalidades completas para la gestión del personal docente y no docente de la institución educativa.

## Características Principales

### 👨‍🏫 Personal Docente (Teaching Staff)

- **Datos Personales**: Nombre, email, teléfono, documento, dirección
- **Información Laboral**: 
  - Fecha de contratación
  - Salario mensual
  - Estado de pago (al día, pendiente, atrasado)
  - Especialización
- **Gestión Académica**:
  - Estudiantes asignados (contador)
  - Cursos asignados (integración con módulo de courses)
- **Seguimiento**:
  - Observaciones y notas administrativas
  - Presentismo/ausentismo
  - Estadísticas de asistencia
- **Contacto de Emergencia**: Nombre y teléfono

### 🧹 Personal No Docente (Non-Teaching Staff)

- **Datos Personales**: Nombre, email, teléfono, documento, dirección
- **Información Laboral**:
  - Fecha de contratación
  - Tarifa por hora
  - Rol (limpieza, mantenimiento, sistemas, seguridad, administración)
  - Empresa a la que pertenece
- **Gestión de Tareas**:
  - Tareas asignadas
  - Horas trabajadas por mes
  - Ganancia estimada mensual
- **Seguimiento**:
  - Observaciones
  - Días de asistencia
  - Estadísticas de asistencia
- **Contacto de Emergencia**: Nombre y teléfono

### 📊 Control de Asistencia

- Registro de entrada/salida (check-in/check-out)
- Estados de asistencia:
  - Presente
  - Ausente
  - Tarde
  - Justificado
  - Licencia por enfermedad
  - Vacaciones
- Horas trabajadas (especialmente para personal no docente)
- Notas sobre cada registro de asistencia
- Reportes de asistencia por período

## Arquitectura

### Estructura de Módulos

```
staff/
├── domain/
│   └── model/
│       ├── TeachingStaff.kt          # Entidad personal docente
│       ├── NonTeachingStaff.kt       # Entidad personal no docente
│       └── StaffAttendance.kt        # Registro de asistencia
├── application/
│   ├── dto/
│   │   ├── TeachingStaffDto.kt
│   │   ├── NonTeachingStaffDto.kt
│   │   └── StaffAttendanceDto.kt
│   └── service/
│       ├── TeachingStaffService.kt
│       ├── NonTeachingStaffService.kt
│       └── StaffAttendanceService.kt
├── infrastructure/
│   ├── repository/
│   │   ├── TeachingStaffRepository.kt
│   │   ├── NonTeachingStaffRepository.kt
│   │   └── StaffAttendanceRepository.kt
│   └── config/
│       └── StaffModuleConfig.kt
└── presentation/
    └── controller/
        ├── TeachingStaffController.kt
        ├── NonTeachingStaffController.kt
        └── StaffAttendanceController.kt
```

## API Endpoints

### Personal Docente

| Método | Endpoint | Descripción | Rol Requerido |
|--------|----------|-------------|---------------|
| GET | `/api/v1/staff/teaching` | Listar todo el personal docente | ADMIN |
| GET | `/api/v1/staff/teaching/{id}` | Obtener docente por ID | ADMIN, TEACHER |
| GET | `/api/v1/staff/teaching/search?query=` | Buscar docentes | ADMIN |
| POST | `/api/v1/staff/teaching` | Crear docente | ADMIN |
| PUT | `/api/v1/staff/teaching/{id}` | Actualizar docente | ADMIN |
| DELETE | `/api/v1/staff/teaching/{id}` | Eliminar docente | ADMIN |

### Personal No Docente

| Método | Endpoint | Descripción | Rol Requerido |
|--------|----------|-------------|---------------|
| GET | `/api/v1/staff/non-teaching` | Listar todo el personal no docente | ADMIN |
| GET | `/api/v1/staff/non-teaching/{id}` | Obtener personal por ID | ADMIN, TEACHER |
| GET | `/api/v1/staff/non-teaching/by-role/{role}` | Filtrar por rol | ADMIN |
| GET | `/api/v1/staff/non-teaching/search?query=` | Buscar personal | ADMIN |
| POST | `/api/v1/staff/non-teaching` | Crear personal | ADMIN |
| PUT | `/api/v1/staff/non-teaching/{id}` | Actualizar personal | ADMIN |
| DELETE | `/api/v1/staff/non-teaching/{id}` | Eliminar personal | ADMIN |

### Asistencia

| Método | Endpoint | Descripción | Rol Requerido |
|--------|----------|-------------|---------------|
| POST | `/api/v1/staff/attendance` | Registrar asistencia | ADMIN |
| PUT | `/api/v1/staff/attendance/{id}` | Actualizar asistencia | ADMIN |
| GET | `/api/v1/staff/attendance/teaching/{staffId}` | Asistencia de docente | ADMIN, TEACHER |
| GET | `/api/v1/staff/attendance/non-teaching/{staffId}` | Asistencia de no docente | ADMIN, TEACHER |
| DELETE | `/api/v1/staff/attendance/{id}` | Eliminar registro | ADMIN |

## Ejemplos de Uso

### Crear Personal Docente

```bash
POST /api/v1/staff/teaching
Content-Type: application/json
Authorization: Bearer {token}

{
  "firstName": "María",
  "lastName": "González",
  "email": "maria.gonzalez@sigep.edu.mx",
  "phoneNumber": "+52 55 1234 5678",
  "documentNumber": "GOMX850315ABC",
  "birthDate": "1985-03-15",
  "address": "Calle Principal 123, CDMX",
  "hireDate": "2020-01-15",
  "monthlySalary": 15000.00,
  "specialization": "Inglés avanzado - Certificación TOEFL",
  "observations": "Excelente desempeño",
  "emergencyContactName": "Juan González",
  "emergencyContactPhone": "+52 55 9876 5432"
}
```

### Crear Personal No Docente

```bash
POST /api/v1/staff/non-teaching
Content-Type: application/json
Authorization: Bearer {token}

{
  "firstName": "Carlos",
  "lastName": "Ramírez",
  "email": "carlos.ramirez@cleaning.com",
  "phoneNumber": "+52 55 2222 3333",
  "documentNumber": "RAMC920712XYZ",
  "birthDate": "1992-07-12",
  "address": "Av. Reforma 456, CDMX",
  "hireDate": "2021-06-01",
  "hourlyRate": 80.00,
  "role": "CLEANING",
  "companyName": "Servicios de Limpieza SA",
  "assignedTasks": "Limpieza de aulas, baños y oficinas administrativas",
  "observations": "Puntual y responsable",
  "emergencyContactName": "Ana Ramírez",
  "emergencyContactPhone": "+52 55 4444 5555"
}
```

### Registrar Asistencia

```bash
POST /api/v1/staff/attendance
Content-Type: application/json
Authorization: Bearer {token}

{
  "teachingStaffId": 1,
  "attendanceDate": "2025-10-22",
  "checkInTime": "08:00:00",
  "checkOutTime": "16:00:00",
  "status": "PRESENT",
  "notes": "Día completo de clases"
}
```

### Registrar Asistencia de Personal No Docente

```bash
POST /api/v1/staff/attendance
Content-Type: application/json
Authorization: Bearer {token}

{
  "nonTeachingStaffId": 5,
  "attendanceDate": "2025-10-22",
  "checkInTime": "06:00:00",
  "checkOutTime": "14:00:00",
  "status": "PRESENT",
  "hoursWorked": 8.0,
  "notes": "Limpieza completa del edificio"
}
```

## Roles de Personal No Docente

- `CLEANING` - Personal de limpieza
- `MAINTENANCE` - Personal de mantenimiento
- `IT_SUPPORT` - Soporte de sistemas/TI
- `SECURITY` - Seguridad
- `ADMINISTRATION` - Administración
- `OTHER` - Otro

## Estados de Pago (Docentes)

- `UP_TO_DATE` - Al día
- `PENDING` - Pendiente
- `OVERDUE` - Atrasado
- `PARTIALLY_PAID` - Parcialmente pagado

## Estados de Asistencia

- `PRESENT` - Presente
- `ABSENT` - Ausente
- `LATE` - Tarde
- `EXCUSED` - Justificado
- `SICK_LEAVE` - Licencia por enfermedad
- `VACATION` - Vacaciones

## Integración con Otros Módulos

### Módulo de Pagos (Payments)

El módulo de Staff **NO** gestiona pagos directamente. La información de salarios y tarifas por hora se utiliza únicamente para:
- Visualización en el perfil del empleado
- Cálculos estimados de ganancias
- Reportes administrativos

La gestión real de pagos se realiza en el módulo `payments`, que puede consultar:
- Salario mensual de docentes desde `TeachingStaff`
- Tarifa por hora y horas trabajadas de no docentes desde `NonTeachingStaff` y `StaffAttendance`

### Módulo de Cursos (Courses)

El módulo de Staff se integra con Courses para:
- Mostrar cursos asignados a cada docente
- Contar estudiantes por docente
- Actualizar el contador `assignedStudentsCount` cuando se asignan/desasignan cursos

### Módulo de Estudiantes (Students)

Relacionado indirectamente a través de:
- Cursos asignados
- Tutorías o responsabilidades académicas

## Auditoría

Todas las entidades de Staff extienden `AuditMetadata` y registran automáticamente:
- `createdAt` - Fecha de creación
- `createdBy` - Usuario que creó el registro
- `updatedAt` - Fecha de última modificación
- `updatedBy` - Usuario que modificó
- `isActive` - Estado de soft-delete

## Caché

Los servicios implementan caché de Redis para optimizar el rendimiento:
- `@Cacheable` en operaciones de lectura
- `@CacheEvict` en operaciones de escritura

Claves de caché:
- `teachingStaff` - Personal docente
- `nonTeachingStaff` - Personal no docente

## Seguridad

### Permisos por Endpoint

- **Solo ADMIN** puede:
  - Crear, actualizar y eliminar personal
  - Ver listados completos
  - Registrar y modificar asistencias
  
- **ADMIN y TEACHER** pueden:
  - Ver detalles individuales del personal
  - Consultar asistencias

## Validaciones

- Email único por persona
- Documento de identidad único por persona
- Fechas válidas (fecha de nacimiento < fecha de contratación < hoy)
- Salarios y tarifas > 0
- En asistencia: debe especificarse teachingStaffId O nonTeachingStaffId (no ambos)

## Notas Importantes

1. **Soft Delete**: Al eliminar personal, solo se marca como inactivo (`isActive = false`)
2. **Pagos**: Este módulo NO gestiona transacciones de pago, solo información salarial
3. **Asistencia**: Los registros de asistencia son inmutables (solo se pueden actualizar el mismo día)
4. **Horas Trabajadas**: Calculadas automáticamente para personal no docente basado en check-in/check-out

## Próximas Mejoras

- [ ] Integración con módulo de reportes para generar informes de asistencia
- [ ] Cálculo automático de nómina basado en asistencia
- [ ] Sistema de notificaciones para ausentismo recurrente
- [ ] Dashboard de métricas de personal
- [ ] Exportación de reportes a PDF/Excel

