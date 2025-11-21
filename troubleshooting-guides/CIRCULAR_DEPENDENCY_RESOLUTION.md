# Resolución de Dependencia Circular: Students ↔ Courses

**Fecha:** 2025-11-10  
**Estado:** ✅ RESUELTO

---

## 🚨 Problema

Al intentar implementar las funcionalidades faltantes del módulo Students para cumplir con el API_CONTRACT, se agregó una dependencia del módulo `courses` en `students` para acceder al `EnrollmentRepository`.

Sin embargo, el módulo `courses` ya tenía una dependencia del módulo `students`, creando una **dependencia circular**:

```
students → courses → students (CICLO)
```

**Error de Gradle:**
```
Circular dependency between the following tasks:
:courses:classes
\--- :courses:compileJava
     +--- :courses:compileKotlin
     |    \--- :students:jar
     ...
```

---

## ✅ Solución Implementada

Se aplicó el patrón **Service Provider Pattern** con DTOs compartidos en el módulo `common`:

### Arquitectura de la Solución

```
┌─────────────────────────────────────────────────────┐
│                     common                          │
│                                                     │
│  • EnrollmentSummaryDto                            │
│  • EnrollmentServiceProvider (Interface)           │
└─────────────────────────────────────────────────────┘
           ↑                            ↑
           │                            │
    implements                      uses
           │                            │
┌──────────┴──────────┐      ┌─────────┴──────────┐
│      courses        │      │      students      │
│                     │      │                    │
│  • EnrollmentService│      │  • StudentService  │
│  • EnrollmentService│      │                    │
│    ProviderImpl     │      │  (usa la interfaz) │
└─────────────────────┘      └────────────────────┘
```

### Cambios Realizados

#### 1. **Módulo Common** - DTOs y Service Provider Interface

**Archivo:** `common/src/main/kotlin/com/sigep/common/application/dto/EnrollmentDtos.kt`
```kotlin
data class EnrollmentSummaryDto(
    val id: Long,
    val studentId: Long,
    val courseId: Long,
    val courseName: String,
    val enrollmentDate: LocalDate,
    val status: String,
    val finalGrade: BigDecimal?,
    val completionDate: LocalDate?
)
```

**Archivo:** `common/src/main/kotlin/com/sigep/common/application/service/EnrollmentServiceProvider.kt`
```kotlin
interface EnrollmentServiceProvider {
    fun getEnrollmentsByStudent(studentId: Long): List<EnrollmentSummaryDto>
    fun getCurrentEnrollmentByStudent(studentId: Long): EnrollmentSummaryDto?
    fun getEnrollmentsByStudentAndStatus(studentId: Long, status: String): List<EnrollmentSummaryDto>
}
```

#### 2. **Módulo Courses** - Implementación del Provider

**Archivo:** `courses/src/main/kotlin/com/sigep/courses/application/service/EnrollmentServiceProviderImpl.kt`
```kotlin
@Service
class EnrollmentServiceProviderImpl(
    private val enrollmentRepository: EnrollmentRepository
) : EnrollmentServiceProvider {
    
    override fun getEnrollmentsByStudent(studentId: Long): List<EnrollmentSummaryDto> {
        // Implementación usando EnrollmentRepository
    }
    
    override fun getCurrentEnrollmentByStudent(studentId: Long): EnrollmentSummaryDto? {
        // Implementación para obtener el enrollment activo
    }
    
    // ...
}
```

#### 3. **Módulo Students** - Uso del Provider

**Archivo:** `students/src/main/kotlin/com/sigep/students/application/service/StudentService.kt`
```kotlin
@Service
class StudentService(
    private val studentRepository: StudentRepository,
    private val enrollmentServiceProvider: EnrollmentServiceProvider  // ✅ Inyecta interfaz
) {
    
    private fun Student.toDetailDto(): StudentDetailDto {
        val currentEnrollment = enrollmentServiceProvider.getCurrentEnrollmentByStudent(this.id!!)
        val allEnrollments = enrollmentServiceProvider.getEnrollmentsByStudent(this.id!!)
        
        return StudentDetailDto(
            // ...
            currentCourseId = currentEnrollment?.courseId,
            currentCourseName = currentEnrollment?.courseName,
            courseHistory = allEnrollments,
            // ...
        )
    }
}
```

#### 4. **Actualización de DTOs**

**Archivo:** `students/src/main/kotlin/com/sigep/students/application/dto/StudentDtos.kt`
```kotlin
data class StudentDetailDto(
    // ...
    val courseHistory: List<EnrollmentSummaryDto>,  // ✅ Usa DTO de common
    // ...
)
```

