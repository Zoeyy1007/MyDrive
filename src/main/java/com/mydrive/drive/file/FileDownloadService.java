
package com.mydrive.drive.file;

import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.storage.StorageService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FileDownloadService{
    private final CurrentUserService currentUserService;
    private final DriveFileRepository driveFileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final StorageService storageService;

    public FileDownloadService(CurrentUserService currentUserService, DriveFileRepository driveFileRepository, FileVersionRepository fileVersionRepository, StorageService storageService) {
        this.currentUserService = currentUserService;
        this.driveFileRepository = driveFileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.storageService = storageService;
    }

    public FileDownload download(UUID fileId){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        DriveFile file = driveFileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
        if (file.isDeleted() || file.getUploadStatus() != UploadStatus.READY) {
            throw new FileNotFoundException(fileId);
        }

        FileVersion fileVersion = fileVersionRepository.findByFileIdAndVersionNumber(file.getId(), file.getCurrentVersion())
                .orElseThrow(() -> new FileNotFoundException(fileId));

        var inputStream = storageService.load(fileVersion.getStorageKey());

        return new FileDownload(
                inputStream,
                file.getName(),
                file.getContentType(),
                file.getSize()
        );
    }
}
