# SiGEP Backend - Arquitectura de Módulos

## Estructura General

```
sigep-backend/
│
├── build.gradle.kts                 # Configuración raíz multi-módulo
├── settings.gradle.kts              # Configuración de módulos
├── gradlew / gradlew.bat           # Gradle wrapper
├── docker-compose.yml              # PostgreSQL + Redis
├── README.md                       # Documentación principal
│
├── common/                         # Módulo compartido
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/sigep/common/
│       ├── domain/
│       │   ├── BaseEntity.kt
│       │   ├── AggregateRoot.kt
│       │   └── ValueObject.kt
│       ├── application/
│       │   ├── dto/
│       │   │   ├── ApiResponse.kt
│       │   │   ├── PageRequest.kt
│       │   │   └── PageResponse.kt
│       │   └── exception/
│       │       └── Exceptions.kt
│       └── infrastructure/
│           └── config/
│               └── GlobalExceptionHandler.kt
│
├── security/                       # Módulo de seguridad
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/sigep/security/
│       ├── domain/
│       │   ├── model/
│       │   │   └── User.kt
│       │   └── repository/
│       │       └── UserRepository.kt
│       ├── application/
│       │   ├── dto/
│       │   │   └── AuthDtos.kt
│       │   └── service/
│       │       └── AuthService.kt
│       ├── infrastructure/
│       │   ├── config/
│       │   │   └── SecurityConfig.kt
│       │   └── security/
│       │       ├── JwtTokenProvider.kt
│       │       └── JwtAuthenticationFilter.kt
│       └── presentation/
│           └── controller/
│               └── AuthController.kt
│
├── students/                       # Bounded Context: Estudiantes
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/sigep/students/
│       ├── domain/
│       │   ├── model/
│       │   │   └── Student.kt
│       │   └── repository/
│       │       └── StudentRepository.kt
│       ├── application/
│       │   ├── dto/
│       │   │   └── StudentDtos.kt
│       │   └── service/
│       │       └── StudentService.kt
│       └── presentation/
│           └── controller/
│               └── StudentController.kt
│
├── courses/                        # Bounded Context: Cursos
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/sigep/courses/
│       ├── domain/
│       │   ├── model/
│       │   │   └── Course.kt
│       │   └── repository/
│       │       └── CourseRepository.kt
│       └── [application, presentation layers...]
│
├── scheduling/                     # Bounded Context: Horarios
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/sigep/scheduling/
│       └── [DDD layers...]
│
├── payments/                       # Bounded Context: Pagos
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/sigep/payments/
│       ├── domain/
│       │   └── model/
│       │       └── Payment.kt
│       └── [application, presentation layers...]
│
├── exams/                          # Bounded Context: Exámenes
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/sigep/exams/
│       ├── domain/
│       │   └── model/
│       │       └── Exam.kt
│       └── [application, presentation layers...]
│
├── communications/                 # Bounded Context: Notificaciones
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/sigep/communications/
│       ├── domain/
│       │   └── model/
│       │       └── Notification.kt
│       └── [application, presentation layers...]
│
├── reports/                        # Bounded Context: Reportes
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/sigep/reports/
│       └── [DDD layers...]
│
└── application/                    # Módulo principal (orquestador)
    ├── build.gradle.kts
    └── src/main/
        ├── kotlin/com/sigep/application/
        │   ├── SigepApplication.kt
        │   └── config/
        │       ├── RedisConfig.kt
        │       └── OpenApiConfig.kt
        └── resources/
            └── application.properties
```

## Dependencias entre Módulos

```
application
    ↓ (depende de todos)
    ├── common
    ├── security → common
    ├── students → common, security
    ├── courses → common, security, students
    ├── scheduling → common, security, courses, students
    ├── payments → common, security, students
    ├── exams → common, security, courses, students
    ├── communications → common, security, students
    └── reports → common, security, students, courses, payments, exams
```

## Migración a Microservicios

### Fase 1 - Actual (Monolito Modular)
- Comunicación: Llamadas a métodos internos
- Base de datos: PostgreSQL compartida
- Cache: Redis compartido

### Fase 2 - Microservicios
Cada módulo se convierte en servicio independiente:

```
students-service:8081
courses-service:8082
payments-service:8083
exams-service:8084
...

API Gateway (Spring Cloud Gateway)
    ↓
Service Discovery (Eureka)
    ↓
Microservicios individuales
    ↓
Event Bus (Apache Kafka)
```

## Comandos Útiles

```bash
# Compilar todo el proyecto
gradlew build

# Compilar módulo específico
gradlew :students:build

# Ejecutar aplicación
gradlew :application:bootRun

# Ejecutar tests
gradlew test

# Limpiar build
gradlew clean

# Ver dependencias
gradlew :students:dependencies
```

## Endpoints Principales

| Módulo | Base Path | Puerto |
|--------|-----------|--------|
| Auth | `/api/v1/auth` | 8080 |
| Students | `/api/v1/students` | 8080 |
| Courses | `/api/v1/courses` | 8080 |
| Payments | `/api/v1/payments` | 8080 |
| Exams | `/api/v1/exams` | 8080 |
| Communications | `/api/v1/notifications` | 8080 |
| Reports | `/api/v1/reports` | 8080 |

## Tecnologías por Capa

### Domain Layer
- Entidades JPA
- Enums
- Value Objects
- Repository Interfaces

### Application Layer
- DTOs (Data Transfer Objects)
- Service Classes (Use Cases)
- Validation
- Mappers (MapStruct)

### Infrastructure Layer
- Repository Implementations
- Configuration Classes
- Security Configuration
- Cache Configuration
- Database Configuration

### Presentation Layer
- REST Controllers
- Request/Response DTOs
- OpenAPI Annotations
- Exception Handlers

