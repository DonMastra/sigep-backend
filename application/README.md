# Módulo Application - Documentación

## 📋 Descripción

El módulo **Application** es el punto de entrada y orquestador principal de la aplicación SiGEP. Actúa como capa de integración que une todos los bounded contexts (módulos) y proporciona la configuración global del sistema.

---

## 🎯 Responsabilidades

### 1. Orquestación de Módulos
- Integra todos los bounded contexts (students, courses, exams, staff, etc.)
- Configura el escaneo de componentes de todos los módulos
- Gestiona las dependencias entre módulos

### 2. Configuración Global
- Configuración de OpenAPI/Swagger
- Configuración de Redis/Caché
- Configuración de JPA y repositorios
- Configuración de seguridad (delegada al módulo security)

### 3. Punto de Entrada
- Clase principal `SigepApplication` con método `main()`
- Configuración de Spring Boot
- Activación de características globales (caching, JPA auditing, etc.)

---

## 🏗️ Estructura del Módulo

```
application/
├── src/main/
│   ├── kotlin/com/sigep/application/
│   │   ├── SigepApplication.kt           # Clase principal
│   │   └── config/
│   │       ├── OpenApiConfig.kt          # Configuración de Swagger
│   │       └── RedisConfig.kt            # Configuración de Redis/Cache
│   └── resources/
│       └── application.yml               # Configuración principal
├── build/                                # Artefactos compilados
└── build.gradle.kts                      # Dependencias del módulo
```

---

## 🔧 Componentes Principales

### 1. SigepApplication.kt

**Ubicación**: `com.sigep.application.SigepApplication`

**Anotaciones**:
```kotlin
@SpringBootApplication(scanBasePackages = ["com.sigep"])
@EnableJpaRepositories(basePackages = [
    "com.sigep.students.domain.repository",
    "com.sigep.courses.domain.repository",
    "com.sigep.staff.infrastructure.repository",
    "com.sigep.security.domain.repository"
])
@EntityScan(basePackages = [
    "com.sigep.students.domain.model",
    "com.sigep.courses.domain.model",
    "com.sigep.staff.domain.model",
    "com.sigep.security.domain.model"
])
@EnableCaching
```

**Funcionalidades**:
- ✅ Escaneo de componentes en todos los módulos
- ✅ Configuración de repositorios JPA
- ✅ Escaneo de entidades JPA
- ✅ Habilitación de caché con Redis

---

### 2. OpenApiConfig.kt

**Ubicación**: `com.sigep.application.config.OpenApiConfig`

**Propósito**: Configurar Swagger UI y documentación OpenAPI para toda la API

**Características**:
```kotlin
@Configuration
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI {
        // Configuración de metadata de la API
        // Configuración de seguridad JWT
    }
}
```

**Metadata de la API**:
- **Título**: "SiGEP API - Sistema de Gestión de Enseñanza Privada"
- **Versión**: "1.0.0"
- **Descripción**: Incluye lista de módulos disponibles
- **Contacto**: support@sigep.edu.mx
- **Licencia**: Private License

**Seguridad Configurada**:
- Tipo: HTTP Bearer Authentication
- Esquema: bearer
- Formato: JWT
- Descripción: "Enter JWT token"

**Swagger UI Disponible en**:
- URL: `http://localhost:8080/swagger-ui/index.html`
- Documentación JSON: `http://localhost:8080/v3/api-docs`

---

### 3. RedisConfig.kt

**Ubicación**: `com.sigep.application.config.RedisConfig`

**Propósito**: Configurar Redis como sistema de caché distribuido

**Características**:
```kotlin
@Configuration
@EnableCaching
class RedisConfig {
    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager
}
```

**Configuración de Caché**:
- **TTL (Time To Live)**: 10 minutos por defecto
- **Serialización de Keys**: StringRedisSerializer
- **Serialización de Values**: GenericJackson2JsonRedisSerializer
- **Null Values**: Deshabilitado (no cachea valores nulos)

