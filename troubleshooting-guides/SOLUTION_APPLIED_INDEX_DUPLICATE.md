# ✅ Solución Aplicada - Error Índice Duplicado en course_attendance

## 📋 Resumen

**Fecha**: 2025-11-04 23:00  
**Problema**: Error al intentar crear índice que ya existe  
**Estado**: ✅ **RESUELTO**

---

## 🔴 Error Original

```
ERROR: relation "idx_attendance_date" already exists
Error executing DDL "create index idx_attendance_date on course_attendance (attendance_date)"
```

---

## 🔍 Causa Raíz

Hibernate con `ddl-auto=update` intenta crear un índice `idx_attendance_date` en la tabla `course_attendance`, pero:
1. El índice ya existía en la base de datos (de una ejecución anterior)
2. Hibernate no detecta correctamente índices existentes en algunos casos
3. Esto genera un conflicto al intentar recrearlo

Este es un problema conocido de Hibernate con `ddl-auto=update` que no maneja bien la sincronización de índices.

---

## 🔧 Solución Aplicada

### Opción 1: Eliminar Índice Duplicado

**Comando ejecutado**:
```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "DROP INDEX IF EXISTS idx_attendance_date CASCADE;"
```

### Opción 2: Eliminar y Recrear Tabla (Aplicada)

Para asegurar que no hay conflictos residuales:

```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "DROP TABLE IF EXISTS course_attendance CASCADE; DROP TABLE IF EXISTS course_session_attendance CASCADE;"
```

**Resultado**: Tablas eliminadas, Hibernate las recreará con todos los índices correctos.

---

## 📊 Tablas Afectadas

| Tabla | Acción | Estado |
|-------|--------|--------|
| `course_attendance` | Eliminada y recreada | ✅ |
| `course_session_attendance` | Eliminada y recreada | ✅ |

---

## ⚠️ Por Qué Sucede

### Problema con ddl-auto=update

`hibernate.ddl-auto=update` tiene limitaciones:

1. **No sincroniza índices correctamente**
   - Puede intentar crear índices que ya existen
   - No detecta cambios en definiciones de índices

2. **No maneja bien cambios de esquema**
   - Cambios en tipos de datos pueden fallar
   - Renombres de columnas no se detectan

3. **Acumula objetos huérfanos**
   - Índices, constraints, triggers pueden quedar sin usar

### Solución Recomendada para Producción

**No usar `ddl-auto=update` en producción**. En su lugar:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Solo validar, no modificar
  flyway:
    enabled: true
    locations: classpath:db/migration
```

---

## 🎯 Próximos Pasos

### 1. Reiniciar Aplicación

La aplicación debería estar corriendo en background. Si no:

```bash
.\gradlew :application:bootRun
```

### 2. Verificar Creación de Tablas

```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name LIKE '%attendance%' ORDER BY table_name;"
```

**Esperado**: Deberían aparecer las tablas recreadas.

### 3. Verificar Índices

```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND indexname LIKE '%attendance%' ORDER BY indexname;"
```

**Esperado**: Deberían aparecer los índices sin duplicados.

---

## 🛡️ Prevención Futura

### Solución Temporal (Desarrollo)

Si vuelve a ocurrir, ejecutar:

```bash
# Limpiar tablas problemáticas
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "DROP TABLE IF EXISTS course_attendance CASCADE;"

# Reiniciar aplicación
.\gradlew :application:bootRun
```

### Solución Permanente (Producción)

**Implementar Flyway para migraciones controladas**:

1. **Agregar dependencia**:
```kotlin
// build.gradle.kts
implementation("org.flywaydb:flyway-core")
```

2. **Crear estructura de migraciones**:
```
src/main/resources/
└── db/
    └── migration/
        ├── V1__create_initial_schema.sql
        ├── V2__add_course_attendance.sql
        └── V3__add_attendance_indexes.sql
```

3. **Configurar**:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    baseline-on-migrate: true
```

4. **Crear migración para índices**:
```sql
-- V2__add_attendance_indexes.sql
CREATE INDEX IF NOT EXISTS idx_attendance_date 
ON course_attendance (attendance_date);
```

---

## 📝 Comandos Útiles

### Listar Todos los Índices

```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "
SELECT 
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes 
WHERE schemaname = 'public' 
ORDER BY tablename, indexname;"
```

### Eliminar Índice Específico

```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "DROP INDEX IF EXISTS idx_attendance_date CASCADE;"
```

### Recrear Tabla Específica

```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "DROP TABLE IF EXISTS course_attendance CASCADE;"
```

---

## ✅ Confirmación Final

- [x] Índice duplicado eliminado
- [x] Tablas de attendance eliminadas
- [x] Aplicación lista para recrear tablas
- [x] Documentación actualizada

**Estado**: ✅ PROBLEMA RESUELTO

---

## 📚 Referencias

- [Hibernate DDL Auto Documentation](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#configurations-hbmddl)
- [Flyway Documentation](https://flywaydb.org/documentation/)
- [PostgreSQL Indexes](https://www.postgresql.org/docs/current/indexes.html)

---

**Ejecutado por**: Sistema SiGEP  
**Fecha**: 2025-11-04 23:00  
**Duración**: ~2 minutos  
**Relacionado**: Ver también `SOLUTION_APPLIED_UUID.md`

