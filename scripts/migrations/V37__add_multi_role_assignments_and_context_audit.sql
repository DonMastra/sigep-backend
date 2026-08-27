-- V37 - Multi-role assignments with one active role per authenticated context.
-- Additive migration: users.role remains as a compatibility/default column.

CREATE TABLE IF NOT EXISTS user_role_assignments (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    role        VARCHAR(32) NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT,
    revoked_at  TIMESTAMP,
    revoked_by  BIGINT,
    CONSTRAINT uk_user_role_assignment UNIQUE (user_id, role),
    CONSTRAINT user_role_assignments_role_check CHECK (role IN ('ADMIN', 'TEACHER', 'GUARDIAN')),
    CONSTRAINT fk_user_role_assignment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_assignment_assigned_by FOREIGN KEY (assigned_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_user_role_assignment_revoked_by FOREIGN KEY (revoked_by) REFERENCES users(id) ON DELETE SET NULL
);

INSERT INTO user_role_assignments (user_id, role, assigned_at)
SELECT id, role, COALESCE(created_at, CURRENT_TIMESTAMP)
FROM users
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO user_role_assignments (user_id, role, assigned_at)
SELECT linked_user_id, 'TEACHER', CURRENT_TIMESTAMP
FROM teaching_staff
WHERE linked_user_id IS NOT NULL
ON CONFLICT (user_id, role) DO NOTHING;

INSERT INTO user_role_assignments (user_id, role, assigned_at)
SELECT DISTINCT guardian_id, 'GUARDIAN', CURRENT_TIMESTAMP
FROM students
WHERE guardian_id IS NOT NULL
ON CONFLICT (user_id, role) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_user_role_assignments_active_user
    ON user_role_assignments(user_id)
    WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_role_assignments_active_role
    ON user_role_assignments(role, user_id)
    WHERE revoked_at IS NULL;

CREATE TABLE IF NOT EXISTS user_role_context_events (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    previous_role VARCHAR(32),
    active_role   VARCHAR(32) NOT NULL,
    event_type    VARCHAR(32) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT user_role_context_previous_role_check
        CHECK (previous_role IS NULL OR previous_role IN ('ADMIN', 'TEACHER', 'GUARDIAN')),
    CONSTRAINT user_role_context_active_role_check
        CHECK (active_role IN ('ADMIN', 'TEACHER', 'GUARDIAN')),
    CONSTRAINT user_role_context_event_type_check
        CHECK (event_type IN ('LOGIN', 'LOGIN_SELECTION', 'SWITCH')),
    CONSTRAINT fk_user_role_context_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_role_context_events_user_created
    ON user_role_context_events(user_id, created_at DESC);