**Uso del Caché**:
Los módulos pueden usar caché con la anotación:
```kotlin
@Cacheable("students")
fun findById(id: Long): Student?

@CacheEvict("students", allEntries = true)
fun deleteStudent(id: Long)
```

---

## 📦 Dependencias

### Módulos del Proyecto
```kotlin
implementation(project(":common"))          // Módulo compartido
implementation(project(":security"))        // Seguridad y autenticación
implementation(project(":students"))        // Gestión de estudiantes
implementation(project(":courses"))         // Gestión de cursos
implementation(project(":staff"))           // Gestión de personal
implementation(project(":exams"))           // Gestión de exámenes
implementation(project(":scheduling"))      // Programación de horarios
implementation(project(":payments"))        // Gestión de pagos (en desarrollo)
implementation(project(":communications"))  // Notificaciones (en desarrollo)
implementation(project(":reports"))         // Reportes (en desarrollo)
```

### Spring Boot Starters
```kotlin
spring-boot-starter-web              // REST API
spring-boot-starter-data-jpa         // JPA/Hibernate
spring-boot-starter-security         // Spring Security
spring-boot-starter-actuator         // Métricas y health checks
spring-boot-starter-validation       // Validación de beans
spring-boot-starter-data-redis       // Redis
spring-boot-starter-cache            // Caché abstraction
```

### Bases de Datos
```kotlin
postgresql                           // Driver PostgreSQL
```

### Documentación
```kotlin
springdoc-openapi-starter-webmvc-ui:2.7.0  // Swagger UI
```

### Monitoreo
```kotlin
micrometer-registry-prometheus       // Métricas para Prometheus
```

### Desarrollo
```kotlin
spring-boot-devtools                 // Hot reload y dev tools
```

---

## ⚙️ Configuración (application.yml)

### Configuración Principal

```yaml
spring:
  application:
    name: sigep-backend

  # PostgreSQL
  datasource:
    url: jdbc:postgresql://localhost:5432/sigep_db
    username: sigep_user
    password: sigep_password
    driver-class-name: org.postgresql.Driver

  # JPA/Hibernate
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update  # Cambiar a 'validate' en producción
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true

  # Redis
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 60000

  cache:
    type: redis
    redis:
      time-to-live: 600000  # 10 minutos

# Server
server:
  port: 8080
  servlet:
    context-path: /

# JWT
jwt:
  secret: mySecretKeyForJWTTokenGenerationShouldBeAtLeast256BitsLongForHS512Algorithm
  expiration: 86400000       # 24 horas
  refresh-expiration: 604800000  # 7 días

# CORS
app:
  cors:
    allowed-origins: http://localhost:4200,https://sigep.edu.mx

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

---

## 🚀 Ejecución

### Compilar el módulo
```bash
gradlew :application:build
```

### Ejecutar la aplicación
```bash
# Opción 1: Con Gradle
gradlew :application:bootRun

# Opción 2: Con JAR
java -jar application/build/libs/sigep-backend.jar

# Opción 3: Con perfil específico
gradlew :application:bootRun --args='--spring.profiles.active=dev'
```

### Generar JAR ejecutable
```bash
gradlew :application:bootJar
```

**Resultado**: `application/build/libs/sigep-backend.jar`

---

## 📊 Endpoints de Monitoreo

### Actuator Endpoints

| Endpoint | Descripción | Autenticación |
|----------|-------------|---------------|
| `/actuator/health` | Estado de salud de la aplicación | No |
| `/actuator/info` | Información de la aplicación | No |
| `/actuator/metrics` | Métricas de la aplicación | Sí (ADMIN) |
| `/actuator/prometheus` | Métricas en formato Prometheus | Sí (ADMIN) |

**Ejemplo de Health Check**:
```bash
curl http://localhost:8080/actuator/health
```

**Respuesta**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.0"
      }
    }
  }
}
```

---

## 🔍 Integración de Módulos

### Component Scan
La aplicación escanea automáticamente todos los paquetes bajo `com.sigep`:

