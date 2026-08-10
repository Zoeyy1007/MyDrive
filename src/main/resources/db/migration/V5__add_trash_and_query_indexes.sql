ALTER TABLE files
    ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE folders
    ADD COLUMN deleted_at TIMESTAMPTZ;

CREATE INDEX idx_files_owner_parent_deleted
    ON files (owner_id, parent_folder_id, deleted_at);

CREATE INDEX idx_files_owner_content_type_deleted
    ON files (owner_id, content_type, deleted_at);

CREATE INDEX idx_files_owner_created_at
    ON files (owner_id, created_at);

CREATE INDEX idx_files_owner_size
    ON files (owner_id, size);

CREATE INDEX idx_folders_owner_parent_deleted
    ON folders (owner_id, parent_id, deleted_at);
