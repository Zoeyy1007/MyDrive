/*
 * PHASE 7 SERVER safe device DTO.
 * Suggested fields: UUID id, String name, Instant createdAt,
 * Instant lastSeenAt, Instant revokedAt.
 * Do not include tokenHash or the raw token.
 */
package com.mydrive.drive.device.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String name,
        Instant createdAt,
        Instant lastSeenAt,
        Instant revokedAt,
        UUID selectedFolderId,
        long lastProcessedSequence,
        long latestSequence,
        Instant lastSyncAt,
        String syncStatus
) {}
