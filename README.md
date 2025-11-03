
## 📚 Recursos Adicionales

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Domain-Driven Design](https://martinfowler.com/tags/domain%20driven%20design.html)

## 👥 Equipo

Desarrollado para el proyecto SiGEP - Sistema de Gestión de Enseñanza Privada

## 📄 Licencia

Privado - Todos los derechos reservados

---

**Última actualización**: Octubre 2025
**Versión**: 1.0.0
# SiGEP Backend - Sistema de Gestión de Enseñanza Privada

## 📋 Descripción

Backend API REST desarrollado con Spring Boot y Kotlin para la gestión integral de un instituto privado de enseñanza de inglés.

## 🏗️ Arquitectura

### Arquitectura Modular basada en Domain-Driven Design (DDD)

El proyecto está estructurado en módulos Gradle independientes que facilitan la migración futura a microservicios:

```
sigep-backend/
├── common/                  # Módulo compartido (excepciones, DTOs base, utilidades)
├── security/                # Autenticación y autorización (JWT, roles)
├── students/                # Bounded Context: Gestión de estudiantes
├── courses/                 # Bounded Context: Gestión de cursos
├── scheduling/              # Bounded Context: Programación de horarios
├── payments/                # Bounded Context: Gestión de pagos
├── exams/                   # Bounded Context: Gestión de exámenes
├── communications/          # Bounded Context: Notificaciones y comunicaciones
├── reports/                 # Bounded Context: Generación de reportes
└── application/             # Módulo principal (orquestador)
```

### Estructura de cada módulo (DDD)

```
module/
└── src/main/kotlin/com/sigep/{module}/
    ├── domain/              # Capa de dominio
    │   ├── model/           # Entidades y agregados
    │   ├── repository/      # Interfaces de repositorios
    │   └── service/         # Lógica de dominio pura
    ├── application/         # Capa de aplicación
    │   ├── dto/             # DTOs y request/response
    │   └── service/         # Casos de uso y servicios de aplicación
    ├── infrastructure/      # Capa de infraestructura
    │   ├── config/          # Configuraciones
    │   └── persistence/     # Implementaciones de repositorios
    └── presentation/        # Capa de presentación
        └── controller/      # Controladores REST
```

## 🚀 Tecnologías

- **Lenguaje**: Kotlin 1.9.25
- **Framework**: Spring Boot 3.5.6
- **Build Tool**: Gradle (Kotlin DSL)
- **Base de Datos**: PostgreSQL
- **Cache**: Redis
- **Seguridad**: Spring Security + JWT
- **Documentación API**: OpenAPI 3 (Swagger)
- **Testing**: JUnit 5, MockK

## 📦 Módulos y Dependencias

### Common
- Spring Boot Starter Web
- Spring Boot Starter Validation
- Kotlin Logging

### Security
- Spring Security
- JWT (jjwt 0.12.3)
- Spring Data JPA
- Spring Data Redis
- PostgreSQL

### Bounded Contexts (Students, Courses, etc.)
- Spring Data JPA
- Spring Data Redis (caching)
- MapStruct (DTO mapping)
- Módulos compartidos (common, security)

### Application (Main)
- Todos los bounded contexts
- Spring Boot Actuator
- Micrometer Prometheus
- OpenAPI (Swagger UI)

## 🔧 Configuración

### Requisitos previos

- JDK 17+
- PostgreSQL 15+
- Redis 7+
- Gradle 8+ (incluido wrapper)

### Variables de entorno / Configuración

Editar `application/src/main/resources/application.properties`:

```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/sigep_db
spring.datasource.username=sigep_user
spring.datasource.password=sigep_password

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# JWT
jwt.secret=tu_secreto_super_seguro_minimo_256_bits
jwt.expiration=86400000
jwt.refresh-expiration=604800000
```

### Setup de Base de Datos

```sql
-- Crear base de datos
CREATE DATABASE sigep_db;

-- Crear usuario
CREATE USER sigep_user WITH PASSWORD 'sigep_password';

-- Otorgar permisos
GRANT ALL PRIVILEGES ON DATABASE sigep_db TO sigep_user;
```

## 🏃 Ejecución

### Desarrollo

```bash
# Compilar el proyecto
gradlew build

# Ejecutar la aplicación
gradlew :application:bootRun

# O usar el JAR generado
java -jar application/build/libs/sigep-backend.jar
```

### Testing

```bash
# Ejecutar todos los tests
gradlew test

# Ejecutar tests de un módulo específico
gradlew :students:test
```

## 📡 API Endpoints

### Base URL
```
http://localhost:8080/api/v1
```

### Autenticación

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/auth/login` | Iniciar sesión |
| POST | `/auth/register` | Registrar usuario |
| POST | `/auth/refresh-token` | Renovar token |
| POST | `/auth/logout` | Cerrar sesión |

### Estudiantes

| Método | Endpoint | Descripción | Roles |
|--------|----------|-------------|-------|
| GET | `/students` | Listar estudiantes | ADMIN, TEACHER |
| GET | `/students/{id}` | Obtener estudiante | ADMIN, TEACHER, GUARDIAN |
| POST | `/students` | Crear estudiante | ADMIN |
| PUT | `/students/{id}` | Actualizar estudiante | ADMIN |
| DELETE | `/students/{id}` | Eliminar estudiante | ADMIN |
| GET | `/students/search?query={q}` | Buscar estudiantes | ADMIN, TEACHER |
| GET | `/students/guardian/{id}` | Estudiantes por tutor | ADMIN, TEACHER, GUARDIAN |

### Cursos, Pagos, Exámenes, etc.

Ver documentación completa en: `http://localhost:8080/swagger-ui.html`

### 🆕 Análisis de Rendimiento de Docentes (Exams Module)

| Método | Endpoint | Descripción | Roles |
|--------|----------|-------------|-------|
| GET | `/teachers/{teacherId}/performance` | Obtener métricas de rendimiento del docente | ADMIN |
| GET | `/teachers/{teacherId}/exams` | Listar exámenes del docente | ADMIN, TEACHER |
| POST | `/teachers/compare` | Comparar rendimiento entre docentes | ADMIN |

**Métricas incluidas:**
- Total de exámenes creados, publicados y cerrados
- Total de estudiantes evaluados
- Promedio general de calificaciones
- Tasa de aprobación
- Estadísticas por curso
- Distribución de exámenes por estado

## 🔐 Roles y Permisos

- **ADMIN**: Acceso total al sistema
- **TEACHER**: Acceso a estudiantes, cursos, exámenes
- **GUARDIAN**: Acceso limitado a información de sus estudiantes

## 🔗 Integración entre Módulos

El sistema implementa relaciones entre módulos siguiendo principios de DDD:

### Relaciones Principales

```
┌─────────────┐
│   Security  │ ◄──────────────────────────┐
└──────┬──────┘                            │
       │ Autenticación/Autorización        │
       │                                   │
┌──────▼──────┐      ┌──────────────┐     │
│  Students   │◄────►│   Courses    │     │
└──────┬──────┘      └──────┬───────┘     │
       │                    │              │
       │                    │              │
┌──────▼──────┐      ┌──────▼───────┐     │
│    Exams    │◄────►│    Staff     │─────┘
└──────┬──────┘      └──────────────┘
       │
┌──────▼──────┐      ┌──────────────┐
│  Payments   │      │ Scheduling   │
└─────────────┘      └──────────────┘
```

### Casos de Uso de Integración

#### 1. **Exams ↔ Students ↔ Courses**
- Un examen pertenece a un curso
- Los estudiantes se inscriben en cursos
- Las calificaciones se registran por estudiante y examen

#### 2. **Exams ↔ Staff (Docentes)**
- Los exámenes tienen docentes asignados (`assignedTeachers`)
- Permite análisis de rendimiento del docente
- Métricas: exámenes creados, resultados obtenidos, tasas de aprobación

#### 3. **Courses ↔ Students ↔ Scheduling**
- Los cursos tienen sesiones programadas
- Los estudiantes asisten a las sesiones
- Control de asistencia y horarios

#### 4. **Students ↔ Payments**
- Gestión de cuotas mensuales
- Historial de pagos por estudiante

### Consultas Cross-Module

**Ejemplo: Ver rendimiento integral de un estudiante**
```kotlin
// Obtener estudiante (Students module)
val student = studentService.getStudent(studentId)

// Obtener cursos inscritos (Courses module)
val enrollments = enrollmentService.getStudentEnrollments(studentId)

// Obtener exámenes y calificaciones (Exams module)
val examHistory = examSubmissionService.getStudentHistory(studentId, courseId)

// Obtener estado de pagos (Payments module - futuro)
val paymentStatus = paymentService.getStudentPaymentStatus(studentId)
```

**Ejemplo: Dashboard de docente**
```kotlin
// Obtener información del docente (Staff module)
val teacher = teachingStaffService.getById(teacherId)

// Obtener cursos asignados (Courses module)
val assignedCourses = courseService.getTeacherCourses(teacherId)

// Obtener estadísticas de rendimiento (Exams module)
val performance = teacherPerformanceService.getTeacherPerformance(teacherId)

// Combinar datos para dashboard
val dashboard = TeacherDashboard(teacher, assignedCourses, performance)
```

## 🔄 Migración a Microservicios

### Actuator Endpoints

- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`

## 🔄 Migración a Microservicios

La arquitectura modular facilita la migración:

1. **Fase actual**: Comunicación interna entre módulos mediante llamadas a métodos
2. **Fase futura**: 
   - Cada módulo se convierte en un microservicio independiente
   - Comunicación mediante HTTP REST + Apache Kafka para eventos
   - Service Discovery con Eureka
   - API Gateway con Spring Cloud Gateway

### Preparación para microservicios

Cada módulo ya tiene:
- ✅ Separación clara de responsabilidades (Bounded Contexts)
- ✅ Dependencias bien definidas
- ✅ Configuración independiente
- ✅ Base de datos compartida (se migrará a bases independientes)

## 📝 Convenciones de Código

- **Packages**: `com.sigep.{module}.{layer}`
- **Naming**: 
  - Entities: Sustantivos en singular (Student, Course)
  - Services: {Entity}Service
  - Controllers: {Entity}Controller
  - DTOs: {Entity}Dto, Create{Entity}Request, Update{Entity}Request
- **Git**: Conventional Commits (feat:, fix:, docs:, etc.)

## 🧪 Testing

```kotlin
// Ejemplo de test
@SpringBootTest
class StudentServiceTest {
    @MockkBean
    private lateinit var studentRepository: StudentRepository
    
    @Autowired
    private lateinit var studentService: StudentService
    
    @Test
    fun `should create student successfully`() {
        // Given
        val request = CreateStudentRequest(...)
        
        // When
        val result = studentService.createStudent(request)
        
        // Then
        assertNotNull(result.id)
        assertEquals(request.email, result.email)
    }
}
```

