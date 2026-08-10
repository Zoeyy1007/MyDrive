CREATE TABLE files (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    parent_folder_id UUID,
    name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    checksum CHAR(64) NOT NULL,
    current_version INTEGER NOT NULL,
    upload_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_files_owner
        FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_files_parent_folder
        FOREIGN KEY (parent_folder_id) REFERENCES folders (id) ON DELETE RESTRICT,
    CONSTRAINT ck_files_size CHECK (size >= 0),
    CONSTRAINT ck_files_current_version CHECK (current_version >= 1),
    CONSTRAINT ck_files_upload_status
        CHECK (upload_status IN ('PENDING', 'READY', 'FAILED'))
);

CREATE INDEX idx_files_owner_parent_folder
    ON files (owner_id, parent_folder_id);

CREATE TABLE file_versions (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    checksum CHAR(64) NOT NULL,
    size BIGINT NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_file_versions_file
        FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE RESTRICT,
    CONSTRAINT fk_file_versions_created_by
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_file_versions_storage_key UNIQUE (storage_key),
    CONSTRAINT uq_file_versions_number UNIQUE (file_id, version_number),
    CONSTRAINT ck_file_versions_number CHECK (version_number >= 1),
    CONSTRAINT ck_file_versions_size CHECK (size >= 0)
);

CREATE INDEX idx_file_versions_file_number
    ON file_versions (file_id, version_number);
