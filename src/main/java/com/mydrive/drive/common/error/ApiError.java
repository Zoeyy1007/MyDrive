
package com.mydrive.drive.common.error;

public record ApiError(
        java.time.Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {}