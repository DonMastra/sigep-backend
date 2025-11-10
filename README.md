
## 🚀 Roadmap

### ✅ Completado (v1.0)
- [x] Arquitectura modular con DDD
- [x] Módulo de seguridad con JWT
- [x] Gestión de estudiantes
- [x] Gestión de cursos con materiales
- [x] Gestión de personal (docente y no docente)
- [x] Sistema de exámenes y calificaciones
- [x] Sesiones y asistencia
- [x] Certificados de curso
- [x] Documentación completa (API Contract, Security)
- [x] Swagger UI

### 🚧 En Desarrollo (v1.1)
- [ ] Módulo de pagos completo
- [ ] Módulo de comunicaciones/notificaciones
- [ ] Módulo de reportes avanzados
- [ ] Tests unitarios e integración
- [ ] Migraciones de base de datos (Flyway)

### 📋 Planificado (v2.0)
- [ ] Migración a microservicios
- [ ] Event sourcing con Kafka
- [ ] API Gateway
- [ ] Service discovery
- [ ] Distributed tracing
- [ ] CI/CD pipeline

## 🐛 Troubleshooting

### Error: "Connection refused" al conectar a PostgreSQL

**Solución:**
```bash
# Verificar que PostgreSQL está corriendo
# Windows:
sc query postgresql-x64-15

# O usar Docker:
docker-compose ps

# Verificar puerto 5432
netstat -ano | findstr :5432
```

### Error: "Authentication failed" en la base de datos

**Solución:**
- Verificar usuario y contraseña en `application.yml`
- Verificar permisos del usuario en PostgreSQL:
```sql
GRANT ALL PRIVILEGES ON DATABASE sigep_db TO sigep_user;
GRANT ALL ON SCHEMA public TO sigep_user;
```

### Error: "Could not resolve dependencies" en Gradle

**Solución:**
```bash
# Limpiar caché y refrescar dependencias
gradlew clean build --refresh-dependencies
```

### Error 403 al probar endpoints en Swagger

**Solución:**
1. Hacer login en `/api/v1/auth/login`
2. Copiar el token de la respuesta
3. Click en botón "Authorize" 🔒 en Swagger
4. Pegar: `Bearer {token}`

### Swagger no muestra endpoints de un módulo

**Solución:**
- Verificar que el módulo tiene `@RestController` y `@RequestMapping`
- Verificar que el package está incluido en component scan
- Verificar que el módulo está en las dependencias de `application`

## 📚 Recursos y Referencias

### Documentación del Proyecto
- [SECURITY.md](SECURITY.md) - Seguridad y autenticación
- [API_CONTRACT.md](API_CONTRACT.md) - Contrato de API para frontend
- [AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md) - Guía de autenticación
- [ARCHITECTURE.md](ARCHITECTURE.md) - Arquitectura del sistema

