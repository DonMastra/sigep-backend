# Módulo Common - Documentación

## 📋 Descripción

El módulo **Common** es el módulo compartido fundamental que proporciona componentes, utilidades y abstracciones reutilizables para todos los demás módulos del sistema SiGEP. Implementa los conceptos base de Domain-Driven Design (DDD) y patrones arquitectónicos comunes.

---

## 🎯 Responsabilidades

### 1. Abstracciones de Dominio (DDD)
- Interfaces y clases base para agregados, entidades y value objects
- Marcadores de Aggregate Roots
- Metadata de auditoría

### 2. DTOs Compartidos
- Respuestas API estandarizadas
- Paginación
- Requests comunes

### 3. Manejo de Excepciones
- Excepciones de negocio personalizadas
- Handler global de excepciones
- Mensajes de error consistentes

### 4. Configuración Compartida
- Auditoría JPA
- Serialización/Deserialización
- Utilidades comunes

---

## 🏗️ Estructura del Módulo

```
common/
├── src/main/kotlin/com/sigep/common/
│   ├── domain/                           # Capa de dominio
│   │   ├── AggregateRoot.kt             # Marker interface para agregados
│   │   ├── BaseEntity.kt                # Entidad base
│   │   ├── ValueObject.kt               # Marker para value objects
│   │   └── exception/                   # Excepciones de dominio
│   │       ├── BusinessException.kt
│   │       ├── ResourceNotFoundException.kt
│   │       └── DuplicateResourceException.kt
│   │
│   ├── application/                      # Capa de aplicación
│   │   ├── dto/                         # DTOs compartidos
│   │   │   ├── ApiResponse.kt           # Wrapper de respuestas
│   │   │   ├── PageResponse.kt          # Respuesta paginada
│   │   │   └── PageRequest.kt           # Request de paginación
│   │   └── exception/                   # Excepciones de aplicación
│   │       └── Exceptions.kt            # Excepciones personalizadas
│   │
│   └── infrastructure/                   # Capa de infraestructura
│       ├── config/
│       │   ├── JpaAuditingConfig.kt    # Configuración de auditoría
│       │   └── GlobalExceptionHandler.kt # Handler global
│       └── audit/
│           ├── AuditMetadata.kt        # Metadata de auditoría
│           └── AuditorAwareImpl.kt     # Implementación de auditor
│
└── build.gradle.kts
```

---

## 🔧 Componentes Principales

### 1. Domain Layer (Capa de Dominio)

#### AggregateRoot.kt

**Propósito**: Marker interface para identificar agregados raíz en DDD

```kotlin
package com.sigep.common.domain

interface AggregateRoot
```

**Uso en módulos**:
```kotlin
@Entity
data class Student(...) : AggregateRoot

@Entity
data class Course(...) : AggregateRoot
```

**Concepto DDD**: Un Aggregate Root es la entidad principal de un agregado que garantiza la consistencia de todas las entidades relacionadas dentro del agregado.

---

#### BaseEntity.kt

**Propósito**: Clase base opcional para entidades comunes (si se implementa)

---

#### ValueObject.kt

**Propósito**: Marker interface para Value Objects en DDD

```kotlin
package com.sigep.common.domain

interface ValueObject
```

**Concepto DDD**: Los Value Objects son objetos inmutables definidos por sus atributos, sin identidad propia.

---

#### Excepciones de Dominio

**BusinessException.kt** - Excepción base para errores de negocio
```kotlin
open class BusinessException(
    override val message: String,
    val code: String = "BUSINESS_ERROR"
) : RuntimeException(message)
```

**ResourceNotFoundException.kt** - Recurso no encontrado
```kotlin
class ResourceNotFoundException(
    message: String,
    code: String = "RESOURCE_NOT_FOUND"
) : BusinessException(message, code)
```

**DuplicateResourceException.kt** - Recurso duplicado
```kotlin
class DuplicateResourceException(
    message: String,
    code: String = "DUPLICATE_RESOURCE"
) : BusinessException(message, code)
```

