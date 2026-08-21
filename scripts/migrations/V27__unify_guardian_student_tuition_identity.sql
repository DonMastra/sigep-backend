-- V27 - Resolve student identity before billing and unify ADMIN/GUARDIAN tuition creation.
-- Manual migration. Re-runnable for QA environments that use Hibernate ddl-auto=validate.

BEGIN;

ALTER TABLE students ADD COLUMN IF NOT EXISTS document_type varchar(30);
ALTER TABLE students ADD COLUMN IF NOT EXISTS document_country varchar(2);
ALTER TABLE students ADD COLUMN IF NOT EXISTS normalized_document_number varchar(50);

UPDATE students
SET document_type = CASE
        WHEN document_number IS NULL OR btrim(document_number) = '' THEN 'NO_DOCUMENT'
        WHEN upper(document_number) ~ '^DNI[ .-]*[0-9 .-]{7,12}$'
          OR document_number ~ '^[0-9 .-]{7,12}$' THEN 'DNI'
        ELSE 'NATIONAL_ID'
    END,
    document_country = CASE
        WHEN document_number IS NULL OR btrim(document_number) = '' THEN 'AR'
        WHEN upper(document_number) ~ '^DNI[ .-]*[0-9 .-]{7,12}$'
          OR document_number ~ '^[0-9 .-]{7,12}$' THEN 'AR'
        ELSE 'ZZ'
    END
WHERE document_type IS NULL OR document_country IS NULL;

UPDATE students
SET normalized_document_number = CASE
        WHEN document_type = 'DNI' THEN lpad(regexp_replace(document_number, '[^0-9]', '', 'g'), 8, '0')
        WHEN document_type IN ('PASSPORT', 'NATIONAL_ID') THEN upper(regexp_replace(document_number, '[^A-Za-z0-9]', '', 'g'))
        ELSE NULL
    END
WHERE normalized_document_number IS NULL
  AND document_number IS NOT NULL;

ALTER TABLE students ALTER COLUMN document_type SET DEFAULT 'DNI';
ALTER TABLE students ALTER COLUMN document_type SET NOT NULL;
ALTER TABLE students ALTER COLUMN document_country SET DEFAULT 'AR';
ALTER TABLE students ALTER COLUMN document_country SET NOT NULL;
ALTER TABLE students ALTER COLUMN document_number DROP NOT NULL;

