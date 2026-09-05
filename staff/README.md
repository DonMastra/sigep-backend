# Módulo de Gestión de Personal (Staff)

> **Última actualización**: Marzo 2026 — Integración completa con frontend Angular.

## Descripción

El módulo **Staff** proporciona funcionalidades completas para la gestión del personal docente y no docente de la institución educativa. Expone una API REST con soporte CRUD completo para ambos tipos de personal, registro de asistencia y estadísticas mensuales.

---

## Características Principales

### 👨‍🏫 Personal Docente (Teaching Staff)

- **Datos Personales**: Nombre, email, teléfono, documento, fecha de nacimiento, dirección
- **Información Laboral**: Fecha de contratación, salario mensual, estado de pago, especialización
- **Gestión Académica**: Contador de estudiantes asignados, cursos asignados (integración con módulo courses)
- **Actividad mensual**: Historial fechado de asistencia y horas, con salario mensual vigente como importe administrativo de referencia
- **Contacto de Emergencia**: Nombre y teléfono (se aceptan como dos campos separados o como string único `"Nombre / Teléfono"`)
- **Soft Delete**: Desactivación lógica (no borrado físico)

### 🧹 Personal No Docente (Non-Teaching Staff)

- **Datos Personales**: Nombre, email, teléfono, documento, fecha de nacimiento, dirección
- **Información Laboral**: Fecha de contratación, tarifa por hora, rol, empresa
- **Gestión de actividad**: Tareas asignadas, historial mensual de horas e importe administrativo estimado
- **Asistencia**: Estadísticas mensuales calculadas sobre días laborales reales (L-V)
- **Contacto de Emergencia**: Dos campos separados o string único (se acepta cualquier formato)
- **Soft Delete**: Desactivación lógica

### 📊 Control de Asistencia

- Registro de entrada/salida (check-in / check-out)
- Estados: `PRESENT`, `ABSENT`, `LATE`, `EXCUSED`, `SICK_LEAVE`, `VACATION`
- Horas trabajadas por registro para docentes y no docentes
- Reportes por período con estadísticas automáticas

---

## Arquitectura

```
staff/
├── domain/model/
│   ├── TeachingStaff.kt          # Entidad personal docente
│   ├── NonTeachingStaff.kt       # Entidad personal no docente (+ enum NonTeachingRole)
│   └── StaffAttendance.kt        # Registro de asistencia (+ enum AttendanceStatus)
├── application/
│   ├── dto/
│   │   ├── TeachingStaffDto.kt   # DTOs, CreateRequest, UpdateRequest (docente)
│   │   ├── NonTeachingStaffDto.kt # DTOs, CreateRequest, UpdateRequest (no docente)
│   │   └── StaffAttendanceDto.kt  # DTOs de asistencia
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
└── presentation/controller/
    ├── TeachingStaffController.kt
    ├── NonTeachingStaffController.kt
    └── StaffAttendanceController.kt
```

---

## API Endpoints

### Personal Docente — `GET /api/v1/staff/teaching`

| Método | Endpoint | Descripción | Rol |
|--------|----------|-------------|-----|
| GET | `/api/v1/staff/teaching` | Listar docentes (paginado) | ADMIN |
| GET | `/api/v1/staff/teaching/{id}` | Detalle administrativo con asistencia del mes | ADMIN |
| GET | `/api/v1/staff/teaching/search?query=` | Buscar por nombre/email/documento | ADMIN |
| GET | `/api/v1/staff/teaching/assignable` | Cuentas activas enlazadas a docentes; el `id` es de `users` | ADMIN |
| POST | `/api/v1/staff/teaching` | Crear docente | ADMIN |
| PUT | `/api/v1/staff/teaching/{id}` | Actualizar docente | ADMIN |
| POST | `/api/v1/staff/teaching/{id}/photo` | Cargar foto de legajo | ADMIN |
| GET | `/api/v1/staff/teaching/{id}/photo` | Consultar foto; TEACHER solo la propia | ADMIN, TEACHER |
| DELETE | `/api/v1/staff/teaching/{id}` | Desactivar docente (soft delete) | ADMIN |

### Personal No Docente — `/api/v1/staff/non-teaching`

| Método | Endpoint | Descripción | Rol |
|--------|----------|-------------|-----|
| GET | `/api/v1/staff/non-teaching` | Listar no docentes (paginado) | ADMIN |
| GET | `/api/v1/staff/non-teaching/{id}` | Detalle administrativo con horas y asistencia del mes | ADMIN |
| GET | `/api/v1/staff/non-teaching/by-role/{role}` | Filtrar por rol | ADMIN |
| GET | `/api/v1/staff/non-teaching/search?query=` | Buscar por nombre/email/doc/empresa | ADMIN |
| POST | `/api/v1/staff/non-teaching` | Crear no docente | ADMIN |
| PUT | `/api/v1/staff/non-teaching/{id}` | Actualizar no docente | ADMIN |
| DELETE | `/api/v1/staff/non-teaching/{id}` | Desactivar no docente (soft delete) | ADMIN |

