# 🎯 Diagrama de Arquitectura SiGEP Backend

## Estructura de Módulos

```
┌─────────────────────────────────────────────────────────────────┐
│                     APPLICATION MODULE                          │
│                   (Punto de entrada principal)                  │
│                  http://localhost:8080/api/v1                   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ├─────────────────────┐
                              ▼                     ▼
┌─────────────────────────────────────┐  ┌─────────────────────┐
│          COMMON MODULE              │  │   SECURITY MODULE   │
├─────────────────────────────────────┤  ├─────────────────────┤
│ • BaseEntity                        │  │ • JWT Authentication│
│ • AggregateRoot                     │  │ • User Management   │
│ • ApiResponse<T>                    │  │ • Role-based Auth   │
│ • PageResponse<T>                   │  │ • Password Encoding │
│ • GlobalExceptionHandler            │  │                     │
│ • Custom Exceptions                 │  │ Roles:              │
└─────────────────────────────────────┘  │ - ADMIN             │
                                         │ - TEACHER           │
                                         │ - GUARDIAN          │
                                         └─────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    BOUNDED CONTEXTS (DDD)                       │
└─────────────────────────────────────────────────────────────────┘

┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   STUDENTS   │  │   COURSES    │  │  SCHEDULING  │  │   PAYMENTS   │
├──────────────┤  ├──────────────┤  ├──────────────┤  ├──────────────┤
│ • Student    │  │ • Course     │  │ • Schedule   │  │ • Payment    │
│ • Guardian   │  │ • Enrollment │  │ • Calendar   │  │ • Invoice    │
│   relation   │  │ • Schedule   │  │ • Conflicts  │  │ • Receipt    │
│              │  │              │  │   detection  │  │              │
│ GET /students│  │ GET /courses │  │ GET /schedule│  │ GET /payments│
│ POST /students  │ POST /courses│  │ POST /schedule  │ POST /payments
│ PUT /students│  │ PUT /courses │  │ PUT /schedule│  │ PUT /payments│
│ DELETE ...   │  │ DELETE ...   │  │ DELETE ...   │  │ DELETE ...   │
└──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘

┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│    EXAMS     │  │COMMUNICATIONS│  │   REPORTS    │
├──────────────┤  ├──────────────┤  ├──────────────┤
│ • Exam       │  │ • Notification│ │ • Academic   │
│ • Result     │  │ • Email      │  │ • Financial  │
│ • Score      │  │ • WebSocket  │  │ • Attendance │
│ • Type       │  │ • Push       │  │ • Excel/PDF  │
│              │  │              │  │              │
│ GET /exams   │  │ GET /notify  │  │ GET /reports │
│ POST /exams  │  │ POST /notify │  │ POST /reports│
│ PUT /exams   │  │ PUT /notify  │  │   /generate  │
│ DELETE ...   │  │ DELETE ...   │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
```

## Capas DDD por Módulo

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                       │
│  • REST Controllers (@RestController)                       │
│  • Request/Response DTOs                                    │
│  • OpenAPI Annotations                                      │
│  • Security annotations (@PreAuthorize)                     │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   APPLICATION LAYER                         │
│  • Use Cases / Application Services                         │
│  • DTOs (Data Transfer Objects)                             │
│  • Validation logic                                         │
│  • Orchestration between domain services                    │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                     DOMAIN LAYER                            │
│  • Entities (JPA)                                           │
│  • Aggregate Roots                                          │
│  • Value Objects                                            │
│  • Repository Interfaces                                    │
│  • Domain Services (pure business logic)                    │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                 INFRASTRUCTURE LAYER                        │
│  • Repository Implementations (Spring Data JPA)             │
│  • Configuration classes                                    │
│  • External integrations                                    │
│  • Database mappings                                        │
└─────────────────────────────────────────────────────────────┘
```

## Stack Tecnológico

```
┌─────────────────────────────────────────────────────────────┐
│                         CLIENT                              │
│              Angular 20 Frontend App                        │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ HTTP/REST + JWT
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                    API GATEWAY LAYER                        │
│  • Spring Security (JWT Filter)                             │
│  • CORS Configuration                                       │
│  • Rate Limiting (Future)                                   │
└─────────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                   APPLICATION LAYER                         │
│  Spring Boot 3.5.6 + Kotlin 1.9.25                          │
│  • REST Controllers                                         │
│  • Business Services                                        │
│  • DTOs & Validation                                        │
└─────────────────────────────────────────────────────────────┘
                          │
                ┌─────────┴─────────┐
                ▼                   ▼
┌──────────────────────┐  ┌──────────────────────┐
│   CACHE LAYER        │  │   DATA LAYER         │
│   Redis 7+           │  │   PostgreSQL 15+     │
│   • Session cache    │  │   • JPA Entities     │
│   • Data cache       │  │   • Transactions     │
│   • TTL: 10min       │  │   • Indexes          │
└──────────────────────┘  └──────────────────────┘
```

## Flujo de Request Típico

```
1. CLIENT
   │
   └──> HTTP Request + JWT Token
        │
        ▼
