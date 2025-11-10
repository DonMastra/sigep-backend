# 📚 Índice de Documentación - SiGEP Backend

**Sistema de Gestión de Enseñanza Privada**  
*Backend API REST - Spring Boot + Kotlin*

---

## 🎯 Inicio Rápido

¿Primera vez con el proyecto? Comienza aquí:

1. **[README.md](README.md)** - Introducción general y guía de inicio
2. **[QUICKSTART.md](QUICKSTART.md)** - Guía rápida para levantar el proyecto
3. **[AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md)** - Cómo autenticarse y probar la API

---

## 📖 Documentación Principal

### 🏗️ Arquitectura y Diseño

| Documento | Descripción | Audiencia |
|-----------|-------------|-----------|
| **[ARCHITECTURE.md](ARCHITECTURE.md)** | Arquitectura completa del sistema, patrones DDD, bounded contexts | Arquitectos, Desarrolladores |
| **[DIAGRAMS.md](DIAGRAMS.md)** | Diagramas de arquitectura, flujos y relaciones entre módulos | Todos |

### 🔐 Seguridad

| Documento | Descripción | Audiencia |
|-----------|-------------|-----------|
| **[SECURITY.md](SECURITY.md)** | Documentación completa del módulo de seguridad, JWT, roles, rate limiting | Desarrolladores, Auditores |
| **[AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md)** | Guía práctica de autenticación, obtención de tokens, testing | Desarrolladores Frontend/Backend |

### 🔌 API y Contratos