### Asistencia — `/api/v1/staff/attendance`

| Método | Endpoint | Descripción | Rol |
|--------|----------|-------------|-----|
| POST | `/api/v1/staff/attendance` | Registrar asistencia | ADMIN |
| PUT | `/api/v1/staff/attendance/{id}` | Actualizar registro | ADMIN |
| GET | `/api/v1/staff/attendance/teaching/{staffId}` | Asistencia de docente por período; TEACHER solo su legajo vinculado | ADMIN, TEACHER |
| GET | `/api/v1/staff/attendance/teaching/{staffId}/monthly-summary?month=YYYY-MM` | Resumen mensual docente; TEACHER solo su legajo vinculado | ADMIN, TEACHER |
| GET | `/api/v1/staff/attendance/non-teaching/{staffId}` | Asistencia de no docente por período | ADMIN |
| GET | `/api/v1/staff/attendance/non-teaching/{staffId}/monthly-summary?month=YYYY-MM` | Resumen mensual de actividad e importe estimado | ADMIN |
| DELETE | `/api/v1/staff/attendance/{id}` | Eliminar registro | ADMIN |

---

## Modelos de Respuesta

### `TeachingStaffDto`

```json
{
  "id": 1,
  "firstName": "María",
  "lastName": "González",
  "fullName": "María González",
  "email": "maria.gonzalez@sigep.edu.mx",
  "phoneNumber": "+52 55 1234 5678",
  "documentNumber": "GOMX850315ABC",
  "birthDate": "1985-03-15",
  "address": "Calle Principal 123, CDMX",
  "hireDate": "2020-01-15",
  "monthlySalary": 15000.00,
  "currency": "ARS",
  "paymentStatus": "UP_TO_DATE",
  "status": "ACTIVE",
  "assignedStudentsCount": 24,
  "assignedCourses": null,
  "specialization": "Inglés avanzado",
  "observations": null,
  "notes": null,
  "emergencyContactName": "Juan González",
  "emergencyContactPhone": "+52 55 9876 5432",
  "totalWorkingDaysInMonth": 22,
  "attendanceStats": {
    "totalDays": 18,
    "presentDays": 16,
    "absentDays": 1,
    "lateDays": 1,
    "attendanceRate": 72.73
  },
  "photoUrl": null,
  "createdAt": "2020-01-15T09:00:00Z",
  "updatedAt": "2026-03-01T14:30:00Z"
}
```

### `NonTeachingStaffDto`

```json
{
  "id": 5,
  "firstName": "Carlos",
  "lastName": "Ramírez",
  "fullName": "Carlos Ramírez",
  "email": "carlos.ramirez@cleaning.com",
  "phoneNumber": "+52 55 2222 3333",
  "documentNumber": "RAMC920712XYZ",
  "birthDate": "1992-07-12",
  "address": "Av. Reforma 456, CDMX",
  "hireDate": "2021-06-01",
  "hourlyRate": 80.00,
  "currency": "ARS",
  "role": "CLEANING",
  "position": "CLEANING",
  "companyName": "Servicios de Limpieza SA",
  "company": "Servicios de Limpieza SA",
  "assignedTasks": "Limpieza de aulas y oficinas",
  "observations": null,
  "emergencyContactName": "Ana Ramírez",
  "emergencyContactPhone": "+52 55 4444 5555",
  "status": "ACTIVE",
  "hoursWorkedThisMonth": 144.0,
  "estimatedEarningsThisMonth": 11520.00,
  "totalWorkingDaysInMonth": 22,
  "attendanceStats": {
    "totalDays": 18,
    "presentDays": 17,
    "absentDays": 1,
    "lateDays": 0,
    "attendanceRate": 77.27
  },
  "photoUrl": null,
  "createdAt": "2021-06-01T08:00:00Z",
  "updatedAt": "2026-03-01T14:30:00Z"
}
```

---

## Ejemplos de Requests

### Crear Docente — formas equivalentes de enviar contacto de emergencia

**Forma 1 (campos separados — recomendada):**
```bash
POST /api/v1/staff/teaching
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
  "specialization": "Inglés avanzado",
  "emergencyContactName": "Juan González",
  "emergencyContactPhone": "+52 55 9876 5432"
}
```

**Forma 2 (campo único — compatible con frontend legacy):**
```bash
{
  ...
  "emergencyContact": "Juan González / +52 55 9876 5432"
}
```

### Crear No Docente — aliases de campos

El frontend puede enviar `position` en lugar de `role`, y `company` en lugar de `companyName`:

```bash
POST /api/v1/staff/non-teaching
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
  "position": "CLEANING",
  "company": "Servicios de Limpieza SA",
  "assignedTasks": "Limpieza de aulas y baños",
  "emergencyContact": "Ana Ramírez / +52 55 4444 5555"
}
```

### Registrar Asistencia

