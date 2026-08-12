/*
 * PHASE 7 SERVER device-management endpoints.
 *
 * @RestController, @RequestMapping("/api/devices"). Dependency:
 * DeviceTokenService.
 *
 * POST /api/devices
 *   @Valid @RequestBody RegisterDeviceRequest -> 201 DeviceTokenResponse
 *   Requires an existing browser/Postman session login.
 *
 * GET /api/devices
 *   -> devices owned by the authenticated user, without secrets.
 *
 * DELETE /api/devices/{id}
 *   -> revoke owned device and return 204. Do not physically delete audit data.
 */

package com.mydrive.drive.device;

import com.mydrive.drive.device.dto.DeviceResponse;
import com.mydrive.drive.device.dto.DeviceTokenResponse;
import com.mydrive.drive.device.dto.RegisterDeviceRequest;
import com.mydrive.drive.device.dto.SyncProgressRequest;
import com.mydrive.drive.device.dto.UpdateDeviceFolderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final DeviceTokenService deviceTokenService;
    private final DeviceSyncService deviceSyncService;

    public DeviceController(
            DeviceTokenService deviceTokenService,
            DeviceSyncService deviceSyncService) {
        this.deviceTokenService = deviceTokenService;
        this.deviceSyncService = deviceSyncService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceTokenResponse registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request) {
        return deviceTokenService.register(request.name(), request.selectedFolderId());
    }

    @GetMapping
    public List<DeviceResponse> getDevices() {
        return deviceTokenService.listCurrentUsersDevices();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeDevice(@PathVariable UUID id) {
        deviceTokenService.revoke(id);
    }

    @PatchMapping("/{id}/folder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSelectedFolder(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDeviceFolderRequest request) {
        deviceSyncService.updateSelectedFolder(id, request.selectedFolderId());
    }

    @PutMapping("/current/sync-progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportProgress(@Valid @RequestBody SyncProgressRequest request) {
        deviceSyncService.reportProgress(request.lastProcessedSequence());
    }
}
