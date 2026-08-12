
package com.mydrive.drive.file.dto;

public record FileVersionResponse(
        java.util.UUID id,
        java.util.UUID fileId,
        int versionNumber,
        String checksum,
        long size,
        java.util.UUID createdBy,
        java.time.Instant createdAt,
        java.util.UUID sourceDeviceId, // nullable until device support exists
        boolean current) {
}