2. SECURITY FILTER
   │
   ├──> Validate JWT Token
   ├──> Extract user info & roles
   └──> Set SecurityContext
        │
        ▼
3. CONTROLLER (Presentation)
   │
   ├──> @PreAuthorize check
   ├──> Validate request DTO
   └──> Call Application Service
        │
        ▼
4. APPLICATION SERVICE
   │
   ├──> Check cache (Redis)
   ├──> Business logic
   └──> Call Repository
        │
        ▼
5. REPOSITORY (Infrastructure)
   │
   ├──> JPA query
   └──> Database query
        │
        ▼
6. DATABASE (PostgreSQL)
   │
   └──> Return data
        │
        ▼
7. RESPONSE FLOW
   │
   ├──> Map Entity to DTO
   ├──> Wrap in ApiResponse<T>
   ├──> Cache result (Redis)
   └──> Return JSON to client
```

## Comunicación entre Módulos

### FASE 1 - ACTUAL (Monolito Modular)
```
PaymentsService ──────> StudentsService
      │                       │
      └──> Internal Method Call (Direct)
```

### FASE 2 - FUTURA (Microservicios)
```
Payments-Service ──HTTP──> API Gateway ──> Students-Service
      │                                           │
      └─────────> Kafka Event Bus <───────────────┘
                  (Async Events)
```

## Dependencias entre Módulos

```
application
    │
    ├──> common (shared utilities)
    ├──> security (auth & authorization)
    │       └──> common
    │
    ├──> students
    │       ├──> common
    │       └──> security
    │
    ├──> courses
    │       ├──> common
    │       ├──> security
    │       └──> students (cross-reference)
    │
    ├──> payments
    │       ├──> common
    │       ├──> security
    │       └──> students (cross-reference)
    │
    ├──> exams
    │       ├──> common
    │       ├──> security
    │       ├──> courses
    │       └──> students
    │
    ├──> communications
    │       ├──> common
    │       ├──> security
    │       └──> students
    │
    ├──> scheduling
    │       ├──> common
    │       ├──> security
    │       ├──> courses
    │       └──> students
    │
    └──> reports
            ├──> common
            ├──> security
            ├──> students
            ├──> courses
            ├──> payments
            └──> exams (aggregates all data)
```

## Modelo de Base de Datos (Simplificado)

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   users     │         │  students   │         │   courses   │
├─────────────┤         ├─────────────┤         ├─────────────┤
│ id (PK)     │         │ id (PK)     │         │ id (PK)     │
│ username    │         │ first_name  │         │ name        │
│ email       │    ┌────│ guardian_id │         │ teacher_id──┤
│ password    │    │    │ email       │         │ level       │
│ role        │◄───┘    │ status      │         │ status      │
└─────────────┘         └─────────────┘         └─────────────┘
                              │                        │
                              │                        │
                              └────────────────────────┘
                                         │
                                         ▼
                              ┌─────────────────────┐
                              │ course_enrollments  │
                              ├─────────────────────┤
                              │ course_id (FK)      │
                              │ student_id (FK)     │
                              └─────────────────────┘

┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│  payments   │         │    exams    │         │notifications│
├─────────────┤         ├─────────────┤         ├─────────────┤
│ id (PK)     │         │ id (PK)     │         │ id (PK)     │
│ student_id  │         │ course_id   │         │ recipient_id│
│ amount      │         │ name        │         │ type        │
│ status      │         │ exam_date   │         │ message     │
│ due_date    │         │ status      │         │ status      │
└─────────────┘         └─────────────┘         └─────────────┘
                              │
                              ▼
                        ┌─────────────┐
                        │exam_results │
                        ├─────────────┤
                        │ exam_id (FK)│
                        │ student_id  │
                        │ score       │
                        │ passed      │
                        └─────────────┘
```

## Seguridad y Roles

```
┌─────────────────────────────────────────────────────────────┐
│                         ADMIN                               │
│  Full access to all resources                               │
│  • Users CRUD                                               │
│  • Students CRUD                                            │
│  • Courses CRUD                                             │
│  • Payments CRUD                                            │
│  • Exams CRUD                                               │
│  • Reports generation                                       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                        TEACHER                              │
│  Educational resources access                               │
│  • Students READ                                            │
│  • Courses READ/UPDATE (own courses)                        │
│  • Exams CRUD (own courses)                                 │
│  • Reports READ                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                       GUARDIAN                              │
│  Limited access to own students                             │
│  • Students READ (own children)                             │
│  • Courses READ (enrolled courses)                          │
│  • Payments READ (own payments)                             │
│  • Exams READ (own students results)                        │
│  • Notifications READ                                       │
└─────────────────────────────────────────────────────────────┘
```

---
**Arquitectura preparada para migración a microservicios**
**Modular • Escalable • Mantenible**

