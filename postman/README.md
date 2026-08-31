# SiGEP Postman

## Importar

1. Importar `SiGEP.postman_collection.json`.
2. Importar `SiGEP.local.postman_environment.json` o `SiGEP.production.postman_environment.json`.
3. Seleccionar el ambiente antes de ejecutar solicitudes.
4. Nunca exportar ni versionar ambientes que contengan tokens o contrasenas.

## Autenticacion multirrol

1. Ejecutar `Auth / Login`.
2. Si la respuesta exige seleccion, definir `selectedRole=ADMIN` y ejecutar `Auth / Select Initial Role`.
3. La coleccion guarda automaticamente `token`, `refreshToken`, `roleSelectionToken` y el rol activo.
4. Para cambiar de espacio, definir `selectedRole`. Si el destino es `ADMIN`, completar temporalmente `adminPassword` y ejecutar `Auth / Switch Active Role`.

## Habilitar GUARDIAN para una cuenta existente

1. Mantener una sesion con rol activo `ADMIN`.
2. Definir `adminUserId` y `userRole=GUARDIAN`.
3. Ejecutar `Admin Users and Roles / Get User Roles`.
4. Ejecutar `Admin Users and Roles / Grant Role to User`. La operacion es idempotente.
5. Repetir `Get User Roles` y comprobar que `roles` contiene `GUARDIAN`.
6. Ejecutar `List Assignable GUARDIAN Users` para comprobar que la cuenta aparece en el catalogo.

Valores iniciales de esta coleccion:

- `adminUserId=604` para `rmainero`.
- `guardianUserId=606` para verificar `amastracchio`.

## Vincular responsables academicos a un estudiante

`PUT /students/{id}/guardians` reemplaza el conjunto academico completo. No reasigna cuentas de facturacion, cargos, pagos ni facturas.

1. Definir `studentId`.
2. Ejecutar `Student Guardian Relationships / Get Student and Current Guardians`.
3. Construir `guardianIdsJson` conservando todos los responsables que deban continuar.
4. Definir `primaryGuardianId` con un ID incluido en `guardianIdsJson`.
5. Documentar el motivo en `guardianUpdateReason`.
6. Recién después de revisar el conjunto completo, definir `confirmGuardianReplacement=YES` y ejecutar `Replace Student Guardians`.
7. Volver a ejecutar `Get Student and Current Guardians` y verificar el resultado.

Para Cedric (`studentId=373`) y Ludovica (`studentId=124`), la auditoria previa mostró las cuentas 327 y 200. No reemplazar ni retirar ninguna sin confirmar primero cuál debe conservarse y cuál será responsable principal.

## Tutores y clientes

El folder `Guardian Clients` separa operaciones deliberadamente:

- `Update Guardian Client Profile` modifica sólo canal preferido y notas administrativas.
- `Update Guardian Client Account` modifica identidad y contacto.
- Ninguna de las dos cambia roles, estado de acceso, estudiantes ni titularidad financiera.

Las operaciones de escritura requieren una variable `confirm...=YES`. La actualización de cuenta requiere que V38 esté aplicada en el ambiente y que el backend correspondiente ya esté desplegado.
