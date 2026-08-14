\set ON_ERROR_STOP on

SELECT current_database() AS database_name,
       current_user AS connected_role,
       pg_size_pretty(pg_database_size(current_database())) AS database_size;

SELECT rolname,
       rolsuper,
       rolinherit,
       rolcreaterole,
       rolcreatedb,
       rolreplication,
       pg_has_role(rolname, 'neon_superuser', 'member') AS neon_superuser_member
FROM pg_roles
WHERE rolname IN ('sigep_owner_prod', 'sigep_app_prod')
ORDER BY rolname;

SELECT has_database_privilege('sigep_app_prod', 'sigep_prod', 'CONNECT') AS runtime_can_connect,
       has_database_privilege('sigep_app_prod', 'sigep_prod', 'CREATE') AS runtime_can_create_database_objects,
       has_database_privilege('sigep_app_prod', 'sigep_prod', 'TEMP') AS runtime_can_create_temp,
       has_schema_privilege('sigep_app_prod', 'public', 'USAGE') AS runtime_can_use_schema,
       has_schema_privilege('sigep_app_prod', 'public', 'CREATE') AS runtime_can_create_in_schema;

SELECT version, git_commit, applied_at, description
FROM public.schema_version
ORDER BY applied_at DESC;

SELECT schemaname, relname, n_live_tup
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY n_live_tup DESC, relname;
