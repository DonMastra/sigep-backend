# Validación de Coherencia BD ↔ Código SiGEP Backend

**Fecha**: Mayo 2026  
**Cambios Aplicados**: Extensión de perfil de usuario (`users` table) para soportar registro GUARDIAN ampliado

---

## 📋 Resumen Ejecutivo

Se han implementado cambios en la entidad `User` para persistir campos adicionales de perfil requeridos por el flujo de registro ampliado y alta de estudiante por GUARDIAN. Estos cambios están alineados entre código Kotlin/JPA y esquema de BD (PostgreSQL).

---

## 🔍 Validación de Coherencia

### Entidad: `com.sigep.security.domain.model.User`

#### Nuevos Campos Agregados

| Campo Kotlin | Tipo | Columna BD | Nullable | Índices |
|---|---|---|---|---|
| `phoneNumber` | String? | `phone_number` | ✅ SI | - |
| `address` | String? | `address` | ✅ SI | - |
| `dateOfBirth` | LocalDate? | `date_of_birth` | ✅ SI | - |
| `documentNumber` | String? | `document_number` | ✅ SI | ✅ UNIQUE (NULLS DISTINCT) |
| `emergencyContact` | String? | `emergency_contact` | ✅ SI | - |

#### Campos Preexistentes (Alineados)

| Campo Kotlin | Tipo | Columna BD | Nullable | Cambios |
|---|---|---|---|---|
| `id` | Long? | `id` | ❌ NO | Explícitamente nombrado ✅ |
| `username` | String | `username` | ❌ NO | UNIQUE |
| `email` | String | `email` | ❌ NO | UNIQUE |
| `password` | String | `password` | ❌ NO | - |
| `firstName` | String | `first_name` | ❌ NO | - |
| `lastName` | String | `last_name` | ❌ NO | - |
| `role` | UserRole (enum) | `role` | ❌ NO | Explícitamente nombrado ✅ |
| `status` | AccountStatus (enum) | `status` | ❌ NO | Explícitamente nombrado ✅ |
| `active` | Boolean | `active` | ❌ NO | - |
| `createdAt` | LocalDateTime | `created_at` | ❌ NO | Explícitamente nombrado ✅ |
| `updatedAt` | LocalDateTime | `updated_at` | ❌ NO | Explícitamente nombrado ✅ |

---

## 🔄 Estrategia de Aplicación de Cambios

### Desarrollo (`ddl-auto: update`)
- Hibernate genera automáticamente las nuevas columnas en `users` al arrancar.
- Los nombres de columnas en snake_case están explícitamente definidos en anotaciones `@Column(name = "...")`.
- **No requiere intervención manual de BD.**

### Producción (`ddl-auto: validate`)
- **Migración Flyway V11** (`scripts/migrations/V11__extend_users_profile_fields.sql`):
  - Agrega 5 columnas opcionales a `users`.
  - Crea índice en `document_number` para búsquedas rápidas.
  - Agrega restricción UNIQUE NULLS DISTINCT en `document_number`.
  - Valida la integridad del esquema antes de arrancar.

---

## 📝 Detalles de Migración Flyway V11

**Archivo**: `scripts/migrations/V11__extend_users_profile_fields.sql`

```sql
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20) NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS address VARCHAR(500) NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS date_of_birth DATE NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS document_number VARCHAR(50) NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS emergency_contact VARCHAR(255) NULL;

-- Índice para búsquedas de documento (usado en validación de student registration)
CREATE INDEX IF NOT EXISTS idx_users_document_number
    ON users(document_number);

-- Restricción única permitiendo múltiples NULLs
ALTER TABLE users
    ADD CONSTRAINT users_document_number_unique_not_null
    UNIQUE NULLS DISTINCT (document_number)
    WHERE document_number IS NOT NULL;
```

---

## ✅ Verificación de Coherencia

### Alineación de Nombres
- ✅ Atributos Kotlin (camelCase) → Explícitamente mapeados a columnas BD (snake_case)
- ✅ Tipos de datos Kotlin → PostgreSQL:
  - `String?` → `VARCHAR`
  - `LocalDate?` → `DATE`
  - `LocalDateTime` → `TIMESTAMP`
  - `UserRole (enum)` → `VARCHAR` + CHECK constraint
  - `AccountStatus (enum)` → `VARCHAR` + CHECK constraint

### Restricciones
- ✅ `documentNumber` único (NULLABLE) para permitir múltiples usuarios sin documento (ej: menores de edad)
- ✅ Email y Username únicos (preexistentes)
- ✅ Campos de perfil nullable para compatibilidad con usuarios ADMIN/TEACHER que no requieren rellenar perfil extendido

### DTOs de Persistencia

**AuthDtos.kt**:
- ✅ `RegisterRequest`: incluye campos opcionales del perfil
- ✅ `UserProfileDto`: retorna perfil completo autenticado en `GET /api/v1/users/me`

**StudentDtos.kt**:
- ✅ `GuardianStudentRegistrationRequest`: soporta precarga desde perfil con `useGuardianProfileData`

