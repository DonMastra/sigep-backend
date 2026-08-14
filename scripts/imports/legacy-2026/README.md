# Importación legacy 2026 para UAT

Este proceso prepara y carga el estado académico necesario para continuar el ciclo lectivo 2026 en el ambiente de entrenamiento `sigep-prod`. Los archivos fuente y el SQL generado contienen datos personales y no deben copiarse al repositorio.

## Decisiones de mapeo

- Las 625 filas de alumnos se consolidan por el identificador `Usuario` del legacy en 546 identidades. Las filas repetidas representan historia por ciclo, no personas duplicadas.
- Se importan las 320 matrículas del ciclo 2026: `Alumno Regular` e `Inscripto` pasan a `ACTIVE`; `Baja` pasa a `DROPPED`.
- Se importan los 40 cursos informados en `Cursadas`, incluso los grupos sin alumnos en el detalle. Los duplicados por nombre se consolidan y el detalle de alumnos prevalece para las matrículas.
- La relación alumno-responsable se acepta únicamente por coincidencia exacta normalizada del nombre completo dentro de `Hijo/s`. Cuando existen dos coincidencias se elige como responsable principal el registro con más datos de contacto y luego la menor fila de origen; la alternativa queda auditada.
- Los responsables se crean inactivos y en `PENDING_APPROVAL`, sin credencial utilizable. Deben habilitarse individualmente mediante el flujo de invitación.
- El export de alumnos no contiene fecha de nacimiento ni domicilio. Se utiliza `1900-01-01` y el texto `SIN INFORMAR - MIGRACION LEGACY 2026` como marcadores visibles y auditados, nunca como datos afirmados.
- El export de docentes solo contiene nombres. Los demás campos obligatorios quedan con marcadores explícitos; no se crean cuentas docentes.
- El export no contiene horarios, duración ni capacidad. Los cursos quedan con duración técnica de 1 hora y capacidad mínima de 30, ambos pendientes de validación. No se inventan sesiones ni aulas.
- Se crean 24 niveles según la estructura entregada. `Kids`, `Children` y `Junior` usan el segmento `CHILDREN`; `Teens` y `Senior`, `TEENS`; `Adults`, `ADULTS`.
- El plan general usa ARS 90.000 y Kids ARS 80.000, vencimiento el día 10, vigencia abril-diciembre y 9 períodos. La matrícula Kids se registra en ARS 85.000. No se crea matrícula general porque su importe no aparece en las fuentes.
- No se importan pagos ni se presume que abril-julio estén impagos. Para las matrículas activas con responsable se crean cuotas de agosto a diciembre, sin recargo ni débito automático.
- Las matrículas activas sin responsable coincidente se cargan académicamente, pero no generan solicitud, cuenta corriente ni deuda inventada. Quedan como incidencias `BLOCKER` para conciliación administrativa.

## Seguridad y preflight

El importador:

1. verifica los conteos y la estructura exacta de los cinco libros revisados;
2. calcula SHA-256 de XLS/XLSX y capturas;
3. exige que la base sea `sigep_prod`, tenga V27 registrada y no contenga filas funcionales;
4. ejecuta V28 y toda la carga dentro de una única transacción;
5. registra hashes de identidad, relaciones, incidencias y mapeos sin copiar payloads fuente a las tablas de auditoría;
6. concilia conteos y referencias antes de `COMMIT`;
7. elimina por defecto el SQL generado después de una ejecución exitosa.

La primera ejecución debe realizarse en una rama Neon descartable creada desde el esquema vacío. Solo después de validar `verify-legacy-2026.sql`, arrancar el backend con `ddl-auto=validate` y probar la restauración se puede repetir con un `run-id` nuevo en la rama UAT.

## Ejecución

Requisitos locales:

- Excel de escritorio para convertir los dos `.xls` sin modificar los originales;
- Node.js y la ruta local de `@oai/artifact-tool`;
- Java/Javac y el driver JDBC de PostgreSQL;
- `DATABASE_URL`, `DATABASE_USERNAME` y `DATABASE_PASSWORD` definidos únicamente en el proceso actual.

Preparación sin escritura en base:

```powershell
.\scripts\imports\legacy-2026\Invoke-LegacyImport.ps1 `
  -SourceDirectory '<carpeta-fuente>' `
  -WorkDirectory '<carpeta-temporal-cifrada>' `
  -ArtifactToolRoot '<ruta-artifact-tool>' `
  -PostgresJdbcJar '<ruta-postgresql.jar>' `
  -RunId 'LEGACY-2026-REHEARSAL-001'
```

La escritura requiere agregar `-Execute`. Las credenciales no deben pasarse como argumentos ni guardarse en historial. `-KeepGeneratedSql` existe solo para una revisión controlada; el comportamiento normal elimina el SQL identificado al confirmar el commit.

## Criterio para repetir en UAT

- conteos esperados confirmados por el manifiesto generado;
- cero referencias rotas;
- cuotas únicamente de agosto a diciembre;
- responsables pendientes sin acceso compartido;
- incidencias sin resolver inventariadas;
- backend iniciado con perfil `prod` y `ddl-auto=validate`;
- backup/restore de la rama descartable probado;
- matrícula general, horarios/capacidades y responsable de cada incidencia definidos antes de considerar el ambiente apto para continuidad operativa.
