/*
 * PHASE 7 SERVER helper for optional device context.
 *
 * Annotate with @Service. Read SecurityContextHolder.getContext()
 * .getAuthentication().getPrincipal().
 *
 * Add:
 *   Optional<UUID> currentDeviceId()
 *
 * Return the DevicePrincipal.deviceId when authentication came from a device
 * token. Return Optional.empty() for browser/Postman session authentication.
 * FileVersion.sourceDeviceId and SyncChange.sourceDeviceId use this value.
 */

package com.mydrive.drive.device;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;


@Service
public class CurrentDeviceService{
    public Optional<UUID> currentDeviceId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof DevicePrincipal devicePrincipal) {
            return Optional.of(devicePrincipal.deviceId());
        }
        return Optional.empty();
    }
}