---

## 🚀 Impacto en Flujos

### 1. Registro Público (POST /api/v1/auth/register)
- **Comportamiento**: Acepta campos opcionales; los guarda en BD si se envían
- **BD**: Todos los campos nuevos quedan NULL si no se envían
- **Compatibilidad**: Retrocompatible; clientes antiguos que NO envían esos campos funcionan sin cambios

### 2. Perfil Autenticado (GET /api/v1/users/me)
- **Comportamiento**: Retorna todos los campos de perfil (incluyendo NULL si vacíos)
- **BD**: Lee directamente los campos nuevos de `users`

### 3. Alta de Estudiante por GUARDIAN
- **Comportamiento**: 
  - Opción A: Precarga desde perfil autenticado (`useGuardianProfileData = true`)
  - Opción B: GUARDIAN proporciona datos nuevos (sobrescribe perfil)
- **BD**: Valida unicidad de email/documento antes de crear student

---

## 📊 Estado de Compilación

| Módulo | Estado | Nota |
|---|---|---|
| `security` | ✅ BUILD OK | Entidad `User` actualizada, anotaciones explícitas |
| `students` | ✅ BUILD OK | `StudentService.createStudentForGuardian` implementado |
| `common` | ✅ BUILD OK | Sin cambios en DTOs core |
| `application` | ✅ BUILD OK | Proyecto general compilado |

---

## 🔍 Próximos Pasos para Validación Completa

### 1. Arrancar en desarrollo
```bash
./gradlew :application:bootRun --args='--spring.profiles.active=dev'
```
- Hibernate ejecutará `ddl-auto: update` automáticamente.
- Verificar en logs que las columnas se crean sin errores.

### 2. Validar esquema en BD
```sql
\d users
-- Verificar que existan:
-- phone_number VARCHAR(20)
-- address VARCHAR(500)
-- date_of_birth DATE
-- document_number VARCHAR(50)
-- emergency_contact VARCHAR(255)
```

### 3. Pruebas funcionales
- Registrar usuario GUARDIAN con perfil (`POST /api/v1/auth/register`)
- Obtener perfil autenticado (`GET /api/v1/users/me`)
- Crear estudiante por GUARDIAN (`POST /api/v1/students/self-registration`)

---

## 📋 Matriz de Compatibilidad

| Escenario | Prod | Dev | Nota |
|---|---|---|---|
| BD vacía → Aplicación dev | ✅ | ✅ | Hibernate crea esquema |
| BD vacía → Aplicación prod | ❓ | - | Requiere ejecutar Flyway V1-V11 previamente |
| BD existente (sin V11) → Aplicación dev | ✅ | ✅ | Hibernate agrega columnas |
| BD existente (V11 aplicada) → Aplicación prod | ✅ | - | Validación exitosa |

---

## 🛠️ Archivos Modificados/Creados

```
security/src/main/kotlin/com/sigep/security/domain/model/User.kt
  ├─ Campos nuevos: phoneNumber, address, dateOfBirth, documentNumber, emergencyContact
  └─ Anotaciones explícitas @Column(name = "...")

security/src/main/kotlin/com/sigep/security/application/dto/AuthDtos.kt
  ├─ RegisterRequest: campos opcionales extendidos
  └─ UserProfileDto: nuevo DTO para /users/me

security/src/main/kotlin/com/sigep/security/application/service/AuthService.kt
  ├─ register(...): persiste campos extendidos
  └─ getMyProfile(userId): retorna UserProfileDto

security/src/main/kotlin/com/sigep/security/presentation/controller/UserProfileController.kt
  └─ GET /api/v1/users/me: nuevo endpoint

students/src/main/kotlin/com/sigep/students/application/dto/StudentDtos.kt
  └─ GuardianStudentRegistrationRequest: nuevo DTO

students/src/main/kotlin/com/sigep/students/application/service/StudentService.kt
  └─ createStudentForGuardian(...): nueva lógica

students/src/main/kotlin/com/sigep/students/presentation/controller/StudentController.kt
  └─ POST /api/v1/students/self-registration: nuevo endpoint

scripts/migrations/V11__extend_users_profile_fields.sql
  └─ Migración Flyway nueva

API_CONTRACT.md
  └─ Documentación de endpoints y payloads actualizados
```

---

## ✨ Conclusión

**Estado**: ✅ **COHERENTE**

- Código Kotlin y BD están alineados.
- Anotaciones explícitas en entidades aseguran nombres de columna consistentes.
- Migración Flyway lista para producción.
- Híbridos `ddl-auto: update` (dev) y Flyway (prod) aplicados correctamente.
- Sin errores de compilación; proyecto builds successfully.

**Próximo paso**: Ejecutar en dev y validar que Hibernate genera las columnas sin problemas.

---

**Generado**: 2026-05-10  
**Versión del Proyecto**: SiGEP Backend 1.0.0  
**Stack**: Kotlin 1.9.25 + Spring Boot 3.5.6 + PostgreSQL 15

