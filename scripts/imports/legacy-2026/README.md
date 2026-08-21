# Importación legacy 2026 para UAT

Este proceso prepara y carga el estado académico necesario para continuar el ciclo lectivo 2026 en el ambiente de entrenamiento `sigep-prod`. Los archivos fuente y el SQL generado contienen datos personales y no deben copiarse al repositorio.

## Decisiones de mapeo

- Las 625 filas de alumnos se consolidan por el identificador técnico `Usuario` del legacy en 546 identidades. `Matrícula` se conserva como identificador de negocio del alumno (`students.student_number`), priorizando el valor 2026 y, si no existe, el del último ciclo disponible.
- Se importan las 320 matrículas del ciclo 2026: `Alumno Regular` e `Inscripto` pasan a `ACTIVE`; `Baja` pasa a `DROPPED`.
- Un mismo alumno puede tener más de una inscripción activa siempre que corresponda a cursos distintos. La identidad de cada inscripción se compone con matrícula y curso para evitar colisiones.
- Se importan los 40 cursos informados en `Cursadas`, incluso los grupos sin alumnos en el detalle. Los duplicados por nombre se consolidan y el detalle de alumnos prevalece para las matrículas.
- La relación alumno-responsable se acepta únicamente por coincidencia exacta normalizada del nombre completo dentro de `Hijo/s`. Cuando existen dos coincidencias se elige como responsable principal el registro con más datos de contacto y luego la menor fila de origen; la alternativa queda auditada.
- Los responsables se crean inactivos y en `PENDING_APPROVAL`, sin credencial utilizable. Deben habilitarse individualmente mediante el flujo de invitación.
- El export de alumnos no contiene fecha de nacimiento ni domicilio. Se utiliza `1900-01-01` y el texto `SIN INFORMAR - MIGRACION LEGACY 2026` como marcadores visibles y auditados, nunca como datos afirmados.
- El export de docentes solo contiene nombres. Los demás campos obligatorios quedan con marcadores explícitos; no se crean cuentas docentes.
- Los 40 cursos quedan con duración confirmada de 60 horas y capacidad confirmada de 20. `6.Cursos.xlsx` aporta horario para los 30 cursos con alumnos activos: se generan sus sesiones sin aula asignada. Los 10 cursos sin alumnos y sin horario utilizable se conservan sin sesiones para que Administración los habilite desde la UI cuando corresponda.
- Se crean 24 niveles según la estructura entregada. `Kids`, `Children` y `Junior` usan el segmento `CHILDREN`; `Teens` y `Senior`, `TEENS`; `Adults`, `ADULTS`.
- El plan general usa ARS 90.000 y Kids ARS 80.000, vencimiento el día 10, vigencia abril-diciembre y 9 períodos. El import inicial no crea políticas de matrícula: Kids permanece pendiente y la matrícula general se aplica únicamente desde la decisión institucional confirmada de ARS 90.000.
- No se importan pagos ni se presume que abril-julio estén impagos. Para las matrículas activas con responsable se crean cuotas de agosto a diciembre, sin recargo ni débito automático.
- Las matrículas activas sin responsable coincidente se cargan académicamente, pero no generan solicitud, cuenta corriente ni deuda inventada. Quedan como incidencias `BLOCKER` para conciliación administrativa.

## Seguridad y preflight

El importador:

1. verifica los conteos y la estructura exacta de los cinco libros revisados;
2. calcula SHA-256 de XLS/XLSX y capturas;
3. exige que la base sea `sigep_prod`, tenga V27 registrada y no contenga filas funcionales;
4. ejecuta V28, V30 y toda la carga dentro de una única transacción;
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
- responsables de cada incidencia no resuelta definidos antes de cerrar la conciliación; los cursos sin alumnos pueden permanecer sin sesiones ni aula hasta que Administración decida habilitarlos desde la UI.

## Conciliación posterior con el libro institucional

`Conciliacion_pendientes_migracion_SiGEP_2026.xlsx` es la única entrada admitida para resolver los datos que no pudieron inferirse de las exportaciones. El proceso de conciliación:

