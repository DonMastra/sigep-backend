# SiGEP UAT de entrenamiento con datos reales

Este ambiente usa nombres `prod`, pero durante esta etapa es preproductivo: sirve para entrenamiento y validacion con datos reales. No ofrece disponibilidad operativa ni reemplaza al sistema legacy.

## Limites de esta etapa

- Neon y Render permanecen en planes gratuitos.
- Se aceptan cold starts, suspension por inactividad, reinicios y cache descartable.
- No se usan pings para mantener servicios activos.
- PostgreSQL es la unica fuente de verdad. Una perdida total de Redis solo causa cache misses.
- No se importan PDF, imagenes, documentos escaneados ni archivos Excel a PostgreSQL o Git.
- La importacion se detiene si la proyeccion supera 350 MB.

## Inventario esperado

| Recurso | QA | UAT de entrenamiento |
| --- | --- | --- |
| Rama Git backend | `qa` | `master` |
| Web Service Render | `sigep-backend-qa` | `sigep-backend-prod` |
| Perfil Spring | `qa` | `prod` |
| Proyecto Neon | `sigep-qa` | `sigep-prod` |
| Base Neon | `sigep_qa` | `sigep_prod` |
| Namespace Redis | `sigep-qa` | `sigep-prod` |

## Baseline V27 sin datos

Los scripts usan `pg_dump` y `psql` locales cuando estan disponibles. Tambien aceptan `PG_BIN_DIR` apuntando al directorio `bin` de PostgreSQL y, como alternativa, usan Docker Desktop con la imagen oficial `postgres:18`. Las URLs deben ser conexiones directas de Neon en formato libpq. Nunca pegarlas en Git, tickets, capturas ni documentos.

1. Confirmar que QA arranca con `JPA_DDL_AUTO=validate`.
2. Definir temporalmente `QA_DATABASE_URL` en la terminal local.
3. Ejecutar:

   ```powershell
   .\scripts\production\export-schema-baseline.ps1
   ```

4. Revisar `scripts/production/schema-baseline-v27.sql` y confirmar que no contiene `COPY`, `INSERT`, propietarios, privilegios ni secretos.
5. Crear la rama Neon descartable `schema-rehearsal` dentro del proyecto `sigep-prod`.
6. Definir `TARGET_DATABASE_URL` con la conexion del rol `sigep_owner_prod` a `sigep_prod` en esa rama.
7. Restaurar y aplicar permisos:

   ```powershell
   .\scripts\production\restore-schema-baseline.ps1 -ApplyRuntimeGrants
   ```

8. Arrancar el backend con perfil `prod` y `JPA_DDL_AUTO=validate` contra la rama descartable.
9. Corregir cualquier diferencia en el baseline. Nunca usar `ddl-auto=update`.
10. Repetir la restauracion sobre la rama predeterminada `sigep-prod` solo después de que el ensayo sea exitoso.

Después de versionar el baseline, registrar el commit sin exponer la URL:

```powershell
docker run --rm --env TARGET_DATABASE_URL --mount "type=bind,source=$PWD/scripts/production,destination=/work,readonly" postgres:18 sh -c 'exec psql --dbname="$TARGET_DATABASE_URL" --no-psqlrc --set=ON_ERROR_STOP=1 --set=schema_version=V27 --set=schema_commit=<COMMIT> --file=/work/record-schema-version.sql'
```

Ejecutar `verify-prod-uat.sql` y conservar solo el resultado sin datos identificados.

## Render `sigep-backend-prod`

El servicio se crea manualmente para que `render.yaml` siga administrando unicamente QA. Debe usar Docker, plan Free, rama `master`, region Virginia, health check `/actuator/health` y auto-deploy desactivado durante la preparacion.

Variables obligatorias:

```text
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<host-directo>/sigep_prod?sslmode=require
DATABASE_USERNAME=sigep_app_prod
DATABASE_PASSWORD=<secreto-exclusivo>
DB_SCHEMA=public
DB_MAX_POOL_SIZE=5
DB_MIN_IDLE=1
JPA_DDL_AUTO=validate
JWT_SECRET=<secreto-exclusivo>
CORS_ALLOWED_ORIGINS=https://sigep.com.ar,https://www.sigep.com.ar
CACHE_NAMESPACE=sigep-prod
REDIS_HOST=<Key Value compartido>
REDIS_PORT=<puerto>
REDIS_USERNAME=<usuario interno>
REDIS_PASSWORD=<secreto>
APP_PUBLIC_REGISTRATION_ENABLED=false
BILLING_FISCAL_PROVIDER=disabled
BILLING_AUTOMATIC_DEBIT_PROVIDER=disabled
BILLING_OUTBOX_ENABLED=false
SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

No reutilizar JWT, credenciales Neon ni contrasenas de QA. No agregar comodines `*.vercel.app`.

## Criterio de habilitacion

- `:application:bootJar` y pruebas focalizadas aprobadas.
- Perfil `prod` rechaza mock fiscal y debito automatico simulado.
- Registro publico devuelve `403` con codigo `PUBLIC_REGISTRATION_DISABLED`.
- Redis usa prefijos separados y no existe ningun `FLUSHALL` o `FLUSHDB`.
- Reiniciar o perder Redis no altera datos PostgreSQL.
- El backend inicia con `ddl-auto=validate` en `schema-rehearsal` y luego en `sigep-prod`.
- `/actuator/health` responde despues del cold start sin publicar detalles internos.
- QA y UAT tienen hosts, bases y conteos distintos; UAT comienza sin registros funcionales.
- Existe un dump cifrado y se demostro una restauracion real en una rama descartable.

## Datos y acceso

Antes de importar se requiere autorizacion institucional expresa, MFA en Neon, acceso minimo nominado y cuentas individuales con cambio de contrasena. Los Excel originales permanecen cifrados en almacenamiento institucional y se registra su SHA-256. La UI debe mostrar: `ENTORNO DE ENTRENAMIENTO - CONTIENE DATOS REALES - NO ES EL SISTEMA OPERATIVO` antes de habilitar a las administradoras.

Al finalizar se documenta una decision autorizada: promover, recargar desde cero o eliminar el ambiente.
