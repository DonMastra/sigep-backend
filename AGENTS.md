# AGENTS.md - SiGEP Backend

## Required Skill Routing

Repository skills live in `.agents/skills` and allow implicit invocation. Before analyzing, implementing, reviewing, or refactoring non-trivial code, use every applicable skill:

- Use `$sigep-spring-backend` for Spring Boot, Kotlin, controllers, services, security, validation, transactions, integrations, scheduled jobs, error handling, architecture, and backend tests.
- Also use `$sigep-rest-api` whenever work touches controllers, endpoints, DTOs, OpenAPI/API documentation, pagination, filtering, validation/error semantics, auth semantics, mock contracts, or frontend/backend coordination.
- Also use `$sigep-database-design` whenever work touches JPA entities, repositories, migrations, PostgreSQL/H2 behavior, constraints, indexes, seed data, audit fields, idempotency records, or persistence queries.
- Use `$sigep-angular-frontend` when a task from this workspace also changes or diagnoses the sibling `sigep` Angular repository.

Read each selected `SKILL.md` completely before acting. Do not load unrelated skills merely because they are available.

## Project Overview

SiGEP Backend is a modular monolith REST API for a private English-language institute. It supports the SiGEP Angular web application with authentication, academic management, staff administration, exams, scheduling, and planned billing/payment capabilities.

Stack:

- Kotlin 1.9.25
- Java 17
- Spring Boot 3.5.6
- Spring Web, Security, Data JPA, Validation, Redis, Cache, Actuator
- PostgreSQL 15
- Redis 7
- JWT with `jjwt` 0.12.3 dependencies
- Bucket4j rate limiting
- Springdoc OpenAPI/Swagger
- Gradle Kotlin DSL
- JUnit 5 and MockK

This repository currently has local uncommitted work on `feature008-courses-flow`. Do not revert unrelated changes.

## Module Architecture

```text
common/          Shared DTOs, exceptions, DDD base classes, provider interfaces
security/        JWT auth, Spring Security, roles, registration approval, admin users
students/        Student CRUD, guardian relationships, profile photos, payment-status placeholder
courses/         Courses, enrollments, course sessions, attendance, materials, certificates
staff/           Teaching staff, non-teaching staff, staff attendance
exams/           Exams, submissions, grades, grade history, teacher performance analytics
scheduling/      Classrooms, time slots, reservations and assignment workflow
payments/        Billing/payments planned; entity exists, API not complete
communications/  Notifications planned; entity exists, delivery not complete
reports/         Advanced reports planned
application/     Entry point, OpenAPI config, Redis/cache config, module import
```

Each implemented business module follows this DDD-oriented layout:

```text
domain/model/            JPA entities and enums
domain/repository/       Spring Data repository interfaces
application/dto/         Request and response DTOs
application/service/     Use-case services and application logic
infrastructure/config/   Component scan, repository scan and entity scan
presentation/controller/ REST controllers
```

Package convention: `com.sigep.{module}.{layer}`.

## Current Functional Areas

- Auth: login, public registration, refresh token, logout, authenticated profile.
- Admin security: registration approval/rejection and admin user catalog.
- Students: listing, search, detail, creation by admin, self-registration by guardian, update, delete, photo upload/download, guardian lookup, payment status placeholder.
- Courses: listing, search, teacher filter, create/update/delete, publish/unpublish, activate/deactivate, enroll student, filter, public published catalog, statistics.
- Enrollments: lookup, student/course lists, history, update, delete, bulk creation.
- Course sessions: CRUD, recurring session generation, exceptions, conflict check, attendance summary, calendar.
- Attendance: course/student/enrollment attendance, bulk records, date reports, statistics.
- Materials: course material CRUD, visibility, type filter, reorder, statistics.
- Certificates: issue, update, revoke, verify, statistics, process expired certificates.
- Staff: teaching and non-teaching staff CRUD/search, teacher ID resolution, staff attendance.
- Exams: exam lifecycle, submissions, grades, scanned file path attachment, grade history, course/exam statistics, teacher performance.
- Scheduling: classrooms, schedule slots, reservations, available reservation search, assignment/unassignment.
- Admin cache: clear all Redis cache.

## API Conventions

- All REST endpoints live under `/api/v1`.
- Most successful responses use `common.application.dto.ApiResponse<T>`.
- Pagination uses `common.application.dto.PageResponse<T>` with `content`, `page`, `size`, `totalElements`, `totalPages`.
- Many controllers accept `page` plus `limit`; some newer/legacy endpoints accept `size`. Several course/scheduling endpoints accept both `limit` and `size`, preferring `limit`.
- POST creation endpoints return `201 CREATED`.
- Most DELETE endpoints return `200 OK` with `ApiResponse`; `exams` delete currently returns `204 NO_CONTENT`.
- Some `exams` endpoints currently return bare DTOs or `PageResponse<T>` instead of `ApiResponse<T>`. Document this as real behavior until code is normalized.
- Swagger UI runs at `/swagger-ui.html`; OpenAPI JSON at `/v3/api-docs`.

## Security Rules

Roles:

- `ADMIN`
- `TEACHER`
- `GUARDIAN`

Prefer the custom security annotations where already used:

- `@RequireAdmin`
- `@RequireAdminOrTeacher`
- `@RequireGuardian`
- `@RequireStaffOrGuardian`

Otherwise follow existing `@PreAuthorize` patterns.

Registration workflow:

