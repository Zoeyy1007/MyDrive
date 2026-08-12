ALTER TABLE files
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_files_lock_version_nonnegative
        CHECK (lock_version >= 0);

ALTER TABLE file_versions
    ADD COLUMN source_device_id UUID;

-- V4 already created idx_file_versions_file_number on
-- (file_id, version_number). PostgreSQL can scan that B-tree index backward
-- for newest-first history, so a second descending index would be redundant.
