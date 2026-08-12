package com.mydrive.drive.device.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record SyncProgressRequest(
        @PositiveOrZero long lastProcessedSequence
) {}
