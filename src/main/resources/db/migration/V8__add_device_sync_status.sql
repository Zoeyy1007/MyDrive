ALTER TABLE devices
    ADD COLUMN selected_folder_id UUID,
    ADD COLUMN last_processed_sequence BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_sync_at TIMESTAMPTZ,
    ADD CONSTRAINT fk_devices_selected_folder
        FOREIGN KEY (selected_folder_id) REFERENCES folders (id) ON DELETE SET NULL,
    ADD CONSTRAINT ck_devices_last_processed_sequence
        CHECK (last_processed_sequence >= 0);

CREATE INDEX idx_devices_selected_folder
    ON devices (selected_folder_id);
