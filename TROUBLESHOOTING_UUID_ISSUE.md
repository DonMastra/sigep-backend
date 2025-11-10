# Solución al Error: UUID vs IDENTITY en PostgreSQL

## 🔴 Problema

Al ejecutar la aplicación, Hibernate arroja el siguiente error:

```
ERROR: identity column type must be smallint, integer, or bigint
Error executing DDL "alter table if exists exams alter column id set data type UUID"
```

## 🔍 Causa Raíz

El módulo **exams** fue desarrollado usando `UUID` como tipo de identificador:

```kotlin
@Entity
@Table(name = "exams")
data class Exam(
    @Id
    @Column(columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),
    // ...
)
```

Sin embargo, las tablas en la base de datos fueron creadas previamente con el tipo `BIGINT` con `IDENTITY` (auto-incremental).

Cuando Hibernate intenta migrar el esquema con `ddl-auto=update`, intenta cambiar el tipo de columna:
- De: `BIGINT IDENTITY` 
- A: `UUID`

**PostgreSQL no permite** que una columna `IDENTITY` sea de tipo UUID porque solo acepta tipos enteros (smallint, integer, bigint).

## 📋 Tablas Afectadas

Las siguientes tablas del módulo `exams` tienen este problema:

1. `exams`
2. `exam_submissions`
3. `exam_grade_history`

## ✅ Soluciones

### Solución 1: Eliminar y Recrear Tablas (Recomendada para desarrollo)

**Ventajas:**
- Simple y rápida
- Hibernate recrea automáticamente con tipos correctos
- No requiere migraciones manuales

**Desventajas:**
- Se pierden todos los datos de exámenes

**Pasos:**

1. **Detener la aplicación** si está corriendo

2. **Ejecutar el script SQL** `scripts/fix-exams-uuid-simple.sql`:

   **Opción A - Usando pgAdmin o DBeaver:**
   - Conectarse a `sigep_db`
   - Abrir el archivo `scripts/fix-exams-uuid-simple.sql`
   - Ejecutar el script

   **Opción B - Desde Docker (si usas docker-compose):**
   ```bash
   docker exec -i sigep-postgres psql -U sigep_user -d sigep_db < scripts/fix-exams-uuid-simple.sql
   ```

   **Opción C - Copiar y pegar manualmente:**
   ```sql
   DROP TABLE IF EXISTS exam_grade_history CASCADE;
   DROP TABLE IF EXISTS exam_submissions CASCADE;
   DROP TABLE IF EXISTS exams CASCADE;
   ```

3. **Iniciar la aplicación**
   ```bash
   gradlew :application:bootRun
   ```

4. **Verificar en logs** que las tablas se crearon correctamente:
   ```
   Hibernate: create table exams (
       id uuid not null,
       ...
   )
   ```

---

### Solución 2: Migración Manual de Datos (Para producción con datos)

Si tienes datos importantes que no puedes perder:

**Pasos:**

1. **Hacer backup de los datos:**
   ```sql
   -- Backup de exams
   CREATE TABLE exams_backup AS SELECT * FROM exams;
   
   -- Backup de exam_submissions
   CREATE TABLE exam_submissions_backup AS SELECT * FROM exam_submissions;
   
   -- Backup de exam_grade_history
   CREATE TABLE exam_grade_history_backup AS SELECT * FROM exam_grade_history;
   ```

2. **Eliminar tablas originales:**
   ```sql
   DROP TABLE IF EXISTS exam_grade_history CASCADE;
   DROP TABLE IF EXISTS exam_submissions CASCADE;
   DROP TABLE IF EXISTS exams CASCADE;
   ```

3. **Iniciar aplicación** para que Hibernate cree las nuevas tablas con UUID

4. **Migrar datos manualmente:**
   ```sql
   -- Migrar exams (convertir BIGINT a UUID)
   -- NOTA: No es posible conversión automática, necesitas generar UUIDs
   -- Esta es solo una plantilla, ajustar según necesidades
   INSERT INTO exams (id, course_id, title, ...)
   SELECT 
       gen_random_uuid(),  -- Nuevo UUID
       course_id,
       title,
       ...
   FROM exams_backup;
   ```

   > ⚠️ **Advertencia**: La migración de BIGINT a UUID requiere repensar las relaciones y claves foráneas.