| Documento | Descripción | Audiencia |
|-----------|-------------|-----------|
| **[API_CONTRACT.md](API_CONTRACT.md)** | Contrato completo de API para integración frontend (TypeScript interfaces, ejemplos) | Desarrolladores Frontend |
| **[Swagger UI](http://localhost:8080/swagger-ui/index.html)** | Documentación interactiva en vivo de todos los endpoints | Todos (requiere app corriendo) |

### 📝 Implementación

| Documento | Descripción | Audiencia |
|-----------|-------------|-----------|
| **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** | Resumen de implementación de funcionalidades | Desarrolladores, Project Managers |
| **[QUICKSTART.md](QUICKSTART.md)** | Guía rápida de configuración e inicio | Nuevos Desarrolladores |

---

## 📦 Documentación por Módulo

### 🔧 Common (Módulo Compartido)
- **Estado**: ✅ Completado y estable
- **Ubicación**: `common/`
- **Documentación**: [common/README.md](common/README.md)
- **Funcionalidades**:
  - DTOs compartidos (ApiResponse, PageResponse)
  - Excepciones personalizadas con mapeo a HTTP status
  - Abstracciones DDD (AggregateRoot, ValueObject)
  - Sistema de auditoría JPA automático
  - Global Exception Handler
  - Configuración base para todos los módulos

**Importancia**: Este es el módulo fundamental del que dependen todos los demás. No tiene dependencias con otros módulos del proyecto.

---

### 🚀 Application (Módulo Principal)
- **Estado**: ✅ Completado y funcionando
- **Ubicación**: `application/`
- **Documentación**: [application/README.md](application/README.md)
- **Funcionalidades**:
  - Punto de entrada de la aplicación (main)
  - Integración de todos los bounded contexts
  - Configuración OpenAPI/Swagger
  - Configuración Redis para caché
  - Actuator para health checks y métricas
  - Component scanning y JPA configuration
  - Hot reload con DevTools

**Swagger UI**: http://localhost:8080/swagger-ui/index.html

---

### 🔐 Security (Seguridad)
- **Estado**: ✅ Completado
- **Ubicación**: `security/`
- **Documentación**: [SECURITY.md](SECURITY.md)
- **Funcionalidades**:
  - Autenticación JWT (access + refresh tokens)
  - Sistema de roles: ADMIN, TEACHER, GUARDIAN
  - Rate limiting
  - Anotaciones de seguridad personalizadas

### 👥 Students (Estudiantes)
- **Estado**: ✅ Completado
- **Ubicación**: `students/`
- **Documentación**: Ver [API_CONTRACT.md](API_CONTRACT.md#-students-endpoints)
- **Funcionalidades**:
  - CRUD de estudiantes
  - Historial de cursos
  - Relación con tutores
  - Búsqueda y paginación

### 📚 Courses (Cursos)
- **Estado**: ✅ Completado
- **Ubicación**: `courses/`
- **Documentación**: 
  - [courses/README.md](courses/README.md)
  - [API_CONTRACT.md](API_CONTRACT.md#-courses-endpoints)
- **Funcionalidades**:
  - CRUD de cursos
  - Inscripción de estudiantes
  - Materiales del curso
  - Sesiones programadas
  - Sistema de asistencia
  - Certificados

### 📝 Exams (Exámenes)
- **Estado**: ✅ Completado
- **Ubicación**: `exams/`
- **Documentación**: 
  - [exams/README.md](exams/README.md)
  - [exams/EXECUTIVE_SUMMARY.md](exams/EXECUTIVE_SUMMARY.md)
  - [exams/TEACHER_PERFORMANCE.md](exams/TEACHER_PERFORMANCE.md)
  - [API_CONTRACT.md](API_CONTRACT.md#-exams-endpoints)
- **Funcionalidades**:
  - CRUD de exámenes
  - Tipos de examen (escrito, oral, práctico, final)
  - Calificaciones por estudiante
  - Análisis de rendimiento de docentes
  - Estadísticas y métricas

### 👔 Staff (Personal)
- **Estado**: ✅ Completado
- **Ubicación**: `staff/`
- **Documentación**: 
  - [staff/README.md](staff/README.md)
  - [API_CONTRACT.md](API_CONTRACT.md#-staff-endpoints)
- **Funcionalidades**:
  - Gestión de personal docente
  - Gestión de personal no docente
  - Control de asistencia
  - Notas y observaciones

### 💳 Payments (Pagos)
- **Estado**: 🚧 En desarrollo
- **Ubicación**: `payments/`
- **Funcionalidades planificadas**:
  - Pagos de estudiantes
  - Pagos a personal
  - Historial de transacciones

### 🔔 Communications (Comunicaciones)
- **Estado**: 🚧 En desarrollo
- **Ubicación**: `communications/`
- **Funcionalidades planificadas**:
  - Notificaciones de materiales
  - Notificaciones de exámenes
  - Recordatorios

### 📊 Reports (Reportes)
- **Estado**: 🚧 En desarrollo
- **Ubicación**: `reports/`
- **Funcionalidades planificadas**:
  - Reportes administrativos
  - Exportación de datos
  - Estadísticas generales

---

## 🛠️ Guías de Desarrollo

### Para Nuevos Desarrolladores

1. **Configuración del Entorno**
   - [QUICKSTART.md](QUICKSTART.md) - Setup inicial
   - [README.md](README.md#-configuración) - Requisitos y configuración

2. **Entender la Arquitectura**
   - [ARCHITECTURE.md](ARCHITECTURE.md) - Patrones y estructura
   - [DIAGRAMS.md](DIAGRAMS.md) - Diagramas visuales

3. **Trabajar con Seguridad**
   - [SECURITY.md](SECURITY.md) - Sistema de seguridad
   - [AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md) - Testing con tokens

### Para Desarrolladores Frontend

1. **Integración con API**
   - [API_CONTRACT.md](API_CONTRACT.md) - Contrato completo con TypeScript interfaces
   - [AUTHENTICATION_GUIDE.md](AUTHENTICATION_GUIDE.md) - Flujo de autenticación
   - [Swagger UI](http://localhost:8080/swagger-ui/index.html) - Probar endpoints

2. **Ejemplos de Integración**
   - Ver sección "Notas para Integración Frontend" en [API_CONTRACT.md](API_CONTRACT.md#-notas-para-integración-frontend)

### Para Arquitectos y Tech Leads

1. **Arquitectura del Sistema**
   - [ARCHITECTURE.md](ARCHITECTURE.md) - Diseño completo
   - [DIAGRAMS.md](DIAGRAMS.md) - Diagramas de componentes

2. **Seguridad y Compliance**
   - [SECURITY.md](SECURITY.md) - Medidas de seguridad implementadas

3. **Roadmap**
   - [README.md](README.md#-roadmap) - Funcionalidades actuales y planificadas

---

## 📋 Recursos Útiles

### Scripts y Utilidades

| Script | Descripción | Uso |
|--------|-------------|-----|
| `setup-env.bat` | Configurar variables de entorno | `.\setup-env.bat` |
| `start.bat` | Iniciar aplicación (Windows) | `.\start.bat` |
| `stop.bat` | Detener aplicación | `.\stop.bat` |
| `scripts/setup-database.sql` | Script de setup de base de datos | SQL |
| `scripts/seed-test-users.sql` | Usuarios de prueba | SQL |

### Docker

| Archivo | Descripción |
|---------|-------------|
| `docker-compose.yml` | Configuración de PostgreSQL y Redis |

### Gradle

| Comando | Descripción |
|---------|-------------|
| `gradlew clean build` | Compilar proyecto |
| `gradlew :application:bootRun` | Ejecutar aplicación |
| `gradlew test` | Ejecutar tests |

---
| Backend API | ✅ Producción | 1.0.0 | 2025-11-04 |
| Módulo Common | ✅ Completado | 1.0.0 | 2025-11-04 |
| Módulo Application | ✅ Completado | 1.0.0 | 2025-11-04 |
## 🔍 Búsqueda Rápida

### Por Funcionalidad

- **Autenticación**: [SECURITY.md](SECURITY.md#-autenticación)
- **Roles y Permisos**: [SECURITY.md](SECURITY.md#️-autorización)
- **JWT Tokens**: [SECURITY.md](SECURITY.md#-jwt-tokens)
- **Rate Limiting**: [SECURITY.md](SECURITY.md#-rate-limiting)
| Documentación | ✅ Actualizada | 1.0.0 | 2025-11-04 |
- **Estudiantes API**: [API_CONTRACT.md](API_CONTRACT.md#-students-endpoints)
- **Cursos API**: [API_CONTRACT.md](API_CONTRACT.md#-courses-endpoints)
- **Exámenes API**: [API_CONTRACT.md](API_CONTRACT.md#-exams-endpoints)
- **Personal API**: [API_CONTRACT.md](API_CONTRACT.md#-staff-endpoints)

### Por Rol de Usuario

- **Administrador**: Ver todos los endpoints con rol "ADMIN" en [API_CONTRACT.md](API_CONTRACT.md)
- **Profesor**: Ver endpoints con rol "TEACHER" en [API_CONTRACT.md](API_CONTRACT.md)
- **Tutor**: Ver endpoints con rol "GUARDIAN" en [API_CONTRACT.md](API_CONTRACT.md)

### Por Error/Problema

- **Errores de Autenticación**: [SECURITY.md](SECURITY.md#️-manejo-de-errores-de-seguridad)
- **Troubleshooting**: [README.md](README.md#-troubleshooting)
- **Códigos de Error HTTP**: [API_CONTRACT.md](API_CONTRACT.md#️-error-codes)

---

## 📊 Estado del Proyecto

| Componente | Estado | Versión | Última Actualización |
|------------|--------|---------|---------------------|
| Backend API | ✅ Producción | 1.0.0 | 2025-11-03 |
| Módulo Security | ✅ Completado | 1.0.0 | 2025-11-03 |
| Módulo Students | ✅ Completado | 1.0.0 | 2025-10-22 |
| Módulo Courses | ✅ Completado | 1.0.0 | 2025-10-22 |
| Módulo Exams | ✅ Completado | 1.0.0 | 2025-10-22 |
| Módulo Staff | ✅ Completado | 1.0.0 | 2025-10-22 |
| Módulo Payments | 🚧 En desarrollo | 0.1.0 | - |
| Módulo Communications | 🚧 En desarrollo | 0.1.0 | - |
| Módulo Reports | 🚧 En desarrollo | 0.1.0 | - |
| Documentación | ✅ Actualizada | 1.0.0 | 2025-11-03 |

---

## 📞 Ayuda y Soporte

**¿No encuentras lo que buscas?**

1. Revisa la [Documentación Swagger](http://localhost:8080/swagger-ui/index.html) para ver todos los endpoints disponibles
2. Consulta [API_CONTRACT.md](API_CONTRACT.md) para ver el contrato completo de API
3. Revisa [TROUBLESHOOTING](README.md#-troubleshooting) para soluciones a problemas comunes
4. Contacta al equipo de desarrollo: dev@sigep.edu.mx

---

**Última actualización de este índice**: Noviembre 3, 2025  
**Mantenedor**: Equipo SiGEP Backend

