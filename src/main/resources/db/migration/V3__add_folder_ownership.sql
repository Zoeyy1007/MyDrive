DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM folders) THEN
        RAISE EXCEPTION
            'Cannot add required folder ownership while folders already exist';
    END IF;
END
$$;

ALTER TABLE folders
    ADD COLUMN owner_id UUID NOT NULL,
    ADD CONSTRAINT fk_folders_owner
        FOREIGN KEY (owner_id)
        REFERENCES users (id)
        ON DELETE RESTRICT;

CREATE INDEX idx_folders_owner_id
    ON folders (owner_id);

CREATE INDEX idx_folders_owner_parent_id
    ON folders (owner_id, parent_id);
