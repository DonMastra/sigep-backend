-- Additive exam migration. Existing scores are intentionally left unchanged.
ALTER TABLE exams
    ADD COLUMN IF NOT EXISTS source_exam_id UUID;

ALTER TABLE exam_submissions
    ADD COLUMN IF NOT EXISTS speaking_score INTEGER,
    ADD COLUMN IF NOT EXISTS source_submission_id UUID,
    ADD COLUMN IF NOT EXISTS recovery_skills VARCHAR(100);

ALTER TABLE exam_grade_history
    ADD COLUMN IF NOT EXISTS previous_speaking_score INTEGER,
    ADD COLUMN IF NOT EXISTS new_speaking_score INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_exam_source_exam') THEN
        ALTER TABLE exams
            ADD CONSTRAINT fk_exam_source_exam
            FOREIGN KEY (source_exam_id) REFERENCES exams(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_submission_source_submission') THEN
        ALTER TABLE exam_submissions
            ADD CONSTRAINT fk_submission_source_submission
            FOREIGN KEY (source_submission_id) REFERENCES exam_submissions(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_submission_speaking_score') THEN
        ALTER TABLE exam_submissions
            ADD CONSTRAINT chk_submission_speaking_score
            CHECK (speaking_score IS NULL OR speaking_score BETWEEN 0 AND 100);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_exam_source_exam_id ON exams(source_exam_id);
CREATE INDEX IF NOT EXISTS idx_submission_source_submission_id
    ON exam_submissions(source_submission_id);

COMMENT ON COLUMN exams.source_exam_id IS
    'Examen original del cual se derivan alumnos y categorias para un recuperatorio.';
COMMENT ON COLUMN exam_submissions.recovery_skills IS
    'Categorias desaprobadas del examen original, separadas por coma.';
