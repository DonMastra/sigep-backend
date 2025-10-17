# 🚀 Guía de Inicio Rápido - SiGEP Backend

## 📋 Prerequisitos

Antes de comenzar, asegúrate de tener instalado:

- ✅ JDK 17 o superior
- ✅ Docker y Docker Compose (para PostgreSQL y Redis)
- ✅ Git

## 🏁 Pasos para Iniciar

### 1. Clonar el repositorio (si aplica)

```bash
git clone <repository-url>
cd sigep-backend
```

### 2. Iniciar servicios de infraestructura (PostgreSQL + Redis)

```cmd
docker-compose up -d
```

Esto iniciará:
- PostgreSQL en `localhost:5432`
- Redis en `localhost:6379`
- PgAdmin en `localhost:5050` (admin@sigep.com / admin)
- Redis Commander en `localhost:8081`

### 3. Verificar que los servicios estén corriendo

```cmd
docker-compose ps
```

Deberías ver 4 contenedores en estado "Up".

### 4. Compilar el proyecto

```cmd
gradlew clean build
```

Este comando:
- Descargará todas las dependencias
- Compilará todos los módulos
- Ejecutará los tests

### 5. Ejecutar la aplicación

```cmd
gradlew :application:bootRun
```

O usando el JAR generado:

```cmd
java -jar application\build\libs\sigep-backend.jar
```

### 6. Verificar que la aplicación esté corriendo

Abre tu navegador y accede a:

- 🏥 Health Check: http://localhost:8080/actuator/health
- 📚 Swagger UI: http://localhost:8080/swagger-ui.html
- 📄 API Docs: http://localhost:8080/v3/api-docs

## 🧪 Probar la API

### 1. Registrar un usuario administrador

```bash
curl -X POST http://localhost:8080/api/v1/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"admin\",\"email\":\"admin@sigep.com\",\"password\":\"admin123\",\"firstName\":\"Admin\",\"lastName\":\"Sistema\",\"role\":\"ADMIN\"}"
```

### 2. Iniciar sesión

```bash
curl -X POST http://localhost:8080/api/v1/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"admin\",\"password\":\"admin123\"}"
```

Guarda el `token` de la respuesta.

### 3. Crear un estudiante

```bash
curl -X POST http://localhost:8080/api/v1/students ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer TU_TOKEN_AQUI" ^
  -d "{\"firstName\":\"Juan\",\"lastName\":\"Pérez\",\"email\":\"juan@example.com\",\"phone\":\"+525512345678\",\"dateOfBirth\":\"2005-01-15\",\"address\":\"Calle Principal 123\",\"guardianId\":1,\"currentLevel\":\"Beginner\"}"
```

### 4. Listar estudiantes

```bash
curl -X GET "http://localhost:8080/api/v1/students?page=0&limit=10" ^
  -H "Authorization: Bearer TU_TOKEN_AQUI"
```

## 🛠️ Comandos Útiles

### Desarrollo

```cmd
REM Compilar sin tests
gradlew build -x test

REM Ejecutar tests
gradlew test

REM Ejecutar tests de un módulo específico
gradlew :students:test

REM Limpiar y recompilar
gradlew clean build
```

### Docker

```cmd
REM Iniciar servicios
docker-compose up -d

REM Detener servicios
docker-compose down

REM Ver logs
docker-compose logs -f

REM Reiniciar servicios
docker-compose restart
```

### Base de Datos

```cmd
REM Conectar a PostgreSQL
docker exec -it sigep-postgres psql -U sigep_user -d sigep_db

REM Backup de la base de datos
docker exec -t sigep-postgres pg_dump -U sigep_user sigep_db > backup.sql

REM Restaurar base de datos
docker exec -i sigep-postgres psql -U sigep_user sigep_db < backup.sql
```

## 📊 Monitoreo

Una vez que la aplicación esté corriendo:

- **Métricas**: http://localhost:8080/actuator/metrics
- **Prometheus**: http://localhost:8080/actuator/prometheus
- **PgAdmin**: http://localhost:5050 (admin@sigep.com / admin)
- **Redis Commander**: http://localhost:8081

## 🐛 Solución de Problemas

### La aplicación no inicia

1. Verifica que PostgreSQL y Redis estén corriendo:
   ```cmd
   docker-compose ps
   ```

2. Verifica los logs:
   ```cmd
   docker-compose logs postgres
   docker-compose logs redis
   ```

### Error de conexión a la base de datos

Verifica la configuración en `application/src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/sigep_db
spring.datasource.username=sigep_user
spring.datasource.password=sigep_password
```

### Puerto 8080 ya está en uso

Cambia el puerto en `application.properties`:

```properties
server.port=8081
```

### Problemas con Gradle

```cmd
REM Limpiar cache de Gradle
gradlew clean --refresh-dependencies

REM O borrar la carpeta .gradle
rmdir /s /q .gradle
gradlew build
```

## 📚 Próximos Pasos

1. ✅ Explorar la documentación de la API en Swagger
2. ✅ Revisar los archivos README.md y ARCHITECTURE.md
3. ✅ Implementar los módulos restantes (courses, payments, etc.)
4. ✅ Configurar perfiles de Spring (dev, prod)
5. ✅ Implementar tests de integración

## 🆘 Soporte

Para problemas o dudas:
- Revisa la documentación en README.md
- Revisa la arquitectura en ARCHITECTURE.md
- Consulta los logs de la aplicación

---

**¡Listo! Tu backend está corriendo en http://localhost:8080** 🎉

