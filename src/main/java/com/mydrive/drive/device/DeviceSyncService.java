package com.mydrive.drive.device;

import com.mydrive.drive.folder.Folder;
import com.mydrive.drive.folder.FolderNotFoundException;
import com.mydrive.drive.folder.FolderRepository;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.sync.SyncChangeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DeviceSyncService {
    private final DeviceRepository deviceRepository;
    private final FolderRepository folderRepository;
    private final SyncChangeRepository syncChangeRepository;
    private final CurrentUserService currentUserService;
    private final CurrentDeviceService currentDeviceService;

    public DeviceSyncService(
            DeviceRepository deviceRepository,
            FolderRepository folderRepository,
            SyncChangeRepository syncChangeRepository,
            CurrentUserService currentUserService,
            CurrentDeviceService currentDeviceService) {
        this.deviceRepository = deviceRepository;
        this.folderRepository = folderRepository;
        this.syncChangeRepository = syncChangeRepository;
        this.currentUserService = currentUserService;
        this.currentDeviceService = currentDeviceService;
    }

    @Transactional
    public void updateSelectedFolder(UUID deviceId, UUID folderId) {
        UUID userId = currentUserService.requireCurrentUser().getId();
        Device device = requireOwnedDevice(deviceId, userId);
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));
        if (folder.isDeleted()) {
            throw new FolderNotFoundException(folderId);
        }
        device.selectFolder(folderId);
        deviceRepository.save(device);
    }

    @Transactional
    public void reportProgress(long sequence) {
        UUID deviceId = currentDeviceService.currentDeviceId()
                .orElseThrow(() -> new AccessDeniedException(
                        "A device token is required to report sync progress"));
        UUID userId = currentUserService.requireCurrentUser().getId();
        Device device = requireOwnedDevice(deviceId, userId);
        long latestSequence = syncChangeRepository
                .findFirstByUserIdOrderBySequenceDesc(userId)
                .map(com.mydrive.drive.sync.SyncChange::getSequence)
                .orElse(0L);
        if (sequence > latestSequence) {
            throw new IllegalArgumentException(
                    "Sync cursor cannot be greater than the latest server sequence");
        }
        device.acknowledge(sequence, Instant.now());
        deviceRepository.save(device);
    }

    private Device requireOwnedDevice(UUID deviceId, UUID userId) {
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));
        if (device.isRevoked()) {
            throw new AccessDeniedException("Device has been revoked");
        }
        return device;
    }
}