```
com.sigep.application.*
com.sigep.common.*
com.sigep.security.*
com.sigep.students.*
com.sigep.courses.*
com.sigep.staff.*
com.sigep.exams.*
...
```

### Repository Scan
Los repositorios JPA se escanean en:

```
com.sigep.students.domain.repository
com.sigep.courses.domain.repository
com.sigep.staff.infrastructure.repository
com.sigep.security.domain.repository
```

### Entity Scan
Las entidades JPA se escanean en:

```
com.sigep.students.domain.model
com.sigep.courses.domain.model
com.sigep.staff.domain.model
com.sigep.security.domain.model
```

---

## 🔐 Seguridad

La configuración de seguridad se delega al módulo `security`:
- Filtros JWT
- Rate limiting
- CORS
- Autenticación/Autorización

**Ver**: [SECURITY.md](../SECURITY.md)

---

## 🧪 Testing

### Verificar que la aplicación inicia correctamente

```bash
# 1. Iniciar la aplicación
gradlew :application:bootRun

# 2. Verificar health check
curl http://localhost:8080/actuator/health

# 3. Verificar Swagger
# Abrir: http://localhost:8080/swagger-ui/index.html

# 4. Probar autenticación
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}'
```

---

## 📈 Métricas y Monitoreo

### Métricas Disponibles

El módulo expone métricas de:
- **JVM**: Memoria, threads, garbage collection
- **HTTP**: Requests, status codes, latencia
- **Database**: Conexiones, queries
- **Cache**: Hit rate, miss rate
- **Custom**: Métricas de negocio (si se implementan)

### Prometheus Integration

Las métricas están disponibles en formato Prometheus:

```bash
curl http://localhost:8080/actuator/prometheus
```

---

## 🚧 Desarrollo y Hot Reload

El módulo incluye Spring Boot DevTools que proporciona:

- ✅ **Automatic Restart**: Reinicio automático al detectar cambios
- ✅ **LiveReload**: Recarga automática del navegador
- ✅ **Property Defaults**: Valores por defecto para desarrollo
- ✅ **Global Settings**: Configuración global de desarrollo

**Activado automáticamente** en modo desarrollo

---

## 📝 Logs

### Niveles de Log Configurables

```yaml
logging:
  level:
    root: INFO
    com.sigep: DEBUG
    org.springframework.security: DEBUG
    org.hibernate.SQL: DEBUG
```

### Ejemplo de Logs de Inicio

```
2025-11-04 10:00:00.123  INFO --- [main] c.s.application.SigepApplicationKt : Starting SigepApplicationKt
2025-11-04 10:00:01.456  INFO --- [main] o.s.b.w.e.tomcat.TomcatWebServer : Tomcat started on port 8080
2025-11-04 10:00:01.789  INFO --- [main] c.s.application.SigepApplicationKt : Started SigepApplicationKt in 7.5 seconds
```

---

## 🐛 Troubleshooting

### Error: "Port 8080 already in use"

**Solución**:
```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Cambiar puerto en application.yml
server:
  port: 8081
```

### Error: "Could not connect to PostgreSQL"

**Solución**:
- Verificar que PostgreSQL está corriendo
- Verificar credenciales en `application.yml`
- Verificar puerto 5432

### Error: "Could not connect to Redis"

**Solución**:
- Redis es opcional, se puede deshabilitar:
```yaml
spring:
  cache:
    type: none
```

### Swagger no muestra endpoints

**Solución**:
- Verificar que los controladores tienen `@RestController`
- Verificar que están en paquete `com.sigep.*`
- Limpiar y recompilar: `gradlew clean build`

---

## 📚 Referencias

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Spring Cache Abstraction](https://spring.io/guides/gs/caching/)
- [Springdoc OpenAPI](https://springdoc.org/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

**Estado del Módulo**: ✅ Completado y funcionando  
**Versión**: 1.0.0  
**Última actualización**: Noviembre 4, 2025

