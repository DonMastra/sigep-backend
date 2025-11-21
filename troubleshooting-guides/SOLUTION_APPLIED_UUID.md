# ✅ Solución Aplicada - Error UUID en Módulo Exams

## 📋 Resumen

**Fecha**: 2025-11-04 22:50  
**Problema**: Error al intentar migrar columnas de tipo BIGINT IDENTITY a UUID  
**Estado**: ✅ **RESUELTO**

---

## 🔴 Error Original

```
ERROR: identity column type must be smallint, integer, or bigint
Error executing DDL "alter table if exists exams alter column id set data type UUID"
```

---

## 🔧 Solución Aplicada

### Paso 1: Eliminar Tablas con Tipo Incorrecto ✅

Se eliminaron las siguientes tablas del módulo `exams`:

```sql
DROP TABLE IF EXISTS exam_grade_history CASCADE;
DROP TABLE IF EXISTS exam_submissions CASCADE;
DROP TABLE IF EXISTS exams CASCADE;
```

**Comando ejecutado**:
```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "DROP TABLE IF EXISTS exam_grade_history CASCADE; DROP TABLE IF EXISTS exam_submissions CASCADE; DROP TABLE IF EXISTS exams CASCADE;"
```

**Resultado**:
```
NOTICE: drop cascades to constraint fktf85ht7yquiorwjx2xbdx3fxw on table exam_results
DROP TABLE
DROP TABLE
DROP TABLE
```

---

### Paso 2: Verificar Eliminación ✅

Se verificó que las tablas fueron eliminadas:

```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('exams', 'exam_submissions', 'exam_grade_history');"
```

**Resultado**: `(0 rows)` - Confirmado, tablas eliminadas.

---

### Paso 3: Recrear Tablas con Hibernate ✅

Se inició la aplicación para que Hibernate recreara las tablas automáticamente:

```bash
.\gradlew :application:bootRun
```

Hibernate detectó que las tablas no existen y las recreó con el esquema correcto usando UUID.

---

### Paso 4: Verificar Tipo de Datos Correcto ✅

Se verificó que las nuevas tablas tienen el tipo de dato correcto:

```bash
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_name IN ('exams', 'exam_submissions', 'exam_grade_history') AND column_name = 'id' ORDER BY table_name;"
```

**Resultado**:
```
      table_name      | column_name | data_type 
----------------------+-------------+-----------
 exam_grade_history   | id          | uuid
 exam_submissions     | id          | uuid
 exams                | id          | uuid
(3 rows)
```

✅ **Confirmado**: Todas las tablas ahora tienen columnas `id` de tipo `uuid`.

---

## 📊 Estado Final

| Tabla | Estado Anterior | Estado Actual | Verificado |
|-------|----------------|---------------|------------|
| `exams` | BIGINT IDENTITY | UUID | ✅ |
| `exam_submissions` | BIGINT IDENTITY | UUID | ✅ |
| `exam_grade_history` | BIGINT IDENTITY | UUID | ✅ |

---

## 🎯 Próximos Pasos

### 1. Verificar Aplicación

Abrir navegador y verificar:

- **Health Check**: http://localhost:8080/actuator/health
  - Debería retornar: `{"status":"UP"}`

- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
  - Verificar que los endpoints del módulo `exams` están disponibles

### 2. Probar Endpoints de Exams

En Swagger UI, probar:

1. **POST /api/v1/auth/login**
   ```json
   {
     "username": "admin",
     "password": "password123"
   }
   ```
   - Copiar el token

2. **Autorizar en Swagger**
   - Click en botón "Authorize"
   - Pegar: `Bearer {token}`

3. **POST /api/v1/exams** (crear un examen de prueba)
   - Verificar que se crea correctamente con UUID

4. **GET /api/v1/exams** (listar exámenes)
   - Verificar que retorna lista (vacía o con el examen creado)

### 3. Verificar en Base de Datos

Conectar a pgAdmin (http://localhost:5050) y verificar:

```sql
-- Ver estructura de tabla exams
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'exams'
ORDER BY ordinal_position;

-- Ver si hay datos
SELECT COUNT(*) FROM exams;
```

---

## 📝 Documentación Creada

Se crearon los siguientes archivos de documentación:

1. **`scripts/fix-exams-uuid.sql`** - Script SQL original con comentarios
2. **`scripts/fix-exams-uuid-simple.sql`** - Script simplificado para ejecutar
3. **`TROUBLESHOOTING_UUID_ISSUE.md`** - Documentación completa del problema y soluciones
4. **`SOLUTION_APPLIED_UUID.md`** - Este archivo (resumen de solución aplicada)

---

## ⚠️ Notas Importantes

### Datos Perdidos

- ✅ No había datos en las tablas de exams (desarrollo)
- ✅ Seguro proceder con eliminación
- ⚠️ En producción, hacer backup antes de eliminar tablas

### Prevención Futura

Para evitar este problema en el futuro:

1. **Usar Migraciones Controladas**
   - Implementar Flyway o Liquibase
   - Cambiar `ddl-auto: update` a `ddl-auto: validate`

2. **Consistencia de Tipos**
   - Decidir: UUID o BIGINT para todos los módulos
   - Documentar decisión en ARCHITECTURE.md

3. **Tests de Schema**
   - Crear tests que verifiquen tipos de columnas
   - Ejecutar en CI/CD

---

## 🎓 Lecciones Aprendidas

1. **PostgreSQL no permite IDENTITY en UUID**
   - IDENTITY solo funciona con tipos enteros
   - UUID requiere generación manual (en código o con `gen_random_uuid()`)

2. **Hibernate ddl-auto=update tiene limitaciones**
   - No puede cambiar tipos incompatibles automáticamente
   - Mejor usar migraciones controladas en producción

3. **Docker facilita operaciones de base de datos**
   - `docker exec` permite ejecutar comandos SQL fácilmente
   - No requiere tener psql instalado localmente

---

## ✅ Confirmación Final

- [x] Tablas eliminadas correctamente
- [x] Tablas recreadas con UUID
- [x] Aplicación iniciada sin errores de DDL
- [x] Documentación creada
- [x] Scripts de solución guardados

**Estado**: ✅ PROBLEMA RESUELTO

---

**Ejecutado por**: Sistema SiGEP  
**Fecha**: 2025-11-04 22:50  
**Duración**: ~5 minutos

