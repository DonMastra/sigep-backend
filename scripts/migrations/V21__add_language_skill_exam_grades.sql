-- Adds integer Reading, Writing and Listening grades while preserving the
-- existing score column as the final/legacy grade.

ALTER TABLE exam_submissions
    ADD COLUMN IF NOT EXISTS reading_score INTEGER,
    ADD COLUMN IF NOT EXISTS writing_score INTEGER,
    ADD COLUMN IF NOT EXISTS listening_score INTEGER;

ALTER TABLE exam_submissions
    DROP CONSTRAINT IF EXISTS chk_submission_reading_score,
    DROP CONSTRAINT IF EXISTS chk_submission_writing_score,
    DROP CONSTRAINT IF EXISTS chk_submission_listening_score;

ALTER TABLE exam_submissions
    ADD CONSTRAINT chk_submission_reading_score
        CHECK (reading_score IS NULL OR reading_score BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_submission_writing_score
        CHECK (writing_score IS NULL OR writing_score BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_submission_listening_score
        CHECK (listening_score IS NULL OR listening_score BETWEEN 0 AND 100);

ALTER TABLE exam_grade_history
    ALTER COLUMN new_score DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS previous_reading_score INTEGER,
    ADD COLUMN IF NOT EXISTS new_reading_score INTEGER,
    ADD COLUMN IF NOT EXISTS previous_writing_score INTEGER,
    ADD COLUMN IF NOT EXISTS new_writing_score INTEGER,
    ADD COLUMN IF NOT EXISTS previous_listening_score INTEGER,
    ADD COLUMN IF NOT EXISTS new_listening_score INTEGER;

ALTER TABLE exam_grade_history
    DROP CONSTRAINT IF EXISTS chk_history_previous_reading_score,
    DROP CONSTRAINT IF EXISTS chk_history_new_reading_score,
    DROP CONSTRAINT IF EXISTS chk_history_previous_writing_score,
    DROP CONSTRAINT IF EXISTS chk_history_new_writing_score,
    DROP CONSTRAINT IF EXISTS chk_history_previous_listening_score,
    DROP CONSTRAINT IF EXISTS chk_history_new_listening_score;

ALTER TABLE exam_grade_history
    ADD CONSTRAINT chk_history_previous_reading_score
        CHECK (previous_reading_score IS NULL OR previous_reading_score BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_history_new_reading_score
        CHECK (new_reading_score IS NULL OR new_reading_score BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_history_previous_writing_score
        CHECK (previous_writing_score IS NULL OR previous_writing_score BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_history_new_writing_score
        CHECK (new_writing_score IS NULL OR new_writing_score BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_history_previous_listening_score
        CHECK (previous_listening_score IS NULL OR previous_listening_score BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_history_new_listening_score
        CHECK (new_listening_score IS NULL OR new_listening_score BETWEEN 0 AND 100);

COMMENT ON COLUMN exam_submissions.reading_score IS 'Reading grade as an integer from 0 to 100';
COMMENT ON COLUMN exam_submissions.writing_score IS 'Writing grade as an integer from 0 to 100';
COMMENT ON COLUMN exam_submissions.listening_score IS 'Listening grade as an integer from 0 to 100';
COMMENT ON COLUMN exam_submissions.score IS 'Final rounded grade, or legacy final-only grade';