DO $$
DECLARE constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT c.conname
        FROM pg_constraint c
        JOIN pg_attribute a
          ON a.attrelid = c.conrelid
         AND a.attnum = ANY(c.conkey)
        WHERE c.conrelid = 'students'::regclass
          AND c.contype = 'u'
          AND cardinality(c.conkey) = 1
          AND a.attname = 'email'
    LOOP
        EXECUTE format('ALTER TABLE students DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_students_normalized_document
    ON students(document_country, document_type, normalized_document_number)
    WHERE normalized_document_number IS NOT NULL
      AND document_type NOT IN ('NO_DOCUMENT', 'IN_PROCESS');
CREATE INDEX IF NOT EXISTS idx_students_guardian ON students(guardian_id);
CREATE INDEX IF NOT EXISTS idx_students_document_identity
    ON students(document_country, document_type, normalized_document_number);

CREATE TABLE IF NOT EXISTS student_guardian_link_events (
    id bigserial PRIMARY KEY,
    student_id bigint NOT NULL,
    previous_guardian_user_id bigint,
    guardian_user_id bigint,
    action varchar(20) NOT NULL,
    origin varchar(20) NOT NULL,
    actor_user_id bigint NOT NULL,
    reason varchar(500),
    created_at timestamp NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_student_guardian_link_student
    ON student_guardian_link_events(student_id, created_at);
CREATE INDEX IF NOT EXISTS idx_student_guardian_link_guardian
    ON student_guardian_link_events(guardian_user_id);

CREATE TABLE IF NOT EXISTS guardian_invitations (
    id varchar(36) PRIMARY KEY,
    user_id bigint NOT NULL UNIQUE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamp NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamp NOT NULL DEFAULT now(),
    accepted_at timestamp
);

ALTER TABLE tuition_applications ADD COLUMN IF NOT EXISTS actor_user_id bigint;
ALTER TABLE tuition_applications ADD COLUMN IF NOT EXISTS origin varchar(20);
ALTER TABLE tuition_applications ADD COLUMN IF NOT EXISTS student_resolution varchar(30);
ALTER TABLE tuition_applications ADD COLUMN IF NOT EXISTS idempotency_key varchar(128);
ALTER TABLE tuition_applications ADD COLUMN IF NOT EXISTS request_fingerprint varchar(64);
ALTER TABLE tuition_applications ADD COLUMN IF NOT EXISTS version bigint DEFAULT 0;

UPDATE tuition_applications
SET actor_user_id = COALESCE(actor_user_id, guardian_user_id),
    origin = COALESCE(origin, 'GUARDIAN'),
    student_resolution = COALESCE(student_resolution, CASE WHEN student_id IS NULL THEN 'CREATED' ELSE 'EXISTING' END),
    version = COALESCE(version, 0);

ALTER TABLE tuition_applications ALTER COLUMN actor_user_id SET NOT NULL;
ALTER TABLE tuition_applications ALTER COLUMN origin SET NOT NULL;
ALTER TABLE tuition_applications ALTER COLUMN student_resolution SET NOT NULL;
ALTER TABLE tuition_applications ALTER COLUMN version SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_tuition_application_idempotency
    ON tuition_applications(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tuition_open_application_student_type
    ON tuition_applications(guardian_user_id, student_id, application_type)
    WHERE student_id IS NOT NULL
      AND status IN ('SUBMITTED', 'PAYMENT_PENDING', 'ENROLLED_PENDING_PLACEMENT', 'READY_FOR_ACADEMIC_ASSIGNMENT', 'WAITLISTED');
CREATE UNIQUE INDEX IF NOT EXISTS uq_tuition_enrollment_ledger_application
    ON tuition_ledger_entries(application_id)
    WHERE concept = 'TUITION_ENROLLMENT';
CREATE UNIQUE INDEX IF NOT EXISTS uq_tuition_monthly_ledger_period
    ON tuition_ledger_entries(application_id, due_date)
    WHERE concept = 'MONTHLY_FEE';
CREATE UNIQUE INDEX IF NOT EXISTS uq_enrollment_active_student_course
    ON enrollments(student_id, course_id)
    WHERE status = 'ACTIVE';

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_students_guardian_user') THEN
        ALTER TABLE students ADD CONSTRAINT fk_students_guardian_user
            FOREIGN KEY (guardian_id) REFERENCES users(id) ON DELETE SET NULL NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_student_guardian_event_student') THEN
        ALTER TABLE student_guardian_link_events ADD CONSTRAINT fk_student_guardian_event_student
            FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_student_guardian_event_guardian') THEN
        ALTER TABLE student_guardian_link_events ADD CONSTRAINT fk_student_guardian_event_guardian
            FOREIGN KEY (guardian_user_id) REFERENCES users(id) ON DELETE SET NULL NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_student_guardian_event_actor') THEN
        ALTER TABLE student_guardian_link_events ADD CONSTRAINT fk_student_guardian_event_actor
            FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_guardian_invitation_user') THEN
        ALTER TABLE guardian_invitations ADD CONSTRAINT fk_guardian_invitation_user
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_guardian_invitation_creator') THEN
        ALTER TABLE guardian_invitations ADD CONSTRAINT fk_guardian_invitation_creator
            FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tuition_application_guardian_user') THEN
        ALTER TABLE tuition_applications ADD CONSTRAINT fk_tuition_application_guardian_user
            FOREIGN KEY (guardian_user_id) REFERENCES users(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tuition_application_actor_user') THEN
        ALTER TABLE tuition_applications ADD CONSTRAINT fk_tuition_application_actor_user
            FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tuition_application_student') THEN
        ALTER TABLE tuition_applications ADD CONSTRAINT fk_tuition_application_student
            FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_tuition_ledger_student') THEN
        ALTER TABLE tuition_ledger_entries ADD CONSTRAINT fk_tuition_ledger_student
            FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_billing_charge_student') THEN
        ALTER TABLE billing_charges ADD CONSTRAINT fk_billing_charge_student
            FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_payment_student') THEN
        ALTER TABLE payments ADD CONSTRAINT fk_payment_student
            FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_enrollment_student') THEN
        ALTER TABLE enrollments ADD CONSTRAINT fk_enrollment_student
            FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE RESTRICT NOT VALID;
    END IF;
END $$;

COMMIT;
