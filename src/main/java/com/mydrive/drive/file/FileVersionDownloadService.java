
package com.mydrive.drive.file;

import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FileVersionDownloadService{
    private final DriveFileRepository driveFileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final CurrentUserService currentUserService;
    private final StorageService storageService;

    public FileVersionDownloadService(
            DriveFileRepository driveFileRepository,
            FileVersionRepository fileVersionRepository,
            CurrentUserService currentUserService,
            StorageService storageService) {
        this.driveFileRepository = driveFileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.currentUserService = currentUserService;
        this.storageService = storageService;
    }

    public FileDownload downloadVersion(UUID fileId, int versionNumber) {
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        DriveFile file = driveFileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        if (file.isDeleted() || file.getUploadStatus() != UploadStatus.READY) {
            throw new FileNotFoundException(fileId);
        }

        FileVersion version = fileVersionRepository.findByFileIdAndVersionNumber(fileId, versionNumber)
                .orElseThrow(() -> new FileVersionNotFoundException(fileId, versionNumber));

        var inputStream = storageService.load(version.getStorageKey());
        return new FileDownload(
                inputStream,
                file.getName(),
                file.getContentType(),
                version.getSize());
    }
}