---

### 2. Application Layer (Capa de Aplicación)

#### ApiResponse.kt

**Propósito**: Wrapper estándar para todas las respuestas de la API

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
```

**Métodos Helper**:
```kotlin
// Respuesta exitosa con datos
ApiResponse.success(data, "Operation successful")

// Respuesta exitosa sin datos
ApiResponse.successNoContent("Deleted successfully")

// Respuesta de error
ApiResponse.error<T>("Error message")
```

**Ejemplo de uso en controllers**:
```kotlin
@GetMapping("/{id}")
fun getStudent(@PathVariable id: Long): ResponseEntity<ApiResponse<StudentDto>> {
    val student = studentService.findById(id)
    return ResponseEntity.ok(ApiResponse.success(student, "Student retrieved"))
}
```

**Respuesta JSON**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "John Doe"
  },
  "message": "Student retrieved",
  "timestamp": "2025-11-04T10:00:00Z"
}
```

---

#### PageResponse.kt

**Propósito**: Wrapper para respuestas paginadas

```kotlin
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
```

**Uso en servicios**:
```kotlin
fun getAllStudents(pageable: Pageable): ApiResponse<PageResponse<StudentDto>> {
    val page = studentRepository.findAll(pageable)
    
    val response = PageResponse(
        content = page.content.map { it.toDto() },
        page = page.number,
        size = page.size,
        totalElements = page.totalElements,
        totalPages = page.totalPages
    )
    
    return ApiResponse.success(response)
}
```

**Respuesta JSON**:
```json
{
  "success": true,
  "data": {
    "content": [
      { "id": 1, "name": "Student 1" },
      { "id": 2, "name": "Student 2" }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 25,
    "totalPages": 3
  },
  "timestamp": "2025-11-04T10:00:00Z"
}
```

---

#### PageRequest.kt

**Propósito**: DTO para parámetros de paginación en requests

```kotlin
data class PageRequest(
    val page: Int = 0,
    val size: Int = 10,
    val sort: String? = null,
    val order: String? = "ASC"
)
```

---

#### Exceptions.kt

**Propósito**: Excepciones personalizadas de la capa de aplicación

```kotlin
// Excepción base de negocio
open class BusinessException(
    override val message: String,
    val code: String = "BUSINESS_ERROR"
) : RuntimeException(message)

// Recurso no encontrado (404)
class ResourceNotFoundException(
    message: String,
    code: String = "RESOURCE_NOT_FOUND"
) : BusinessException(message, code)

// Error de validación (400)
class ValidationException(
    message: String,
    val details: List<String> = emptyList(),
    code: String = "VALIDATION_ERROR"
) : BusinessException(message, code)

// No autorizado (401)
class UnauthorizedException(
    message: String = "Unauthorized access",
    code: String = "UNAUTHORIZED"
) : BusinessException(message, code)

// Prohibido (403)
class ForbiddenException(
    message: String = "Forbidden access",
    code: String = "FORBIDDEN"
) : BusinessException(message, code)

// Recurso duplicado (409)
class DuplicateResourceException(
    message: String,
    code: String = "DUPLICATE_RESOURCE"
) : BusinessException(message, code)
```

**Uso en servicios**:
```kotlin
fun getStudent(id: Long): StudentDto {
    return studentRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("Student not found with id: $id") }
        .toDto()
}
```

---

### 3. Infrastructure Layer (Capa de Infraestructura)

#### GlobalExceptionHandler.kt

