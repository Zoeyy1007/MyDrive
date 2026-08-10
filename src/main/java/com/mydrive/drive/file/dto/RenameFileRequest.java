
package com.mydrive.drive.file.dto;

public record RenameFileRequest(
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 255)
        String name
) {}