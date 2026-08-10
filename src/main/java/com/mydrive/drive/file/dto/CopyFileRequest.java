
package com.mydrive.drive.file.dto;

public record CopyFileRequest(
        java.util.UUID parentFolderId,
        @jakarta.validation.constraints.Size(max = 255)
        @jakarta.validation.constraints.Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String name
) {}
