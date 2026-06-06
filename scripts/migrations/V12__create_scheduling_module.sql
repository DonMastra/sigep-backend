-- =============================================================================
-- V12__create_scheduling_module.sql
-- Creates classrooms, schedule_slots, reservations tables.
-- Drops legacy course_schedules table (migrated to ScheduleSlot model).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Drop legacy course_schedules (replaced by schedule_slots + reservations)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS course_schedules CASCADE;

-- -----------------------------------------------------------------------------
-- 2. classrooms
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS classrooms (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    building   VARCHAR(100),
    floor      VARCHAR(20),
    capacity   INT          NOT NULL CHECK (capacity >= 1),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_classroom_name ON classrooms (name);

-- -----------------------------------------------------------------------------
-- 3. schedule_slots
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS schedule_slots (
    id           BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT       NOT NULL REFERENCES classrooms (id) ON DELETE RESTRICT,
    day_of_week  VARCHAR(10)  NOT NULL,
    start_time   VARCHAR(5)   NOT NULL,
    end_time     VARCHAR(5)   NOT NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    notes        VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_slot_day_of_week CHECK (
        day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')
    )
);

CREATE INDEX IF NOT EXISTS idx_slot_classroom ON schedule_slots (classroom_id);
CREATE INDEX IF NOT EXISTS idx_slot_day       ON schedule_slots (day_of_week);

-- -----------------------------------------------------------------------------
-- 4. reservations
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservations (
    id          BIGSERIAL PRIMARY KEY,
    slot_id     BIGINT      NOT NULL REFERENCES schedule_slots (id) ON DELETE RESTRICT,
    target_type VARCHAR(10) NOT NULL DEFAULT 'NONE',
    target_id   BIGINT,
    status      VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE',
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_reservation_target_type CHECK (
        target_type IN ('COURSE', 'SESSION', 'NONE')
    ),
    CONSTRAINT chk_reservation_status CHECK (
        status IN ('AVAILABLE', 'ASSIGNED', 'INACTIVE')
    )
);

CREATE INDEX IF NOT EXISTS idx_reservation_slot   ON reservations (slot_id);
CREATE INDEX IF NOT EXISTS idx_reservation_status ON reservations (status);
CREATE INDEX IF NOT EXISTS idx_reservation_target ON reservations (target_type, target_id);

-- Enforce uniqueness: a slot can have at most one non-INACTIVE reservation at a time
CREATE UNIQUE INDEX IF NOT EXISTS uq_slot_active_reservation
    ON reservations (slot_id)
    WHERE status <> 'INACTIVE';

