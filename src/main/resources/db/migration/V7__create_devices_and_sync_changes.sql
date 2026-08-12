CREATE TABLE devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,

    CONSTRAINT fk_devices_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_devices_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_devices_name_not_blank CHECK (BTRIM(name) <> '')
);

CREATE INDEX idx_devices_user_revoked
    ON devices (user_id, revoked_at);

CREATE TABLE sync_changes (
    sequence BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    source_device_id UUID,
    resource_type VARCHAR(10) NOT NULL,
    resource_id UUID NOT NULL,
    operation VARCHAR(20) NOT NULL,
    relative_path VARCHAR(4096) NOT NULL,
    previous_relative_path VARCHAR(4096),
    version_number INTEGER,
    occurred_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_sync_changes_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_sync_changes_source_device
        FOREIGN KEY (source_device_id) REFERENCES devices (id) ON DELETE SET NULL,
    CONSTRAINT ck_sync_changes_resource_type
        CHECK (resource_type IN ('FILE', 'FOLDER')),
    CONSTRAINT ck_sync_changes_operation
        CHECK (operation IN ('CREATED', 'UPDATED', 'RENAMED', 'MOVED', 'DELETED', 'RESTORED')),
    CONSTRAINT ck_sync_changes_version_number
        CHECK (version_number IS NULL OR version_number >= 1)
);

CREATE INDEX idx_sync_changes_user_sequence
    ON sync_changes (user_id, sequence);

ALTER TABLE file_versions
    ADD CONSTRAINT fk_file_versions_source_device
        FOREIGN KEY (source_device_id)
        REFERENCES devices (id)
        ON DELETE SET NULL;
