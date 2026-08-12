package com.mydrive.sync.state;

import java.time.Instant;
import java.util.UUID;

public record LocalFileState(
        String relativePath,
        UUID remoteResourceId,
        String checksum,
        Integer remoteVersion,
        long size,
        long modifiedMillis,
        SyncStatus status,
        Instant lastSyncedAt) {
}
