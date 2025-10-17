# 📚 Índice de Documentación - SiGEP Backend

Bienvenido a la documentación del backend de SiGEP. Esta guía te ayudará a navegar por todos los recursos disponibles.

## 🚀 Para Empezar

1. **[QUICKSTART.md](QUICKSTART.md)** ⭐ **¡EMPIEZA AQUÍ!**
   - Guía paso a paso para iniciar el proyecto
   - Configuración inicial
   - Primeros pasos con la API

2. **[README.md](README.md)**
   - Descripción general del proyecto
   - Tecnologías utilizadas
   - Comandos útiles
   - Endpoints principales

## 🏗️ Arquitectura

3. **[ARCHITECTURE.md](ARCHITECTURE.md)**
   - Estructura completa de módulos
   - Capas DDD (Domain, Application, Infrastructure, Presentation)
   - Dependencias entre módulos
   - Estrategia de migración a microservicios

4. **[DIAGRAMS.md](DIAGRAMS.md)**
   - Diagramas visuales de la arquitectura
   - Flujo de requests
   - Modelo de base de datos
   - Comunicación entre módulos

5. **[SUMMARY.md](SUMMARY.md)**
   - Resumen ejecutivo de todos los módulos
   - Características implementadas
   - Tecnologías y versiones
   - Roadmap

## 📦 Módulos Implementados

### Core Modules