---

### Solución 3: Cambiar a BIGINT en el Código (No recomendada)

Cambiar el código para usar `Long` en lugar de `UUID`:

**Ventajas:**
- No se pierden datos
- Compatible con tablas existentes

**Desventajas:**
- Cambia la arquitectura decidida (UUID es mejor para sistemas distribuidos)
- Requiere cambiar código en múltiples archivos
- Inconsistente con otros módulos si usan UUID

**No se recomienda** a menos que tengas datos críticos y no puedas migrarlos.

---

## 🎯 Solución Recomendada

Para el entorno actual de **desarrollo**, la **Solución 1** es la más apropiada:

1. Ejecutar script de eliminación de tablas
2. Reiniciar aplicación
3. Verificar que las tablas se recrearon correctamente

**Script a ejecutar:**
```sql
-- Archivo: scripts/fix-exams-uuid-simple.sql
DROP TABLE IF EXISTS exam_grade_history CASCADE;
DROP TABLE IF EXISTS exam_submissions CASCADE;
DROP TABLE IF EXISTS exams CASCADE;
```

---

## 🔄 Prevención Futura

Para evitar este problema en el futuro:

### 1. Usar Migraciones de Base de Datos

En lugar de `ddl-auto=update`, usar **Flyway** o **Liquibase**:

```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Solo validar, no modificar
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### 2. Definir Tipo de ID Consistentemente

Si usas UUID, asegurarse de que las tablas se creen con UUID desde el inicio:

```kotlin
@Id
@Column(columnDefinition = "UUID")
val id: UUID = UUID.randomUUID()
```

### 3. Testing de Schema

Crear tests de integración que verifiquen el esquema de base de datos:

```kotlin
@Test
fun `verify exams table has UUID primary key`() {
    val sql = "SELECT data_type FROM information_schema.columns WHERE table_name = 'exams' AND column_name = 'id'"
    val dataType = jdbcTemplate.queryForObject(sql, String::class.java)
    assertEquals("uuid", dataType)
}
```

---

## 📊 Verificación Post-Fix

Después de aplicar la solución, verificar que todo funciona:

1. **Iniciar aplicación sin errores:**
   ```bash
   gradlew :application:bootRun
   ```

2. **Verificar estructura de tablas en base de datos:**
   ```sql
   SELECT column_name, data_type 
   FROM information_schema.columns 
   WHERE table_name = 'exams' AND column_name = 'id';
   
   -- Debería retornar: id | uuid
   ```

3. **Probar endpoints del módulo exams:**
   - GET /api/v1/exams
   - POST /api/v1/exams (crear examen de prueba)

4. **Verificar en Swagger:** http://localhost:8080/swagger-ui/index.html

---

## 📝 Notas

- Este problema solo afecta al módulo `exams`
- Los módulos `students`, `courses`, `staff` no tienen este problema
- UUID es la elección correcta para sistemas distribuidos y es consistente con la arquitectura DDD
- En producción, se recomienda usar migraciones controladas (Flyway/Liquibase)

---

**Fecha**: 2025-11-04  
**Estado**: Documentado y script de solución creado  
**Scripts**: 
- `scripts/fix-exams-uuid.sql`
- `scripts/fix-exams-uuid-simple.sql`

---

## ⚠️ Problema Relacionado: Índices Duplicados

### Error

```
ERROR: relation "idx_xxx" already exists
```

### Causa

Hibernate con `ddl-auto=update` intenta crear índices que ya existen.

### Solución Rápida

```bash
# Eliminar índice duplicado
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "DROP INDEX IF EXISTS idx_xxx CASCADE;"

# O eliminar la tabla completa
docker exec -i sigep-postgres psql -U sigep_user -d sigep_db -c "DROP TABLE IF EXISTS tabla_xxx CASCADE;"

# Reiniciar aplicación
.\gradlew :application:bootRun
```

**Ver documentación completa**: `SOLUTION_APPLIED_INDEX_DUPLICATE.md`