### Tecnologías Principales
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Security](https://spring.io/projects/spring-security)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [JWT.io](https://jwt.io/) - JSON Web Tokens

### Patrones y Arquitectura
- [Domain-Driven Design](https://martinfowler.com/tags/domain%20driven%20design.html)
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Bounded Contexts](https://martinfowler.com/bliki/BoundedContext.html)

### Herramientas
- [Gradle Documentation](https://docs.gradle.org/)
- [OpenAPI Specification](https://swagger.io/specification/)
- [Docker Documentation](https://docs.docker.com/)

## 👥 Equipo de Desarrollo

Desarrollado para el proyecto SiGEP - Sistema de Gestión de Enseñanza Privada

## 📞 Contacto y Soporte

- **Documentación Swagger**: http://localhost:8080/swagger-ui/index.html
- **Email**: dev@sigep.edu.mx
- **Issues**: Reportar en el sistema de tickets interno

## 📄 Licencia

Privado - Todos los derechos reservados © 2025 SiGEP

---

**Última actualización**: Noviembre 4, 2025  
**Versión**: 1.0.0  
**Estado**: ✅ Producción
# SiGEP Backend - Sistema de Gestión de Enseñanza Privada

## 📋 Descripción

Backend API REST desarrollado con Spring Boot y Kotlin para la gestión integral de un instituto privado de enseñanza de inglés.

## 📚 Documentación

- **[README](README.md)** - Este archivo (introducción y guía rápida)
- **[SECURITY.md](SECURITY.md)** - Documentación completa del módulo de seguridad
- **[API_CONTRACT.md](API_CONTRACT.md)** - Contrato de API para integración con frontend
- **[AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md)** - Guía de autenticación y testing
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Arquitectura detallada del sistema
- **[Swagger UI](http://localhost:8080/swagger-ui/index.html)** - Documentación interactiva de la API (cuando la app está corriendo)

## 🏗️ Arquitectura

### Arquitectura Modular basada en Domain-Driven Design (DDD)

El proyecto está estructurado en módulos Gradle independientes que facilitan la migración futura a microservicios:

```
sigep-backend/
├── common/                  # Módulo compartido (excepciones, DTOs base, utilidades)
├── security/                # Autenticación y autorización (JWT, roles, rate limiting)
├── students/                # Bounded Context: Gestión de estudiantes
├── courses/                 # Bounded Context: Gestión de cursos y materiales
├── staff/                   # Bounded Context: Gestión de personal docente y no docente
├── exams/                   # Bounded Context: Gestión de exámenes y calificaciones
├── scheduling/              # Bounded Context: Programación de horarios y sesiones
├── payments/                # Bounded Context: Gestión de pagos (En desarrollo)
├── communications/          # Bounded Context: Notificaciones (En desarrollo)
├── reports/                 # Bounded Context: Reportes (En desarrollo)
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
- **Build Tool**: Gradle 8.14.3 (Kotlin DSL)
- **Base de Datos**: PostgreSQL 15+
- **Cache**: Redis 7+
- **Seguridad**: 
  - Spring Security 6.5.5
  - JWT (jjwt 0.12.3) con tokens de acceso y refresh
  - BCrypt (strength 12) para encriptación de contraseñas
  - Bucket4j para rate limiting
- **Documentación API**: OpenAPI 3 (Swagger UI)
- **ORM**: Spring Data JPA con Hibernate
- **Logging**: SLF4J + Logback
- **Testing**: JUnit 5, MockK (pendiente de implementar)

## 📦 Módulos del Sistema

### 🔧 Common (Módulo Compartido)
**Estado**: ✅ Completado y estable

Módulo fundamental que proporciona la base para todos los demás módulos:
- **DTOs compartidos**: ApiResponse, PageResponse, PageRequest
- **Excepciones personalizadas**: BusinessException, ResourceNotFoundException, etc.
- **Abstracciones DDD**: AggregateRoot, ValueObject, BaseEntity
- **Auditoría JPA**: AuditMetadata con createdAt, createdBy, updatedAt, updatedBy
- **Global Exception Handler**: Manejo centralizado de errores
- **Configuración compartida**: JpaAuditing, serialización

**Características principales**:
- ✅ Respuestas API estandarizadas
- ✅ Sistema de paginación consistente
- ✅ Mapeo automático de excepciones a HTTP status codes
- ✅ Auditoría automática de entidades
- ✅ Logging estructurado

**Ver documentación completa**: [common/README.md](common/README.md)

---

### 🚀 Application (Módulo Principal)
**Estado**: ✅ Completado y funcionando

Módulo orquestador que integra todos los bounded contexts:
- **Punto de entrada**: Clase principal SigepApplication con método main()
- **Configuración OpenAPI**: Swagger UI con autenticación JWT
- **Configuración Redis**: Sistema de caché distribuido (TTL 10 min)
- **Component Scanning**: Escaneo automático de todos los módulos
- **JPA Configuration**: Repositorios y entidades de todos los módulos
- **Actuator Endpoints**: Health checks, métricas, Prometheus

**Dependencias integradas**:
- ✅ Todos los bounded contexts (students, courses, exams, staff, etc.)
- ✅ Spring Security (delegado al módulo security)
- ✅ PostgreSQL + Redis
- ✅ Swagger/OpenAPI
- ✅ Spring Boot DevTools (hot reload)

**Endpoints de monitoreo**:
- `/actuator/health` - Health check (público)
- `/actuator/metrics` - Métricas (ADMIN)
- `/actuator/prometheus` - Prometheus metrics (ADMIN)

**Ver documentación completa**: [application/README.md](application/README.md)

---

### 🔐 Security (Seguridad)
**Estado**: ✅ Completado y funcionando

Módulo central de autenticación y autorización que provee:
- Autenticación con JWT (tokens de acceso + refresh tokens)
- Sistema de roles: ADMIN, TEACHER, GUARDIAN
- Rate limiting para prevenir ataques de fuerza bruta
- CORS configurado para frontend Angular
- Anotaciones personalizadas de seguridad (@RequireAdmin, @RequireTeacher, etc.)
- Manejo centralizado de errores de seguridad
- Data initializer con usuarios de prueba en modo desarrollo

**Endpoints principales:**
- `POST /api/v1/auth/login` - Login y obtención de tokens
- `POST /api/v1/auth/register` - Registro de nuevos usuarios
- `POST /api/v1/auth/refresh-token` - Renovación de tokens
- `POST /api/v1/auth/logout` - Cierre de sesión

**Ver documentación completa**: [SECURITY.md](SECURITY.md)

---

### 👥 Students (Estudiantes)
**Estado**: ✅ Completado y funcionando

Gestión completa de estudiantes con:
- CRUD de estudiantes
- Historial de cursos
- Relación con tutores (guardians)
- Búsqueda y filtrado
- Paginación

---

### 📚 Courses (Cursos)
**Estado**: ✅ Completado y funcionando

Gestión de cursos con:
- CRUD de cursos
- Inscripción de estudiantes
- Materiales del curso
- Sesiones programadas
- Sistema de asistencia
- Certificados de finalización

---

### 👔 Staff (Personal)
**Estado**: ✅ Completado y funcionando

Gestión de personal docente y no docente:
- **Personal docente**: gestión de profesores, cursos asignados, estudiantes, presentismo
- **Personal no docente**: limpieza, mantenimiento, IT, administración
- Control de asistencia
- Notas y observaciones

---

### 📝 Exams (Exámenes)
**Estado**: ✅ Completado y funcionando

Gestión de exámenes y calificaciones:
- CRUD de exámenes
- Calificaciones por estudiante
- Análisis de rendimiento de docentes
- Estadísticas y métricas
- Historial académico

---

### 💳 Payments (Pagos)
**Estado**: 🚧 En desarrollo

Módulo para gestión de:
- Pagos de estudiantes (cuotas mensuales)
- Pagos a personal docente
- Pagos a personal no docente
- Historial de transacciones

---

### 📅 Scheduling (Programación)
**Estado**: ✅ Parcialmente completado
## 🚀 Tecnologías
Integrado con módulo de Courses para:
- Sesiones de curso
- Horarios y aulas
- Asistencia a sesiones
- **Lenguaje**: Kotlin 1.9.25
---
- **Seguridad**: Spring Security + JWT
### 🔔 Communications (Comunicaciones)
**Estado**: 🚧 En desarrollo
- Spring Boot Starter Web
Módulo de notificaciones para:
- Notificaciones de nuevos materiales
- Notificaciones de exámenes
- Certificados emitidos
- JWT (jjwt 0.12.3)
---

### 📊 Reports (Reportes)
**Estado**: 🚧 En desarrollo

Generación de reportes administrativos.

---

### 🔧 Common (Común)
**Estado**: ✅ Completado

Módulo compartido con:
- DTOs base (ApiResponse, PageResponse)
- Excepciones personalizadas
- Utilidades comunes
- Domain model base (AggregateRoot)

---

### 🚀 Application (Aplicación Principal)
**Estado**: ✅ Completado

Módulo orquestador que:
- Integra todos los bounded contexts
- Configura Swagger/OpenAPI
- Configura Actuator y métricas
- Punto de entrada de la aplicación
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

### Requisitos Previos

- **JDK**: 17 o superior
- **PostgreSQL**: 15 o superior
- **Redis**: 7 o superior (opcional para caché)
- **Gradle**: 8.14.3 (incluido wrapper, no requiere instalación)
- **Docker** (opcional): Para levantar PostgreSQL y Redis en contenedores

### Setup de Base de Datos

#### Opción 1: Docker Compose (Recomendado para desarrollo)

```bash
# Iniciar PostgreSQL y Redis en contenedores
docker-compose up -d

# Verificar que están corriendo
docker-compose ps
```

#### Opción 2: Instalación Local

```sql
-- Conectarse a PostgreSQL como superusuario
psql -U postgres

-- Crear base de datos
CREATE DATABASE sigep_db;

-- Crear usuario
CREATE USER sigep_user WITH PASSWORD 'sigep_password';

-- Otorgar permisos
GRANT ALL PRIVILEGES ON DATABASE sigep_db TO sigep_user;
GRANT ALL ON SCHEMA public TO sigep_user;
```

### Configuración de la Aplicación

La configuración se encuentra en `application/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sigep_db
    username: sigep_user
    password: sigep_password
  
  jpa:
    hibernate:
      ddl-auto: update  # Usar 'validate' en producción
    show-sql: true
  
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: mySecretKeyForJWTTokenGenerationShouldBeAtLeast256BitsLongForHS512Algorithm
  expiration: 86400000      # 24 horas
  refresh-expiration: 604800000  # 7 días

app:
  cors:
    allowed-origins: http://localhost:4200,https://sigep.edu.mx
```

> ⚠️ **Importante para Producción**: 
> - Cambiar `jwt.secret` por una clave aleatoria de 512+ bits
> - Usar variables de entorno en lugar de valores hardcoded
> - Configurar `ddl-auto: validate` o usar migraciones con Flyway/Liquibase
> - Habilitar HTTPS

## 🏃 Ejecución

### Compilar el Proyecto

```bash
# Windows
gradlew clean build

# Linux/Mac
./gradlew clean build
```

### Ejecutar la Aplicación

#### Opción 1: Con Gradle (Desarrollo - con Hot Reload)

```bash
# Windows
gradlew :application:bootRun

# Linux/Mac
./gradlew :application:bootRun
```

#### Opción 2: Con JAR compilado

```bash
# Compilar primero
gradlew :application:bootJar

# Ejecutar el JAR
java -jar application/build/libs/sigep-backend.jar
```

#### Opción 3: Con perfil específico

```bash
# Ejecutar en modo desarrollo (crea usuarios de prueba)
gradlew :application:bootRun --args='--spring.profiles.active=dev'

# Ejecutar en modo producción
gradlew :application:bootRun --args='--spring.profiles.active=prod'
```

### Verificar que la Aplicación Está Corriendo

1. **Health Check**: http://localhost:8080/actuator/health
   - Debe retornar: `{"status":"UP"}`

2. **Swagger UI**: http://localhost:8080/swagger-ui/index.html
   - Documentación interactiva de la API

3. **API Docs JSON**: http://localhost:8080/v3/api-docs
   - Especificación OpenAPI en formato JSON

### Usuarios de Prueba (Modo Dev)

En modo desarrollo, se crean automáticamente estos usuarios:

| Usuario | Contraseña | Rol | Email |
|---------|------------|-----|-------|
| admin | password123 | ADMIN | admin@sigep.edu.mx |
| teacher | password123 | TEACHER | teacher@sigep.edu.mx |
| guardian | password123 | GUARDIAN | guardian@sigep.edu.mx |

### Testing

```bash
# Ejecutar todos los tests
gradlew test

# Ejecutar tests de un módulo específico
gradlew :students:test
gradlew :security:test

# Ejecutar tests con reporte detallado
gradlew test --info

# Ver reporte de tests en HTML
# Abrir: build/reports/tests/test/index.html
```

### Logs

Los logs se muestran en consola con el siguiente formato:

```
2025-11-03 10:00:00.123  INFO 12345 --- [main] c.s.application.SigepApplicationKt : Started SigepApplicationKt in 7.5 seconds
2025-11-03 10:00:15.456 DEBUG 12345 --- [nio-8080-exec-1] o.s.security.web.FilterChainProxy : Securing GET /api/v1/students
```

Niveles de log configurables en `application.yml`:
- `logging.level.com.sigep=DEBUG` - Para debugging detallado
- `logging.level.org.springframework.security=DEBUG` - Para debugging de seguridad

## 📡 API Endpoints

### Base URL
```
Desarrollo: http://localhost:8080
Producción: https://api.sigep.edu.mx
```

### API Prefix
Todos los endpoints: `/api/v1`

### 🔐 Autenticación

| Método | Endpoint | Descripción | Autenticación |
|--------|----------|-------------|---------------|
| POST | `/auth/login` | Iniciar sesión | No |
| POST | `/auth/register` | Registrar usuario | No |
| POST | `/auth/refresh-token` | Renovar token | No |
| POST | `/auth/logout` | Cerrar sesión | Sí |

**Ejemplo de uso:**
```bash
# Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}'

# Usar el token en requests
curl -X GET http://localhost:8080/api/v1/students \
  -H "Authorization: Bearer {token}"
```

### 👥 Estudiantes

| Método | Endpoint | Descripción | Roles |
|--------|----------|-------------|-------|
| GET | `/students` | Listar estudiantes (paginado) | ADMIN, TEACHER |
| GET | `/students/{id}` | Obtener estudiante | ADMIN, TEACHER, GUARDIAN* |
| POST | `/students` | Crear estudiante | ADMIN |
| PUT | `/students/{id}` | Actualizar estudiante | ADMIN |
| DELETE | `/students/{id}` | Eliminar estudiante | ADMIN |
| GET | `/students/search` | Buscar estudiantes | ADMIN, TEACHER |
| GET | `/students/guardian/{id}` | Estudiantes por tutor | ADMIN, TEACHER, GUARDIAN* |

\* Solo puede ver sus propios estudiantes

### 📚 Cursos

| Método | Endpoint | Descripción | Roles |
|--------|----------|-------------|-------|
| GET | `/courses` | Listar cursos | ADMIN, TEACHER, GUARDIAN |
| GET | `/courses/{id}` | Obtener curso | ADMIN, TEACHER, GUARDIAN |
| POST | `/courses` | Crear curso | ADMIN |
| PUT | `/courses/{id}` | Actualizar curso | ADMIN |
| DELETE | `/courses/{id}` | Eliminar curso | ADMIN |
| POST | `/courses/{id}/enrollments` | Inscribir estudiante | ADMIN, TEACHER* |
| POST | `/courses/{id}/materials` | Subir material | ADMIN, TEACHER* |
| GET | `/courses/{id}/materials` | Listar materiales | ADMIN, TEACHER, GUARDIAN |
| GET | `/courses/{id}/sessions` | Listar sesiones | ADMIN, TEACHER, GUARDIAN |
| POST | `/courses/{id}/attendance` | Registrar asistencia | ADMIN, TEACHER* |

\* Solo cursos asignados

### 📝 Exámenes

| Método | Endpoint | Descripción | Roles |
|--------|----------|-------------|-------|
| GET | `/exams` | Listar exámenes | ADMIN, TEACHER |
| GET | `/exams/{id}` | Obtener examen | ADMIN, TEACHER, GUARDIAN* |
| POST | `/exams` | Crear examen | ADMIN, TEACHER |
| PUT | `/exams/{id}` | Actualizar examen | ADMIN, TEACHER* |
| DELETE | `/exams/{id}` | Eliminar examen | ADMIN |
| GET | `/exams/{id}/grades` | Obtener calificaciones | ADMIN, TEACHER, GUARDIAN* |
| POST | `/exams/{id}/grades` | Registrar calificación | ADMIN, TEACHER* |
| GET | `/exams/student/{studentId}` | Historial de exámenes | ADMIN, TEACHER, GUARDIAN* |

\* Con restricciones según relación

### 👔 Personal (Staff)

| Método | Endpoint | Descripción | Roles |
|--------|----------|-------------|-------|
| GET | `/staff/teaching` | Listar docentes | ADMIN |
| GET | `/staff/teaching/{id}` | Obtener docente | ADMIN |
| POST | `/staff/teaching` | Crear docente | ADMIN |
| PUT | `/staff/teaching/{id}` | Actualizar docente | ADMIN |
| DELETE | `/staff/teaching/{id}` | Eliminar docente | ADMIN |
| GET | `/staff/non-teaching` | Listar no docentes | ADMIN |
| POST | `/staff/attendance` | Registrar asistencia | ADMIN |
| GET | `/staff/{id}/attendance` | Ver asistencia | ADMIN |

### 📊 Reportes y Análisis

| Método | Endpoint | Descripción | Roles |
|--------|----------|-------------|-------|
| GET | `/teachers/{id}/performance` | Rendimiento docente | ADMIN |
| GET | `/courses/{id}/statistics` | Estadísticas de curso | ADMIN, TEACHER* |
| GET | `/students/{id}/progress` | Progreso de estudiante | ADMIN, TEACHER, GUARDIAN* |

### 📖 Documentación Completa

Para ver todos los endpoints con detalles de request/response:

- **Swagger UI Interactivo**: http://localhost:8080/swagger-ui/index.html
- **Contrato de API para Frontend**: [API_CONTRACT.md](API_CONTRACT.md)
- **Guía de Autenticación**: [AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md)

## 🔐 Seguridad y Roles

### Sistema de Roles (RBAC)

La aplicación implementa control de acceso basado en roles con tres niveles:

| Rol | Descripción | Acceso Principal |
|-----|-------------|------------------|
| **ADMIN** | Administrador del sistema | Acceso total a todos los módulos y funcionalidades |
| **TEACHER** | Docente/Profesor | Gestión de cursos asignados, estudiantes, exámenes y calificaciones |
| **GUARDIAN** | Responsable/Tutor | Solo lectura de información de sus estudiantes asignados |

### Permisos por Módulo

#### 👥 Students (Estudiantes)
- **ADMIN**: CRUD completo
- **TEACHER**: Lectura de todos, escritura limitada
- **GUARDIAN**: Solo lectura de estudiantes asignados

#### 📚 Courses (Cursos)
- **ADMIN**: CRUD completo
- **TEACHER**: Lectura de todos, escritura solo en cursos asignados
- **GUARDIAN**: Solo lectura

#### 📝 Exams (Exámenes)
- **ADMIN**: CRUD completo, gestión de calificaciones
- **TEACHER**: CRUD de exámenes en cursos asignados, calificar
- **GUARDIAN**: Solo lectura de exámenes y calificaciones de sus estudiantes

#### 👔 Staff (Personal)
- **ADMIN**: CRUD completo
- **TEACHER**: Sin acceso
- **GUARDIAN**: Sin acceso

#### 💳 Payments (Pagos - en desarrollo)
- **ADMIN**: Gestión completa
- **TEACHER**: Ver sus pagos
- **GUARDIAN**: Ver pagos de sus estudiantes

### Características de Seguridad

✅ **Autenticación JWT Stateless**
- Access token: 24 horas de validez
- Refresh token: 7 días de validez
- Renovación automática sin re-login

✅ **Encriptación Robusta**
- BCrypt con strength 12 para contraseñas
- Tokens firmados con HS512

✅ **Rate Limiting**
- 100 requests por minuto por cliente
- Protección contra fuerza bruta

✅ **CORS Configurado**
- Orígenes permitidos: localhost:4200 (dev) + dominio producción
- Headers controlados

✅ **Validación de Entrada**
- Todas las requests validadas con Bean Validation
- DTOs tipados con TypeScript-like contracts

**Ver documentación completa**: [SECURITY.md](SECURITY.md)

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

