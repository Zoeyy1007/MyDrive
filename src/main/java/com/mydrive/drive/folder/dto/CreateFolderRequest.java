
package com.mydrive.drive.folder.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreateFolderRequest(
        @NotBlank
        @jakarta.validation.constraints.Size(max = 255)
        String name,
        UUID parentId){

}