- valida las siete hojas, sus encabezados y los 108 casos sin responsable, 36 ambiguos, 40 cursos, 8 decisiones, 591 responsables del catálogo y 546 identificadores de alumno;
- cruza las identidades confirmadas con los hashes y filas de origen de `LEGACY-2026-UAT-20260814A`;
- ignora toda fila que no tenga `Estado = CONFIRMADO`;
- vincula el responsable y crea solicitud, cuotas y cargos de agosto a diciembre solo para casos originalmente bloqueados por falta de responsable; los casos Adults confirmados como `AUTOTUTELA` reciben un usuario GUARDIAN inactivo vinculado a la misma persona STUDENT;
- cuando cambia un responsable ambiguo, alinea alumno, solicitud y cuenta de los cargos en una transacción, pero aborta si existe pago, factura, recargo o débito asociado;
- actualiza duración y capacidad para todo curso confirmado y genera sesiones entre el 1 de agosto y el 31 de diciembre cuando días y horas están completos; el aula es opcional y puede asignarse posteriormente desde la UI;
- rechaza horarios parciales y cursos con alumnos activos sin horario, y detecta choques entre docente, alumnos y aulas presenciales antes de escribir;
- registra autorizaciones, administradoras, continuidad y cartel solo mediante hashes de evidencia; la única decisión funcional estructurada es la matrícula general exacta de ARS 90.000;
- confirma o corrige los 546 `student_number`, aplica V29 y V30, audita cada decisión y cada fila creada/actualizada, verifica el resultado y confirma todo en una sola transacción.

La conciliación está vinculada deliberadamente a `LEGACY-2026-UAT-20260814A`, generado por `legacy-2026-v1`: para localizar sus inscripciones reutiliza la clave histórica basada solo en `Matrícula`. El importador base actual mantiene `Matrícula + curso` para nuevas cargas y así admite dos inscripciones simultáneas del mismo alumno sin colisiones.

La duración de curso debe expresarse en horas enteras porque `courses.duration` es un entero. Los horarios exactos quedan en `course_sessions`. No se inventan feriados ni excepciones: deben registrarse luego con el flujo de sesiones.

Preparación sin escritura:

```powershell
.\scripts\imports\legacy-2026\Invoke-LegacyReconciliation.ps1 `
  -WorkbookPath '<libro-completado.xlsx>' `
  -WorkDirectory '<carpeta-temporal-cifrada>' `
  -ArtifactToolRoot '<ruta-artifact-tool>' `
  -PostgresJdbcJar '<ruta-postgresql.jar>'
```

La salida informa el SHA-256 del libro, los confirmados por bloque y la cantidad de sesiones a generar. Revisar ese resumen antes de repetir con `-Execute`. La ejecución se rechaza si no existe ninguna fila confirmada. Las credenciales se leen exclusivamente de `DATABASE_URL`, `DATABASE_USERNAME` y `DATABASE_PASSWORD` del proceso.

Antes de ejecutar en `sigep_prod`, realizar un backup y repetir la preparación/ejecución en una rama Neon descartable creada desde el estado actual de UAT. Luego ejecutar `verify-legacy-reconciliation.sql`, arrancar el backend con perfil `prod` y revisar los flujos afectados.

## Rollback de una conciliación

El rollback se prepara por `run-id` y no escribe por defecto:

```powershell
.\scripts\imports\legacy-2026\Invoke-LegacyReconciliationRollback.ps1 `
  -RunId '<LEGACY-RECON-2026-...>' `
  -WorkDirectory '<carpeta-temporal-cifrada>' `
  -PostgresJdbcJar '<ruta-postgresql.jar>'
```

Agregar `-Execute` únicamente después de revisar el SQL y su hash. El rollback aborta si una conciliación posterior depende de la misma decisión, si un cargo ya tiene actividad financiera, si una sesión tiene asistencia/excepciones o si una cuenta creada ya fue utilizada. Conserva la auditoría, restaura vínculos e identificadores, elimina usuarios de autotutela y la política general creados por la ejecución, vuelve a abrir las incidencias y marca la ejecución como `ROLLED_BACK`.