**Propósito**: Manejador global de excepciones para toda la API

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.message))
    }
    
    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(ex.message))
    }
    
    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error(ex.message))
    }
    
    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error(ex.message))
    }
    
    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResource(ex: DuplicateResourceException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ApiResponse.error(ex.message))
    }
    
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Validation failed: ${errors.joinToString(", ")}"))
    }
    
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Unexpected error", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred"))
    }
}
```

**Mapeo de Excepciones a HTTP Status**:

| Excepción | HTTP Status | Descripción |
|-----------|-------------|-------------|
| `ResourceNotFoundException` | 404 Not Found | Recurso no encontrado |
| `ValidationException` | 400 Bad Request | Error de validación |
| `UnauthorizedException` | 401 Unauthorized | No autenticado |
| `ForbiddenException` | 403 Forbidden | No autorizado |
| `DuplicateResourceException` | 409 Conflict | Recurso duplicado |
| `BusinessException` | 400 Bad Request | Error de lógica de negocio |
| `MethodArgumentNotValidException` | 400 Bad Request | Validación de Bean Validation |
| `Exception` | 500 Internal Server Error | Error inesperado |

---

#### AuditMetadata.kt

**Propósito**: Clase base para entidades que requieren auditoría

```kotlin
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AuditMetadata {
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
    
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    var createdBy: String = "system"
    
    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
    
    @LastModifiedBy
    @Column(name = "updated_by")
    var updatedBy: String = "system"
    
    @Column(name = "is_active")
    var isActive: Boolean = true
}
```

**Uso en entidades**:
```kotlin
@Entity
@Table(name = "teaching_staff")
class TeachingStaff(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    val firstName: String,
    val lastName: String
    // ...otros campos
) : AuditMetadata()  // Hereda campos de auditoría
```

**Campos automáticos**:
- `createdAt`: Fecha/hora de creación (automático)
- `createdBy`: Usuario que creó (del SecurityContext)
- `updatedAt`: Fecha/hora de última modificación (automático)
- `updatedBy`: Usuario que modificó (del SecurityContext)
- `isActive`: Flag de soft delete

---

#### AuditorAwareImpl.kt

**Propósito**: Implementación para obtener el usuario actual del SecurityContext

```kotlin
@Component
class AuditorAwareImpl : AuditorAware<String> {
    override fun getCurrentAuditor(): Optional<String> {
        val authentication = SecurityContextHolder.getContext().authentication
        return if (authentication != null && authentication.isAuthenticated) {
            Optional.of(authentication.name)
        } else {
            Optional.of("system")
        }
    }
}
```

---

#### JpaAuditingConfig.kt

**Propósito**: Habilitar auditoría JPA en toda la aplicación

```kotlin
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
class JpaAuditingConfig
```

---

## 📦 Dependencias

```kotlin
dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")
}
```

---

## 🔄 Flujo de Uso

### 1. Flujo de Request/Response Estándar

```
┌─────────────┐
│   Request   │
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│   Controller     │ ← Recibe request
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│   Service        │ ← Lógica de negocio
└──────┬───────────┘   Puede lanzar excepciones
       │
       ▼
┌──────────────────┐
│   Repository     │ ← Acceso a datos
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│   Entity         │ ← Con AuditMetadata
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│   DTO            │ ← Conversión a DTO
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  ApiResponse<T>  │ ← Wrapper de respuesta
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│   Response       │ ← JSON al cliente
└──────────────────┘

Si hay error en cualquier punto ↓

┌──────────────────────────┐
│ GlobalExceptionHandler   │ ← Captura excepción
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│ ApiResponse.error()      │ ← Respuesta de error
└──────────────────────────┘
```

---

### 2. Ejemplo Completo de Uso

**Controller**:
```kotlin
@RestController
@RequestMapping("/api/v1/students")
class StudentController(private val studentService: StudentService) {
    
    @GetMapping("/{id}")
    fun getStudent(@PathVariable id: Long): ResponseEntity<ApiResponse<StudentDto>> {
        val student = studentService.findById(id)
        return ResponseEntity.ok(ApiResponse.success(student, "Student retrieved"))
    }
    
    @GetMapping
    fun getAllStudents(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<ApiResponse<PageResponse<StudentDto>>> {
        val students = studentService.findAll(PageRequest.of(page, size))
        return ResponseEntity.ok(ApiResponse.success(students))
    }
}
```

**Service**:
```kotlin
@Service
class StudentService(private val studentRepository: StudentRepository) {
    
    fun findById(id: Long): StudentDto {
        return studentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Student not found") }
            .toDto()
    }
    
