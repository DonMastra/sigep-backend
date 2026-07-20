-- Repair only accounts created by the legacy insert-test-data.sql hash.
-- The previous checksum did not match the documented development password.
UPDATE users
SET password = '$2a$12$s8S1ftMF00C/p/vQMbgyfemtqzTqdWFkhuuhsOzAHjV7.k2S9kzYm',
    updated_at = NOW()
WHERE password = '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYIr6E8h.9i';
