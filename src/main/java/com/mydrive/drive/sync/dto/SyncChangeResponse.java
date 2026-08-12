/*
 * PHASE 7 SERVER response record matching the safe SyncChange fields:
 * sequence, resourceType, resourceId, operation, relativePath,
 * previousRelativePath, versionNumber, sourceDeviceId, occurredAt.
 * Do not include userId because the endpoint is already owner-scoped.
 */

package com.mydrive.drive.sync.dto;

public record SyncChangeResponse(
        long sequence,
        String resourceType,
        java.util.UUID resourceId,
        String operation,
        String relativePath,
        String previousRelativePath,
        Integer versionNumber,
        java.util.UUID sourceDeviceId,
        java.time.Instant occurredAt
){}