    fun findAll(pageable: Pageable): PageResponse<StudentDto> {
        val page = studentRepository.findAll(pageable)
        return PageResponse(
            content = page.content.map { it.toDto() },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages
        )
    }
}
```

**Entity** (con auditoría):
```kotlin
@Entity
@Table(name = "students")
class Student(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    val firstName: String,
    val lastName: String
) : AuditMetadata(), AggregateRoot
```

---

## ✅ Convenciones y Mejores Prácticas

### 1. Uso de Excepciones

✅ **Correcto**:
```kotlin
if (!userRepository.existsById(userId)) {
    throw ResourceNotFoundException("User not found with id: $userId")
}
```

❌ **Incorrecto**:
```kotlin
if (!userRepository.existsById(userId)) {
    return null  // No usar null, lanzar excepción
}
```

---

### 2. Respuestas de API

✅ **Correcto**:
```kotlin
return ResponseEntity.ok(ApiResponse.success(data, "Success message"))
```

❌ **Incorrecto**:
```kotlin
return ResponseEntity.ok(data)  // Sin wrapper ApiResponse
```

---

### 3. Paginación

✅ **Correcto**:
```kotlin
return ApiResponse.success(
    PageResponse(
        content = items,
        page = page.number,
        size = page.size,
        totalElements = page.totalElements,
        totalPages = page.totalPages
    )
)
```

---

### 4. Auditoría

✅ **Usar AuditMetadata** para entidades que requieren tracking:
```kotlin
class MyEntity(...) : AuditMetadata()
```

✅ **Soft Delete** usando campo `isActive`:
```kotlin
fun deleteStudent(id: Long) {
    val student = findById(id)
    student.isActive = false
    studentRepository.save(student)
}
```

---

## 📊 Diagrama de Dependencias

```
┌─────────────────────────────────┐
│         Application             │
│     (Módulo Principal)          │
└────────────┬────────────────────┘
             │ depends on
             ▼
┌─────────────────────────────────┐
│          Security               │
└────────────┬────────────────────┘
             │ depends on
             ▼
┌─────────────────────────────────┐
│     Students / Courses / etc    │
│     (Bounded Contexts)          │
└────────────┬────────────────────┘
             │ depends on
             ▼
┌─────────────────────────────────┐
│           Common                │
│    (Módulo Compartido)          │
│  ✓ No depende de otros módulos  │
└─────────────────────────────────┘
```

> **Importante**: El módulo Common NO debe depender de ningún otro módulo del proyecto. Es la base sobre la cual se construyen todos los demás módulos.

---

## 🔍 Testing

### Unit Test Example

```kotlin
@Test
fun `should create success response`() {
    val data = "test data"
    val response = ApiResponse.success(data, "Success")
    
    assertTrue(response.success)
    assertEquals(data, response.data)
    assertEquals("Success", response.message)
    assertNotNull(response.timestamp)
}

@Test
fun `should handle ResourceNotFoundException`() {
    assertThrows<ResourceNotFoundException> {
        throw ResourceNotFoundException("Not found")
    }
}
```

---

## 📚 Referencias

- [Domain-Driven Design](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [Spring Data JPA Auditing](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#auditing)
- [Exception Handling in Spring](https://spring.io/blog/2013/11/01/exception-handling-in-spring-mvc)
- [Bean Validation](https://beanvalidation.org/)

---

**Estado del Módulo**: ✅ Completado y estable  
**Versión**: 1.0.0  
**Última actualización**: Noviembre 4, 2025

---

## 📝 Notas Importantes

1. **Este módulo es fundamental** - Todos los demás módulos dependen de él
2. **No agregar dependencias innecesarias** - Mantener ligero y enfocado
3. **Cambios en este módulo afectan a toda la aplicación** - Proceder con cuidado
4. **Seguir principios DDD** - Aggregate Roots, Value Objects, Domain Events
5. **Mantener respuestas consistentes** - Siempre usar ApiResponse wrapper