- Public registration accepts `TEACHER` or `GUARDIAN`.
- New accounts start as `PENDING_APPROVAL`.
- Admin approval activates the account.
- Rejected or pending accounts cannot log in.

The JWT filter stores `userId` and `userRole` on the `HttpServletRequest`; services/controllers use those attributes for actor-aware operations.

## Cross-Module Communication

Target pattern: modules should not reach across domain boundaries directly for runtime data. Define provider interfaces in `common`, implement them in the owning module, and inject the interface into consumers.

Known provider examples:

- `EnrollmentServiceProvider`: implemented by `courses`, consumed by `students`.
- `TeacherInfoProvider`: implemented by `staff`.
- `SchedulingTargetValidationProvider`: validates reservation targets through the owning domain.
- `ReservationInfoProvider` and `ReservationAssignmentProvider`: expose scheduling reservation state to other domains.

Current-state exception: `staff` and `exams` still declare direct Gradle dependencies on `courses` and `students`. Do not expand this coupling unless explicitly required; prefer adding provider interfaces in `common`.

## Entity and Persistence Patterns

- Shared base classes live in `common/src/main/kotlin/com/sigep/common/domain`.
- Prefer `BaseEntity` for common `id`, `createdAt`, `updatedAt`.
- Most modules use `Long` primary keys.
- `exams` uses `UUID` primary keys for exams/submissions because of legacy migration decisions.
- Entities are Kotlin `data class`es in current code; preserve local patterns when editing.
- Development profile uses Hibernate `ddl-auto: update`; production should move toward validation/versioned migrations.

## Exceptions and Errors

Use exceptions from `common.application.exception`:

- `ResourceNotFoundException`
- `DuplicateResourceException`
- `BusinessException`
- `ValidationException`
- `UnauthorizedException`
- `ForbiddenException`

Global handling lives in `common.infrastructure.config.GlobalExceptionHandler`. The newer error contract uses `ErrorResponse` with `status`, `code`, `message`, optional `field`, optional `details`, `path`, and `timestamp`.

## Configuration

Primary config files:

- `application/src/main/resources/application.yml`
- `application/src/main/resources/application-dev.yml`
- `application/src/main/resources/application-prod.yml`

Important defaults:

- Server port: `8080`
- PostgreSQL: `localhost:5432/sigep_db`
- Redis: `localhost:6379`
- Business/Jackson timezone: `America/Argentina/Buenos_Aires`
- CORS origins: `http://localhost:4200`, `https://sigep.edu.mx`
- Rate limit: 100 requests/minute/client
- Redis cache TTL: 10 minutes

## Developer Commands

Windows:

```powershell
docker-compose up -d
gradlew.bat clean build
gradlew.bat :application:bootRun --args="--spring.profiles.active=dev"
gradlew.bat :students:test
gradlew.bat :exams:test
gradlew.bat :application:bootJar
```

Linux/macOS:

```bash
docker-compose up -d
./gradlew clean build
./gradlew :application:bootRun --args='--spring.profiles.active=dev'
```

Dev users:

| username | password | role |
|---|---|---|
| `admin` | `password123` | `ADMIN` |
| `teacher` | `password123` | `TEACHER` |
| `guardian` | `password123` | `GUARDIAN` |

## In-Development Modules

Do not build features that depend on `payments`, `communications`, or `reports` as if they were complete.

- Billing/payments should expose student debt/payment state, invoices/receipts, payment methods and payment status once designed.
- Communications should deliver notifications for approval/rejection and academic/operational events once transport is defined.
- Reports should aggregate academic, financial and operational data once source contracts stabilize.

Until then, use explicit TODO comments and define provider interfaces/contracts in `common` before integrating from active modules.

## Documentation Expectations

- Keep `README.md` as the entry guide.
- Keep `API_CONTRACT.md` aligned with controller routes and frontend needs.
- Keep `AGENT_CONTEXT.md` as the high-detail context file for agents.
- Keep `.agents/skills` aligned with recurring Spring, REST, database, and cross-repository Angular practices.
- When code and docs disagree, inspect controllers/DTOs first and document real behavior plus known risk.
- Avoid mojibake and mixed encodings. Use UTF-8 Markdown.

## Key Reference Files

| Purpose | File |
|---|---|
| Entry point | `application/src/main/kotlin/com/sigep/application/SigepApplication.kt` |
| App config | `application/src/main/resources/application.yml` |
| Redis/cache config | `application/src/main/kotlin/com/sigep/application/config/RedisConfig.kt` |
| OpenAPI config | `application/src/main/kotlin/com/sigep/application/config/OpenApiConfig.kt` |
| API wrapper | `common/src/main/kotlin/com/sigep/common/application/dto/ApiResponse.kt` |
| Pagination wrapper | `common/src/main/kotlin/com/sigep/common/application/dto/PageResponse.kt` |
| Exceptions | `common/src/main/kotlin/com/sigep/common/application/exception/Exceptions.kt` |
| Global error handler | `common/src/main/kotlin/com/sigep/common/infrastructure/config/GlobalExceptionHandler.kt` |
| Auth controller | `security/src/main/kotlin/com/sigep/security/presentation/controller/AuthController.kt` |
| Course controller pattern | `courses/src/main/kotlin/com/sigep/courses/presentation/controller/CourseController.kt` |
| Scheduling controllers | `scheduling/src/main/kotlin/com/sigep/scheduling/presentation/controller/` |
| Docker infra | `docker-compose.yml` |
