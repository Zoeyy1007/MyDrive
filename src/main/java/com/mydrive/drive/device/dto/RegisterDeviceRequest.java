/*
 * PHASE 7 SERVER request DTO.
 * Package: com.mydrive.drive.device.dto
 *
 * Create a public record with String name.
 * Add @NotBlank and @Size(max=100). The authenticated browser session supplies
 * the user; never accept userId from this request.
 */

package com.mydrive.drive.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RegisterDeviceRequest(
        @NotBlank
        @Size(max=100)
        String name,
        UUID selectedFolderId
){}
