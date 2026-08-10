
package com.mydrive.drive.folder.dto;

public record RenameFolderRequest(
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 255)
        String name) {
}