```bash
POST /api/v1/staff/attendance
Authorization: Bearer {token}

{
  "teachingStaffId": 1,
  "attendanceDate": "2026-03-11",
  "checkInTime": "08:00:00",
  "checkOutTime": "16:00:00",
  "status": "PRESENT",
  "notes": "Día completo de clases"
}
```

---

## Enumeraciones

### `NonTeachingRole`

| Valor | Descripción | Alias frontend |
|-------|-------------|----------------|
| `CLEANING` | Personal de limpieza | — |
| `MAINTENANCE` | Mantenimiento | — |
| `IT_SUPPORT` | Soporte de TI/sistemas | `IT` |
| `IT` | Alias de IT_SUPPORT | — |
| `SECURITY` | Seguridad | — |
| `ADMINISTRATION` | Administración | — |
| `OTHER` | Otro | — |

> **Nota**: El valor `IT` se acepta en requests y se almacena como `IT`. El frontend puede enviar `IT` o `IT_SUPPORT` indistintamente.

### `PaymentStatus` (Docentes)

| Valor | Descripción |
|-------|-------------|
| `UP_TO_DATE` | Al día |
| `PENDING` | Pendiente |
| `OVERDUE` | Atrasado/Vencido |
| `PARTIALLY_PAID` | Pago parcial |

### `AttendanceStatus`

| Valor | Descripción |
|-------|-------------|
| `PRESENT` | Presente |
| `ABSENT` | Ausente |
| `LATE` | Tarde |
| `EXCUSED` | Justificado |
| `SICK_LEAVE` | Licencia por enfermedad |
| `VACATION` | Vacaciones |

---

## Compatibilidad Frontend ↔ Backend

Esta API fue diseñada para ser compatible con el frontend Angular del proyecto SiGEP. A continuación se detallan los mapeos de compatibilidad:

| Campo frontend | Campo backend | Estrategia |
|---|---|---|
| `position` | `role` | Se aceptan ambos en requests; ambos se devuelven en responses |
| `company` | `companyName` | Idem |
| `emergencyContact` (string) | `emergencyContactName` + `emergencyContactPhone` | Split automático por `/`; también se aceptan los campos separados |
| `IT` (rol) | `IT_SUPPORT` | El enum acepta ambos valores |
| `status: 'ACTIVE'/'INACTIVE'` | `isActive: boolean` | Conversión automática en la capa de servicio |
| `totalWorkingDaysInMonth` | — | Calculado por el backend (días L-V del mes actual) |
| `attendanceRate` | — | Calculado sobre días laborales reales (no sobre registros totales) |

---

## Caché

Los servicios usan Redis Cache para optimizar lecturas:

| Cache Key | Invalidación |
|-----------|-------------|
| `teachingStaff` | Al crear, actualizar o eliminar cualquier docente |
| `nonTeachingStaff` | Al crear, actualizar o eliminar cualquier no docente |

---

## Seguridad y Validaciones

- **Solo ADMIN**: crear, actualizar, eliminar personal y asistencias; ver listados
- **ADMIN + TEACHER**: consultar únicamente la asistencia del propio docente cuando el actor es TEACHER
- Email único por tipo de personal
- Número de documento único por tipo de personal
- En asistencia: exactamente uno de `teachingStaffId` o `nonTeachingStaffId` debe estar presente

---

## Integración con Otros Módulos

- **Courses**: Los cursos asignados a docentes se obtienen vía integración con el módulo `courses`. El campo `assignedCourses` se puebla al obtener el detalle.
- **Payments**: El módulo staff **no gestiona pagos**. Expone `monthlySalary` y `hourlyRate` para que el módulo `payments` calcule nómina.
- **Students**: Relacionado indirectamente a través de cursos asignados.

---

## Notas de Implementación

1. **Soft Delete**: `DELETE` solo marca `isActive = false`. El registro permanece en la base de datos.
2. **`totalWorkingDaysInMonth`**: Se calcula dinámicamente como la cantidad de días de lunes a viernes del mes en curso desde la fecha de contratación.
3. **`attendanceRate`**: tanto el campo compatible del mes actual como `monthly-summary` miden presencia sobre registros. El resumen mensual expone por separado `dataCoverageRate` sobre días hábiles transcurridos.
4. **`estimatedEarningsThisMonth`**: campo compatible del mes actual no docente. Para consulta histórica usar `monthly-summary`, que conserva la tarifa y moneda aplicadas a cada nuevo registro desde V39.
5. **Historial mensual**: `staff_attendance` conserva la fecha de cada registro para docentes y no docentes. El resumen separa presencia sobre registros de cobertura de carga sobre días hábiles transcurridos.
6. **Importe no docente**: es una referencia administrativa (`horas × tarifa`), no una liquidación de haberes ni un estado de pago. `amountIsHistorical` solo es verdadero cuando todo el importe usa valores preservados en los registros.
7. **Importe docente**: el resumen devuelve el salario mensual vigente del legajo con `compensationBasis=MONTHLY_SALARY` y `amountIsHistorical=false`. No multiplica horas ni representa una liquidación o el salario histórico del período seleccionado.
