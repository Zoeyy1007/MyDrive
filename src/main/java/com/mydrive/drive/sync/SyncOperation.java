/*
 * PHASE 7 SERVER enum:
 * CREATED, UPDATED, RENAMED, MOVED, DELETED, RESTORED.
 * Phase 8 will add explicit conflict operations/status.
 */

package com.mydrive.drive.sync;

public enum SyncOperation {
    CREATED,
    UPDATED,
    RENAMED,
    MOVED,
    DELETED,
    RESTORED
}