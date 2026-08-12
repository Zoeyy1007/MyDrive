PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS local_files (
    relative_path TEXT PRIMARY KEY,
    remote_resource_id TEXT,
    checksum TEXT,
    remote_version INTEGER,
    size INTEGER NOT NULL,
    modified_millis INTEGER NOT NULL,
    sync_status TEXT NOT NULL,
    last_synced_at TEXT
);

CREATE TABLE IF NOT EXISTS sync_cursor (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    last_sequence INTEGER NOT NULL
);

INSERT OR IGNORE INTO sync_cursor (id, last_sequence) VALUES (1, 0);

CREATE TABLE IF NOT EXISTS pending_operations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operation_type TEXT NOT NULL,
    relative_path TEXT NOT NULL,
    payload_json TEXT,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pending_operations_due
    ON pending_operations (next_attempt_at, id);