---

## 🎯 Beneficios de Esta Solución

### 1. **Sin Dependencias Circulares**
- `students` solo depende de `common`
- `courses` solo depende de `common` y `students`
- No hay ciclos

### 2. **Preparado para Microservicios**
- La interfaz `EnrollmentServiceProvider` puede ser reemplazada fácilmente por una llamada HTTP o Kafka
- Los DTOs están en `common`, compartidos entre módulos

### 3. **Principios SOLID**
- **Dependency Inversion Principle**: Students depende de una abstracción (interfaz), no de una implementación concreta
- **Single Responsibility**: Cada módulo tiene responsabilidades claras

### 4. **Testeable**
- Se puede mockear fácilmente `EnrollmentServiceProvider` en tests unitarios de `StudentService`

---

## 📋 Funcionalidades Implementadas (Cumple API_CONTRACT)

### ✅ Endpoints Implementados

| Endpoint | Método | Descripción | Estado |
|----------|--------|-------------|---------|
| `/api/v1/students` | GET | Listar estudiantes con paginación | ✅ |
| `/api/v1/students/{id}` | GET | Obtener detalle de estudiante + historial | ✅ |
| `/api/v1/students/search` | GET | Buscar estudiantes | ✅ |
| `/api/v1/students/guardian/{guardianId}` | GET | Estudiantes por guardián | ✅ |
| `/api/v1/students` | POST | Crear estudiante | ✅ |
| `/api/v1/students/{id}` | PUT | Actualizar estudiante | ✅ |
| `/api/v1/students/{id}` | DELETE | Eliminar estudiante | ✅ |

### ✅ DTOs Implementados

#### StudentDto (Básico)
```typescript
interface StudentDto {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  documentNumber: string;
  dateOfBirth: string;
  enrollmentDate: string;
  guardianId: number | null;
  currentCourseId: number | null;      // ✅ Implementado
  currentCourseName: string | null;    // ✅ Implementado
  active: boolean;
  createdAt: string;
  updatedAt: string;
}
```

#### StudentDetailDto (Completo)
```typescript
interface StudentDetailDto {
  // ...todos los campos de StudentDto
  address: string;
  phoneNumber: string;
  emergencyContact: string;
  medicalNotes: string | null;
  courseHistory: EnrollmentSummaryDto[];  // ✅ Implementado
}
```

#### EnrollmentSummaryDto (Historial)
```typescript
interface EnrollmentSummaryDto {
  id: number;
  studentId: number;
  courseId: number;
  courseName: string;
  enrollmentDate: string;
  status: string;
  finalGrade: number | null;
  completionDate: string | null;
}
```

---

## 🧪 Verificación

### Build Exitoso
```bash
.\gradlew.bat clean build -x test
# BUILD SUCCESSFUL in 42s
```

### Sin Errores de Compilación
- ✅ Todos los módulos compilan correctamente
- ⚠️ Solo warnings menores (unused functions, unnecessary assertions)

---

## 📚 Documentación Actualizada

- ✅ `API_CONTRACT.md` actualizado con DTOs correctos
- ✅ Este documento de resolución creado

---

## 🔄 Migración Futura a Microservicios

Cuando se migre a microservicios, solo se necesita:

1. Crear un nuevo módulo `students-api-client` que implemente `EnrollmentServiceProvider`
2. La implementación hará llamadas HTTP al servicio de Courses
3. Cambiar la dependencia en `students/build.gradle.kts`

**Ejemplo:**
```kotlin
@Service
class EnrollmentServiceProviderHttpClient(
    private val webClient: WebClient
) : EnrollmentServiceProvider {
    
    override fun getEnrollmentsByStudent(studentId: Long): List<EnrollmentSummaryDto> {
        return webClient.get()
            .uri("/api/v1/enrollments/student/$studentId")
            .retrieve()
            .bodyToFlux(EnrollmentSummaryDto::class.java)
            .collectList()
            .block() ?: emptyList()
    }
}
```

---

## ✅ Conclusión

El problema de dependencia circular se resolvió exitosamente aplicando:
- **Service Provider Pattern**
- **Dependency Inversion Principle**
- **Shared DTOs en Common**

La arquitectura ahora es:
- ✅ Compilable
- ✅ Sin ciclos de dependencia
- ✅ Preparada para microservicios
- ✅ Cumple con el API_CONTRACT
- ✅ Testeable y mantenible

