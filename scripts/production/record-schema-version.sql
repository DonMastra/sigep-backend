\set ON_ERROR_STOP on

CREATE TABLE IF NOT EXISTS public.schema_version (
    version VARCHAR(32) PRIMARY KEY,
    git_commit VARCHAR(64) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255) NOT NULL
);

INSERT INTO public.schema_version (version, git_commit, description)
VALUES (:'schema_version', :'schema_commit', 'QA schema-only baseline validated for training UAT')
ON CONFLICT (version) DO UPDATE
SET git_commit = EXCLUDED.git_commit,
    applied_at = CURRENT_TIMESTAMP,
    description = EXCLUDED.description;

GRANT SELECT ON public.schema_version TO sigep_app_prod;
