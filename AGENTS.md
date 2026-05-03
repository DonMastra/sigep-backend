# AGENTS.md – SiGEP Backend

## Project Overview

SiGEP is a **modular monolith** REST API for a private English-language institute, built with **Kotlin 1.9.25 + Spring Boot 3.5.6**, structured as independent Gradle submodules designed for future microservice extraction.

**Stack:** Kotlin · Spring Boot (Web, Security, Data JPA, Data Redis, Actuator) · PostgreSQL 15 · Redis 7 · JWT (jjwt 0.12.3) · Bucket4j · OpenAPI/Swagger · MockK (tests)

---

## Module Architecture

```
common/        # Shared DTOs, exceptions, DDD base classes — zero dependencies on other modules
security/      # JWT auth, Spring Security config, UserRole enum, rate limiting → depends on: common
students/      # Student CRUD, guardian relationships              → depends on: common, security
courses/       # Courses, enrollments, sessions, attendance, certs → depends on: common, security, students
staff/         # Teaching & non-teaching staff management          → depends on: common, security
exams/         # Exams, grades, teacher performance analytics      → depends on: common, security, courses, students
scheduling/    # Session scheduling (partial)                      → depends on: common, security, courses, students
payments/      # Payments (in development)                        → depends on: common, security, students
communications/# Notifications (in development)                   → depends on: common, security, students
reports/       # Reporting (in development)                        → depends on: all
application/   # Entry point, OpenAPI config, Redis config — imports all modules
```

Each module follows strict DDD layering:
- `domain/model/` – JPA entities and enums
- `domain/repository/` – Spring Data JPA repository interfaces
- `application/dto/` – request/response DTOs
- `application/service/` – use-case services
- `infrastructure/config/` – `@Configuration` with `@ComponentScan`, `@EnableJpaRepositories`, `@EntityScan` per module
- `presentation/controller/` – REST controllers

**Package convention:** `com.sigep.{module}.{layer}`

---

## Cross-Module Communication Pattern

Modules **never import each other directly** (to enable future microservice extraction). Instead, cross-boundary data flows through interfaces declared in `common/`.

**Example – StudentService consuming enrollment data from the Courses module:**
- `common` declares: `EnrollmentServiceProvider` interface
- `courses` implements it: `EnrollmentServiceProviderImpl` (injected via Spring)
- `students` injects only the interface: no direct dependency on `courses`

When adding cross-module calls, **always define the interface in `common/`** and implement it in the owning module.

---

## Key Developer Commands

```bash
# Start infrastructure (PostgreSQL + Redis + pgAdmin + Redis Commander)
docker-compose up -d

# Build entire project
./gradlew clean build          # Linux/Mac
gradlew clean build            # Windows

# Run application (hot reload, dev profile creates test users)
gradlew :application:bootRun --args='--spring.profiles.active=dev'

# Build & run specific module
gradlew :students:build
gradlew :exams:test

# Package JAR
gradlew :application:bootJar
java -jar application/build/libs/sigep-backend.jar
```

**Dev test users** (created automatically with `--spring.profiles.active=dev`):

| username | password    | role     |
|----------|-------------|----------|
| admin    | password123 | ADMIN    |
| teacher  | password123 | TEACHER  |
| guardian | password123 | GUARDIAN |

---

## API Conventions

- All endpoints are prefixed `/api/v1/`
- Every response is wrapped in `ApiResponse<T>` from `common/` – use `ApiResponse.success(data)` or `ApiResponse.successNoContent(message)`
- Pagination uses `PageResponse<T>` with params `page`, `limit`, `sort`, `order`
- `@PreAuthorize` is used on every controller method; roles are `ROLE_ADMIN`, `ROLE_TEACHER`, `ROLE_GUARDIAN`
- HTTP `201 CREATED` for POST that creates a resource; `200 OK` for everything else including DELETE

---

## Entity & Exception Patterns

- All entities extend `AggregateRoot` (marker interface) or `BaseEntity` (abstract with `id`, `createdAt`, `updatedAt`)
- Entities use Kotlin `data class` with `copy()` for immutable updates (see `StudentService.updateStudent`)
- **Most modules use `Long` as PK**; `exams` module uses `UUID` (legacy migration reason — see `troubleshooting-guides/`)
- Throw from `common.application.exception`: `ResourceNotFoundException`, `DuplicateResourceException`, `BusinessException`, `ValidationException`, `UnauthorizedException`, `ForbiddenException`
- Exceptions are mapped globally in `common/.../GlobalExceptionHandler`

---

## Configuration

- Main config: `application/src/main/resources/application.yml`
- Profile overrides: `application-dev.yml`, `application-prod.yml`
- `ddl-auto: update` in dev; switch to `validate` (or Flyway) for production
- Redis cache TTL: 10 minutes; cache names used: `students`, `students_detail`
- CORS allowed origins: `http://localhost:4200` (Angular dev) + `https://sigep.edu.mx`
- Rate limit: 100 req/min per client (Bucket4j)

---

## In-Development Modules

`payments/`, `communications/`, `reports/` have stub entities and empty build files. Do not implement features that depend on these until their interfaces are defined in `common/`. Use `TODO:` comments with integration notes (see `StudentService.getStudentPaymentStatus` as the pattern).

---

## Key Reference Files

| Purpose | File |
|---|---|
| DDD base classes | `common/src/main/kotlin/com/sigep/common/domain/` |
| Shared exceptions | `common/.../application/exception/Exceptions.kt` |
| Cross-module interface pattern | `common/.../application/service/EnrollmentServiceProvider.kt` |
| Module config pattern | `exams/.../infrastructure/config/ExamsModuleConfig.kt` |
| Controller pattern | `courses/.../presentation/controller/CourseController.kt` |
| Service with caching | `students/.../application/service/StudentService.kt` |
| UUID entity example | `exams/.../domain/model/ExamSubmission.kt` |
| Infrastructure setup | `docker-compose.yml` |
| App config | `application/src/main/resources/application.yml` |