- **common/** - Utilidades compartidas, excepciones, DTOs base
- **security/** - Autenticación JWT, autorización por roles
- **application/** - Módulo principal orquestador

### Bounded Contexts (DDD)

- **students/** - Gestión completa de estudiantes ✅ **IMPLEMENTADO**
- **courses/** - Gestión de cursos y horarios (estructura base)
- **scheduling/** - Programación de calendarios (estructura base)
- **payments/** - Gestión de pagos (estructura base)
- **exams/** - Exámenes y resultados (estructura base)
- **communications/** - Notificaciones (estructura base)
- **reports/** - Generación de reportes (estructura base)

## 🔧 Configuración

### Archivos de Configuración

- **build.gradle.kts** (raíz) - Configuración multi-módulo
- **settings.gradle.kts** - Definición de módulos
- **docker-compose.yml** - PostgreSQL + Redis + herramientas
- **application.properties** - Configuración de Spring Boot

### Scripts

- **start.bat** - Inicia servicios Docker + aplicación (Windows)
- **stop.bat** - Detiene servicios Docker (Windows)
- **scripts/setup-database.sql** - Script de inicialización de BD

## 📖 Guías por Rol

### Para Desarrolladores Backend

1. Leer **QUICKSTART.md** para configuración inicial
2. Revisar **ARCHITECTURE.md** para entender la estructura
3. Estudiar el módulo **students** como ejemplo completo de DDD
4. Implementar módulos restantes siguiendo el mismo patrón

### Para DevOps

1. Revisar **docker-compose.yml** para servicios de infraestructura
2. Configurar **application.properties** para diferentes ambientes
3. Configurar CI/CD basado en Gradle
4. Monitorear con Actuator endpoints

### Para Frontend Developers

1. Revisar **DIAGRAMS.md** para entender los endpoints
2. Acceder a **Swagger UI** en `http://localhost:8080/swagger-ui.html`
3. Probar endpoints con la colección de Postman (pendiente)
4. Referencia al README.md del frontend para contrato de API

## 🧪 Testing

```bash
# Ejecutar todos los tests
gradlew test

# Tests de un módulo específico
gradlew :students:test

# Tests con coverage
gradlew test jacocoTestReport
```

## 📊 Monitoreo

Una vez iniciada la aplicación:

- **API Docs**: http://localhost:8080/swagger-ui.html
- **Health**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/metrics
- **PgAdmin**: http://localhost:5050 (admin@sigep.com / admin)
- **Redis Commander**: http://localhost:8081

## 🔐 Seguridad

### Autenticación

Todos los endpoints (excepto `/api/v1/auth/*`) requieren JWT token:

```
Authorization: Bearer <token>
```

### Roles

- **ADMIN** - Acceso completo
- **TEACHER** - Acceso a recursos educativos
- **GUARDIAN** - Acceso limitado a sus estudiantes

## 📝 Convenciones de Código

### Estructura de Paquetes

```
com.sigep.{module}.{layer}.{component}

Ejemplos:
- com.sigep.students.domain.model.Student
- com.sigep.students.application.service.StudentService
- com.sigep.students.presentation.controller.StudentController
```

### Nombres de Clases

- **Entities**: Sustantivos singulares (Student, Course, Payment)
- **Services**: {Entity}Service
- **Controllers**: {Entity}Controller
- **Repositories**: {Entity}Repository
- **DTOs**: {Entity}Dto, Create{Entity}Request, Update{Entity}Request

## 🚦 Estado del Proyecto

| Módulo | Estado | Documentación |
|--------|--------|---------------|
| common | ✅ Completo | - |
| security | ✅ Completo | JWT, Roles |
| students | ✅ Completo | CRUD completo |
| courses | 🟡 Estructura base | Pendiente servicios |
| scheduling | 🟡 Estructura base | Pendiente servicios |
| payments | 🟡 Estructura base | Pendiente servicios |
| exams | 🟡 Estructura base | Pendiente servicios |
| communications | 🟡 Estructura base | Pendiente servicios |
| reports | 🟡 Estructura base | Pendiente servicios |
| application | ✅ Completo | Orquestador |

## 🎯 Próximos Pasos

1. ✅ **Completar módulos restantes** - Implementar servicios y controladores
2. ⬜ **Tests de integración** - TestContainers
3. ⬜ **CI/CD** - GitHub Actions
4. ⬜ **Postman Collection** - Documentación interactiva
5. ⬜ **WebSockets** - Notificaciones en tiempo real
6. ⬜ **File Upload** - Documentos y fotos
7. ⬜ **Audit Logging** - Trazabilidad de operaciones
8. ⬜ **Rate Limiting** - Protección contra abuso

## 📞 Recursos Adicionales

### Documentación Externa

- [Spring Boot Docs](https://spring.io/projects/spring-boot)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Spring Security](https://spring.io/projects/spring-security)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Redis](https://redis.io/documentation)
- [PostgreSQL](https://www.postgresql.org/docs/)

### Herramientas Recomendadas

- **IDE**: IntelliJ IDEA
- **API Testing**: Postman, Insomnia
- **Database**: DBeaver, pgAdmin
- **Git**: GitHub Desktop, SourceTree
- **Docker**: Docker Desktop

## 📄 Archivos de Documentación

```
sigep-backend/
├── README.md                    # Documentación principal
├── QUICKSTART.md               # Guía de inicio rápido ⭐
├── ARCHITECTURE.md             # Arquitectura detallada
├── DIAGRAMS.md                 # Diagramas visuales
├── SUMMARY.md                  # Resumen ejecutivo
├── INDEX.md                    # Este archivo
├── .gitignore                  # Archivos ignorados por Git
├── docker-compose.yml          # Servicios Docker
├── start.bat / stop.bat        # Scripts de inicio/parada
└── scripts/
    └── setup-database.sql      # Inicialización de BD
```

## 🤝 Contribuciones

Para contribuir al proyecto:

1. Seguir las convenciones de código establecidas
2. Crear tests para nuevas funcionalidades
3. Actualizar documentación relevante
4. Usar Conventional Commits (feat:, fix:, docs:, etc.)

## 📧 Soporte

Para dudas o problemas:

1. Revisar esta documentación
2. Consultar los logs de la aplicación
3. Revisar issues conocidos
4. Contactar al equipo de desarrollo

---

**¡Comienza con QUICKSTART.md y estarás listo en minutos!** 🚀

**Última actualización**: Octubre 2025

