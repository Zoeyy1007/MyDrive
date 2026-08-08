CREATE TABLE folders (
    id UUID PRIMARY KEY,
    parent_id UUID,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_folders_parent
        FOREIGN KEY (parent_id)
        REFERENCES folders (id)
        ON DELETE RESTRICT
);

CREATE INDEX idx_folders_parent_id ON folders (parent_id);
