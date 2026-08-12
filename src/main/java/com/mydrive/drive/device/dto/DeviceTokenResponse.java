/*
 * PHASE 7 SERVER one-time registration response.
 * Suggested fields: DeviceResponse device, String token.
 *
 * This is the ONLY response that returns the raw token. Explain to the caller
 * that it cannot be retrieved again; losing it means revoking the device and
 * registering a new one.
 */

package com.mydrive.drive.device.dto;

public record DeviceTokenResponse(
        DeviceResponse device,
        String token
) {}
