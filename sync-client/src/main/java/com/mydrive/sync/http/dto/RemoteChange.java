package com.mydrive.sync.http.dto;

import java.time.Instant;
import java.util.UUID;

public record RemoteChange(
        long sequence,
        String resourceType,
        UUID resourceId,
        String operation,
        String relativePath,
        String previousRelativePath,
        Integer versionNumber,
        UUID sourceDeviceId,
        Instant occurredAt) {
}
