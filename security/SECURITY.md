# Documentación de Seguridad - SiGEP Backend

## 📋 Índice
- [Arquitectura de Seguridad](#arquitectura-de-seguridad)
- [Autenticación](#autenticación)
- [Autorización](#autorización)
- [Roles y Permisos](#roles-y-permisos)
- [JWT Tokens](#jwt-tokens)
- [Rate Limiting](#rate-limiting)
- [CORS Configuration](#cors-configuration)
- [Endpoints Públicos vs Protegidos](#endpoints-públicos-vs-protegidos)
- [Manejo de Errores de Seguridad](#manejo-de-errores-de-seguridad)

---

## 🏗️ Arquitectura de Seguridad

El módulo de seguridad implementa un sistema robusto basado en **JWT (JSON Web Tokens)** y **Spring Security** con las siguientes características:

- ✅ Autenticación stateless con JWT
- ✅ Refresh tokens para renovación de sesión
- ✅ Control de acceso basado en roles (RBAC)
- ✅ Rate limiting para prevenir ataques de fuerza bruta
- ✅ CORS configurado para frontend Angular
- ✅ Encriptación de contraseñas con BCrypt (strength 12)
- ✅ Manejo centralizado de errores de seguridad

### Flujo de Autenticación

```
┌─────────┐         ┌──────────┐         ┌──────────┐         ┌──────────┐
│ Cliente │         │   Auth   │         │   JWT    │         │   User   │
│Frontend │         │Controller│         │ Provider │         │Repository│
└────┬────┘         └────┬─────┘         └────┬─────┘         └────┬─────┘
     │                   │                    │                     │
     │  POST /auth/login │                    │                     │
     ├──────────────────>│                    │                     │
     │                   │  Validate credentials                   │
     │                   ├───────────────────────────────────────>│
     │                   │                    │    User Entity     │
     │                   │<───────────────────────────────────────┤
     │                   │  Generate JWT      │                     │
     │                   ├───────────────────>│                     │
     │                   │  Access Token +    │                     │
     │                   │  Refresh Token     │                     │
     │                   │<───────────────────┤                     │
     │  LoginResponse    │                    │                     │
     │  (tokens + user)  │                    │                     │
     │<──────────────────┤                    │                     │
     │                   │                    │                     │
```

---

## 🔐 Autenticación

### Endpoints de Autenticación

#### 1. Login
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password123"
}
```

**Respuesta exitosa (200 OK):**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "user": {
      "id": 1,
      "username": "admin",
      "email": "admin@sigep.edu.mx",
      "firstName": "Admin",
      "lastName": "Sistema",
      "role": "ADMIN",
      "active": true
    }
  },
  "message": "Login successful",
  "timestamp": "2025-11-03T10:00:00Z"
}
```

**Errores:**
- `401 Unauthorized`: Credenciales inválidas o usuario inactivo
- `429 Too Many Requests`: Demasiados intentos de login

#### 2. Register
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "nuevo_usuario",
  "email": "usuario@example.com",
  "password": "SecurePass123",
  "firstName": "Nombre",
  "lastName": "Apellido",
  "role": "TEACHER"
}
```

**Validaciones:**
- Username: mínimo 3 caracteres, único
- Email: formato válido, único
- Password: mínimo 6 caracteres
- Role: ADMIN | TEACHER | GUARDIAN

**Respuesta exitosa (201 Created):**
```json
{
  "success": true,
  "data": {
    "id": 2,
    "username": "nuevo_usuario",
    "email": "usuario@example.com",
    "firstName": "Nombre",
    "lastName": "Apellido",
    "role": "TEACHER",
    "active": true
  },
  "message": "User registered successfully",
  "timestamp": "2025-11-03T10:05:00Z"
}
```

**Errores:**
- `409 Conflict`: Username o email ya existe
- `400 Bad Request`: Validación de campos fallida

#### 3. Refresh Token
```http
POST /api/v1/auth/refresh-token
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

**Respuesta exitosa (200 OK):**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "user": { ... }
  },
  "message": "Token refreshed successfully",
  "timestamp": "2025-11-03T10:10:00Z"
}
```

#### 4. Logout
```http
POST /api/v1/auth/logout
Authorization: Bearer {token}
```

> **Nota**: En un sistema JWT stateless, el logout se maneja principalmente en el cliente eliminando los tokens. Para implementar blacklist de tokens, se puede usar Redis.

---

## 🛡️ Autorización

### Sistema de Roles (RBAC - Role-Based Access Control)

La aplicación define tres roles principales:

| Role      | Descripción                                    | Permisos Generales |
|-----------|------------------------------------------------|-------------------|
| `ADMIN`   | Administrador del sistema                      | Acceso total      |
| `TEACHER` | Docente/Profesor                               | Gestión de cursos asignados, exámenes y estudiantes |
| `GUARDIAN`| Responsable/Tutor de estudiantes               | Solo lectura de información de sus estudiantes |

### Anotaciones de Seguridad

El módulo provee anotaciones personalizadas para facilitar el control de acceso:

```kotlin
// Solo administradores
@RequireAdmin
@GetMapping("/admin/reports")
fun getAdminReports() { ... }

// Solo profesores
@RequireTeacher
@PostMapping("/exams")
fun createExam() { ... }

// Solo tutores
@RequireGuardian
@GetMapping("/my-students")
fun getMyStudents() { ... }

// Administradores o profesores
@RequireAdminOrTeacher
@GetMapping("/students")
fun listStudents() { ... }

// Cualquier usuario autenticado (admin, teacher o guardian)
@RequireStaffOrGuardian
@GetMapping("/courses")
fun listCourses() { ... }

// Acceso a recursos propios o staff
@RequireOwnershipOrStaff
@GetMapping("/students/{id}")
fun getStudent(@PathVariable id: Long) { ... }
```

---

## 🔑 JWT Tokens

### Estructura del Access Token

El JWT contiene los siguientes claims:

```json
{
  "sub": "admin",              // username
  "userId": 1,                 // ID del usuario
  "role": "ADMIN",             // Rol del usuario
  "email": "admin@sigep.edu.mx",
  "iat": 1699000000,           // Issued at (timestamp)
  "exp": 1699086400            // Expiration (timestamp)
}
```

### Configuración de Tokens

| Parámetro | Valor por Defecto | Descripción |
|-----------|-------------------|-------------|
| `jwt.secret` | `mySecretKey...` | Clave secreta (mínimo 256 bits para HS512) |
| `jwt.expiration` | `86400000` ms | Expiración del access token (24 horas) |
| `jwt.refresh-expiration` | `604800000` ms | Expiración del refresh token (7 días) |

### Uso del Token en Requests

Todas las peticiones a endpoints protegidos deben incluir el header:

```http
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

### Validación de Tokens

El filtro `JwtAuthenticationFilter` intercepta todas las requests y:

1. Extrae el token del header `Authorization`
2. Valida la firma y expiración
3. Extrae los claims (username, userId, role)
4. Crea el objeto `Authentication` en el `SecurityContext`
5. Permite o deniega el acceso según los roles

---

## 🚦 Rate Limiting

### Configuración

Para prevenir ataques de fuerza bruta y DDoS, se implementa rate limiting con **Bucket4j**:

| Parámetro | Valor por Defecto | Descripción |
|-----------|-------------------|-------------|
| Capacidad | 100 requests | Máximo de peticiones permitidas |
| Refill | 100 tokens/minuto | Tokens que se reponen cada minuto |

### Funcionamiento

- Cada cliente (identificado por IP o userId) tiene un bucket de tokens
- Cada request consume 1 token
- Si no hay tokens disponibles, se retorna `429 Too Many Requests`
- Los tokens se reponen gradualmente según la configuración

### Endpoints Excluidos

No se aplica rate limiting a:
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/actuator/health`

### Respuesta de Rate Limit Excedido

```json
{
  "success": false,
  "message": "Too many requests. Please try again later.",
  "errors": ["RATE_LIMIT_EXCEEDED"]
}
```

---

## 🌐 CORS Configuration

### Orígenes Permitidos

Por defecto se permiten requests desde:
- `http://localhost:4200` (desarrollo Angular)
- `https://sigep.edu.mx` (producción)

### Configuración

```yaml
app:
  cors:
    allowed-origins: http://localhost:4200,https://sigep.edu.mx
```

### Métodos HTTP Permitidos
- GET
- POST
- PUT
- DELETE
- PATCH
- OPTIONS

### Headers Permitidos
- Authorization
- Content-Type
- X-Requested-With
- Accept
- Origin
- Access-Control-Request-Method
- Access-Control-Request-Headers

### Headers Expuestos (para lectura desde frontend)
- Authorization
- X-Total-Count
- X-Page-Number
- X-Page-Size

---

## 🔓 Endpoints Públicos vs Protegidos

### Endpoints Públicos (sin autenticación)

✅ **Autenticación:**
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/refresh-token`

✅ **Documentación (solo en desarrollo):**
- `GET /v3/api-docs/**`
- `GET /swagger-ui/**`
- `GET /swagger-ui.html`

✅ **Health Checks:**
- `GET /actuator/health`
- `GET /actuator/info`

### Endpoints Protegidos

🔒 Todos los demás endpoints requieren autenticación con JWT

🔐 Los permisos específicos se controlan mediante:
- Anotaciones `@PreAuthorize` en controladores
- Anotaciones personalizadas (`@RequireAdmin`, etc.)
- Validación en servicios para recursos propios

### Matriz de Permisos por Módulo

| Módulo | Endpoint | ADMIN | TEACHER | GUARDIAN |
|--------|----------|-------|---------|----------|
| Students | GET /students | ✅ | ✅ | ❌ |
| Students | POST /students | ✅ | ❌ | ❌ |
| Students | PUT /students/{id} | ✅ | ❌ | ❌ |
| Students | DELETE /students/{id} | ✅ | ❌ | ❌ |
| Students | GET /students/{id} | ✅ | ✅ | ✅ (solo propios) |
| Courses | GET /courses | ✅ | ✅ | ✅ |
| Courses | POST /courses | ✅ | ❌ | ❌ |
| Courses | PUT /courses/{id} | ✅ | ✅ (asignados) | ❌ |
| Exams | GET /exams | ✅ | ✅ | ❌ |
| Exams | POST /exams | ✅ | ✅ | ❌ |
| Exams | GET /exams/{id}/grades | ✅ | ✅ | ✅ (propios) |
| Staff | GET /staff | ✅ | ❌ | ❌ |
| Staff | POST /staff | ✅ | ❌ | ❌ |
| Payments | GET /payments | ✅ | ❌ | ✅ (propios) |
| Reports | GET /reports/admin | ✅ | ❌ | ❌ |

---

## ⚠️ Manejo de Errores de Seguridad

### Custom Handlers

#### 1. CustomAuthenticationEntryPoint

Maneja errores cuando un usuario **no autenticado** intenta acceder a recursos protegidos.

**Respuesta (401 Unauthorized):**
```json
{
  "success": false,
  "message": "Unauthorized access - Authentication required",
  "timestamp": "2025-11-03T10:00:00Z"
}
```

#### 2. CustomAccessDeniedHandler

Maneja errores cuando un usuario **autenticado** no tiene permisos suficientes.

**Respuesta (403 Forbidden):**
```json
{
  "success": false,
  "message": "Access denied - Insufficient permissions",
  "timestamp": "2025-11-03T10:00:00Z"
}
```

### Excepciones Personalizadas

```kotlin
// Usuario no autorizado
throw UnauthorizedException("Invalid credentials")

// Recurso duplicado
throw DuplicateResourceException("Username already exists")

// Recurso no encontrado
throw ResourceNotFoundException("User not found")
```

---

## 🧪 Testing de Seguridad

### Usuarios de Prueba (Desarrollo)

En modo `dev`, se crean automáticamente:

| Username | Password | Role | Email |
|----------|----------|------|-------|
| admin | password123 | ADMIN | admin@sigep.edu.mx |
| teacher | password123 | TEACHER | teacher@sigep.edu.mx |
| guardian | password123 | GUARDIAN | guardian@sigep.edu.mx |

### Cómo probar en Swagger

1. Ejecutar aplicación con perfil dev:
```bash
gradlew bootRun --args='--spring.profiles.active=dev'
```

2. Ir a: `http://localhost:8080/swagger-ui/index.html`

3. Usar endpoint `/api/v1/auth/login` con uno de los usuarios de prueba

4. Copiar el token de la respuesta

5. Click en botón "Authorize" 🔒

6. Pegar token en formato: `Bearer {token}`

7. Probar endpoints protegidos

---

## 🔒 Mejores Prácticas de Seguridad

### ✅ Implementadas

- ✅ Contraseñas encriptadas con BCrypt (strength 12)
- ✅ Tokens JWT con expiración
- ✅ Refresh tokens para renovación segura de sesión
- ✅ Rate limiting contra fuerza bruta
- ✅ CORS restrictivo
- ✅ Validación de entrada en todos los DTOs
- ✅ Sesiones stateless
- ✅ Separación de roles y permisos
- ✅ Logging de eventos de seguridad

### 🚧 Recomendaciones para Producción

- 🔐 Cambiar `jwt.secret` por una clave aleatoria de 512+ bits
- 🔐 Usar HTTPS en producción
- 🔐 Implementar blacklist de tokens en Redis
- 🔐 Habilitar 2FA para administradores
- 🔐 Auditoría de accesos (logging avanzado)
- 🔐 Escaneo de vulnerabilidades (OWASP ZAP, SonarQube)
- 🔐 Rotación periódica de secrets
- 🔐 Implementar captcha en login después de X intentos fallidos
- 🔐 Configurar timeouts y límites apropiados
- 🔐 Monitoreo y alertas de seguridad

---

## 📚 Referencias

- [Spring Security Documentation](https://spring.io/projects/spring-security)
- [JWT.io](https://jwt.io/)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Bucket4j Documentation](https://bucket4j.com/)

---

**Última actualización**: Noviembre 2025  
**Versión del módulo**: 1.0.0

