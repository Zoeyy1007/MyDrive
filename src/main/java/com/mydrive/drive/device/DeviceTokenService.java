/*
 * PHASE 7 SERVER device registration, validation, listing, and revocation.
 * Raw device tokens are returned once and only their SHA-256 hashes are stored.
 */
package com.mydrive.drive.device;

import com.mydrive.drive.account.AppUserRepository;
import com.mydrive.drive.device.dto.DeviceResponse;
import com.mydrive.drive.device.dto.DeviceTokenResponse;
import com.mydrive.drive.folder.Folder;
import com.mydrive.drive.folder.FolderNotFoundException;
import com.mydrive.drive.folder.FolderRepository;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.sync.SyncChangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceTokenService {
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeviceRepository deviceRepository;
    private final CurrentUserService currentUserService;
    private final AppUserRepository appUserRepository;
    private final FolderRepository folderRepository;
    private final SyncChangeRepository syncChangeRepository;

    public DeviceTokenService(
            DeviceRepository deviceRepository,
            CurrentUserService currentUserService,
            AppUserRepository appUserRepository,
            FolderRepository folderRepository,
            SyncChangeRepository syncChangeRepository) {
        this.deviceRepository = deviceRepository;
        this.currentUserService = currentUserService;
        this.appUserRepository = appUserRepository;
        this.folderRepository = folderRepository;
        this.syncChangeRepository = syncChangeRepository;
    }

    @Transactional
    public DeviceTokenResponse register(String name, UUID selectedFolderId) {
        String normalizedName = validateName(name);
        var currentUser = currentUserService.requireCurrentUser();
        validateSelectedFolder(selectedFolderId, currentUser.getId());

        byte[] randomBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        Instant now = Instant.now();
        Device device = new Device(
                UUID.randomUUID(),
                currentUser.getId(),
                normalizedName,
                hashToken(rawToken),
                now,
                null,
                null,
                selectedFolderId,
                0,
                null);

        Device savedDevice = deviceRepository.save(device);
        return new DeviceTokenResponse(
                toResponse(savedDevice, latestSequence(currentUser.getId())), rawToken);
    }

    @Transactional
    public Optional<DevicePrincipal> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        Optional<Device> foundDevice = deviceRepository
                .findByTokenHashAndRevokedAtIsNull(hashToken(rawToken));
        if (foundDevice.isEmpty()) {
            return Optional.empty();
        }

        Device device = foundDevice.get();
        return appUserRepository.findById(device.getUserId())
                .map(appUser -> {
                    device.touch(Instant.now());
                    deviceRepository.save(device);
                    return new DevicePrincipal(
                            device.getId(),
                            appUser.getId(),
                            appUser.getEmail());
                });
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> listCurrentUsersDevices() {
        UUID userId = currentUserService.requireCurrentUser().getId();
        long latestSequence = latestSequence(userId);
        return deviceRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(device -> toResponse(device, latestSequence))
                .toList();
    }

    @Transactional
    public void revoke(UUID deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("Device id cannot be null");
        }

        UUID userId = currentUserService.requireCurrentUser().getId();
        Device device = deviceRepository.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));
        if (!device.isRevoked()) {
            device.revoke(Instant.now());
            deviceRepository.save(device);
        }
    }

    private String hashToken(String rawToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Device name cannot be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Device name cannot exceed 100 characters");
        }
        return normalized;
    }

    private void validateSelectedFolder(UUID folderId, UUID userId) {
        if (folderId == null) {
            return;
        }
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, userId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));
        if (folder.isDeleted()) {
            throw new FolderNotFoundException(folderId);
        }
    }

    private long latestSequence(UUID userId) {
        return syncChangeRepository.findFirstByUserIdOrderBySequenceDesc(userId)
                .map(com.mydrive.drive.sync.SyncChange::getSequence)
                .orElse(0L);
    }

    private DeviceResponse toResponse(Device device, long latestSequence) {
        String status;
        if (device.isRevoked()) {
            status = "REVOKED";
        } else if (device.getLastSyncAt() == null) {
            status = "NEVER_SYNCED";
        } else if (device.getLastProcessedSequence() >= latestSequence) {
            status = "UP_TO_DATE";
        } else {
            status = "BEHIND";
        }
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getCreatedAt(),
                device.getLastSeenAt(),
                device.getRevokedAt(),
                device.getSelectedFolderId(),
                device.getLastProcessedSequence(),
                latestSequence,
                device.getLastSyncAt(),
                status);
    }
}
