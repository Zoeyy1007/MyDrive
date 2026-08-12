package com.mydrive.drive.device.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateDeviceFolderRequest(
        @NotNull UUID selectedFolderId
) {}
