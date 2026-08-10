
package com.mydrive.drive.file.dto;

import com.mydrive.drive.file.UploadStatus;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        UUID parentFolderId,
        String name,
        String contentType,
        long size,
        String checksum,
        int currentVersion,
        UploadStatus uploadStatus,
        Instant createdAt,
        Instant updatedAt
) {}
