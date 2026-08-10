
package com.mydrive.drive.file;

import com.mydrive.drive.file.dto.CopyFileRequest;
import com.mydrive.drive.file.dto.FileResponse;
import com.mydrive.drive.file.dto.MoveFileRequest;
import com.mydrive.drive.file.dto.RenameFileRequest;
import com.mydrive.drive.folder.Folder;
import com.mydrive.drive.folder.FolderNotFoundException;
import com.mydrive.drive.folder.FolderRepository;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.storage.StorageKeyFactory;
import com.mydrive.drive.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
import java.util.UUID;

@Service
public class FileCommandService{
    private final DriveFileRepository driveFileRepository;
    private final CurrentUserService currentUserService;
    private final StorageService storageService;
    private final StorageKeyFactory storageKeyFactory;
    private final FileVersionRepository fileVersionRepository;
    private final FolderRepository folderRepository;

    public FileCommandService(
            DriveFileRepository driveFileRepository,
            CurrentUserService currentUserService,
            StorageService storageService,
            StorageKeyFactory storageKeyFactory,
            FileVersionRepository fileVersionRepository,
            FolderRepository folderRepository) {
        this.driveFileRepository = driveFileRepository;
        this.currentUserService = currentUserService;
        this.storageService = storageService;
        this.storageKeyFactory = storageKeyFactory;
        this.fileVersionRepository = fileVersionRepository;
        this.folderRepository = folderRepository;
    }

    @Transactional
    public FileResponse rename(UUID fileId, RenameFileRequest request){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        DriveFile file = requireOwnedReadyFile(fileId, ownerId, false);
        file.rename(request.name(), Instant.now());
        return toResponse(driveFileRepository.save(file));
    }

    @Transactional
    public FileResponse move(UUID fileId, MoveFileRequest request){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        DriveFile file = requireOwnedReadyFile(fileId, ownerId, false);
        validateDestination(request.parentFolderId(), ownerId);
        file.move(request.parentFolderId(), Instant.now());
        return toResponse(driveFileRepository.save(file));
    }

    public FileResponse copy(UUID fileId, CopyFileRequest request){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        DriveFile source = requireOwnedReadyFile(fileId, ownerId, false);
        validateDestination(request.parentFolderId(), ownerId);
        FileVersion sourceVersion = fileVersionRepository.findByFileIdAndVersionNumber(fileId, source.getCurrentVersion())
                .orElseThrow(() -> new FileNotFoundException(fileId));
        var newFileId = UUID.randomUUID();
        String newName = request.name() != null ? request.name() : source.getName();
        String newStorageKey = storageKeyFactory.versionKey(ownerId, newFileId, 1);
        Instant now = Instant.now();
        DriveFile copiedFile = new DriveFile(
                newFileId,
                ownerId,
                request.parentFolderId(),
                newName,
                source.getContentType(),
                source.getSize(),
                source.getChecksum(),
                1,
                UploadStatus.PENDING,
                now,
                now,
                null
        );
        FileVersion copiedVersion = new FileVersion(
                UUID.randomUUID(),
                newFileId,
                1,
                newStorageKey,
                source.getChecksum(),
                source.getSize(),
                ownerId,
                now
        );
        driveFileRepository.save(copiedFile);
        fileVersionRepository.save(copiedVersion);
        try {
            storageService.copy(sourceVersion.getStorageKey(), newStorageKey);
            copiedFile.markReady();
            return toResponse(driveFileRepository.save(copiedFile));
        } catch (RuntimeException exception) {
            copiedFile.markFailed();
            driveFileRepository.save(copiedFile);
            throw exception;
        }
    }

    @Transactional
    public void moveToTrash(UUID fileId){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        DriveFile file = requireOwnedReadyFile(fileId, ownerId, false);
        file.moveToTrash(Instant.now());
        driveFileRepository.save(file);
    }

    @Transactional
    public FileResponse restore(UUID fileId){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        DriveFile file = requireOwnedReadyFile(fileId, ownerId, true);
        validateDestination(file.getParentFolderId(), ownerId);
        file.restore(Instant.now());
        return toResponse(driveFileRepository.save(file));
    }

    private DriveFile requireOwnedReadyFile(UUID fileId, UUID ownerId, boolean allowDeleted) {
        DriveFile file = driveFileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
        if (file.getUploadStatus() != UploadStatus.READY || (!allowDeleted && file.isDeleted())) {
            throw new FileNotFoundException(fileId);
        }
        return file;
    }

    private void validateDestination(UUID folderId, UUID ownerId) {
        if (folderId == null) {
            return;
        }
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));
        if (folder.isDeleted()) {
            throw new FolderNotFoundException(folderId);
        }
    }

    private FileResponse toResponse(DriveFile file) {
        return new FileResponse(
                file.getId(),
                file.getParentFolderId(),
                file.getName(),
                file.getContentType(),
                file.getSize(),
                file.getChecksum(),
                file.getCurrentVersion(),
                file.getUploadStatus(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
