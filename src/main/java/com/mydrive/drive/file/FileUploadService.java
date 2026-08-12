
package com.mydrive.drive.file;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.checksum.ChecksumService;
import com.mydrive.drive.file.dto.FileResponse;
import com.mydrive.drive.folder.FolderRepository;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.storage.StorageKeyFactory;
import com.mydrive.drive.storage.StorageException;
import com.mydrive.drive.storage.StorageService;
import com.mydrive.drive.sync.SyncChangeService;
import com.mydrive.drive.sync.SyncOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class FileUploadService {
    /* A successful finalization records CREATED in the metadata transaction. */
    private static final Logger logger = LoggerFactory.getLogger(FileUploadService.class);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final FileVersionRepository fileVersionRepository;
    private final DriveFileRepository driveFileRepository;
    private final FolderRepository folderRepository;
    private final CurrentUserService currentUserService;
    private final StorageService storageService;
    private final StorageKeyFactory storageKeyFactory;
    private final ChecksumService checksumService;
    private final TransactionTemplate transactionTemplate;
    private final SyncChangeService syncChangeService;

    public FileUploadService(
            FileVersionRepository fileVersionRepository,
            DriveFileRepository driveFileRepository,
            FolderRepository folderRepository,
            CurrentUserService currentUserService,
            StorageService storageService,
            StorageKeyFactory storageKeyFactory,
            ChecksumService checksumService,
            TransactionTemplate transactionTemplate,
            SyncChangeService syncChangeService) {
        this.fileVersionRepository = fileVersionRepository;
        this.driveFileRepository = driveFileRepository;
        this.folderRepository = folderRepository;
        this.currentUserService = currentUserService;
        this.storageService = storageService;
        this.storageKeyFactory = storageKeyFactory;
        this.checksumService = checksumService;
        this.transactionTemplate = transactionTemplate;
        this.syncChangeService = syncChangeService;
    }

    public FileResponse upload(UUID parentFolderId, MultipartFile multipartFile) {
        AppUser currentUser = currentUserService.requireCurrentUser();
        UUID ownerId = currentUser.getId();
        String filename = validateAndExtractFilename(multipartFile);
        long size = multipartFile.getSize();
        String contentType = normalizeContentType(multipartFile.getContentType());

        validateParentOwnership(parentFolderId, ownerId);

        UUID uploadId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        int versionNumber = 1;
        String temporaryKey = storageKeyFactory.temporaryKey(ownerId, uploadId);
        String finalKey = storageKeyFactory.versionKey(ownerId, fileId, versionNumber);

        Path localTemporaryFile = null;
        boolean temporaryObjectAttempted = false;
        DriveFile driveFile = null;

        try {
            localTemporaryFile = Files.createTempFile("drive-upload-", ".tmp");
            try (InputStream input = multipartFile.getInputStream()) {
                Files.copy(input, localTemporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String checksum;
            try (InputStream input = Files.newInputStream(localTemporaryFile)) {
                checksum = checksumService.sha256(input);
            }

            temporaryObjectAttempted = true;
            try (InputStream input = Files.newInputStream(localTemporaryFile)) {
                storageService.save(temporaryKey, input, size, contentType);
            }

            Instant now = Instant.now();
            driveFile = new DriveFile(
                    fileId,
                    ownerId,
                    parentFolderId,
                    filename,
                    contentType,
                    size,
                    checksum,
                    versionNumber,
                    UploadStatus.PENDING,
                    now,
                    now,
                    null
            );
            FileVersion version = new FileVersion(
                    UUID.randomUUID(),
                    fileId,
                    versionNumber,
                    finalKey,
                    checksum,
                    size,
                    ownerId,
                    now
            );

            DriveFile pendingFile = driveFile;
            driveFile = Objects.requireNonNull(
                    transactionTemplate.execute(status -> {
                        DriveFile saved = driveFileRepository.save(pendingFile);
                        fileVersionRepository.save(version);
                        return saved;
                    }),
                    "Pending file transaction returned null"
            );

            storageService.copy(temporaryKey, finalKey);
            verifyFinalObject(finalKey, size);

            DriveFile readyFile = driveFile;
            driveFile = Objects.requireNonNull(
                    transactionTemplate.execute(status -> {
                        readyFile.markReady();
                        DriveFile saved = driveFileRepository.save(readyFile);
                        syncChangeService.recordFileChange(saved, SyncOperation.CREATED, null);
                        return saved;
                    }),
                    "Ready file transaction returned null"
            );

            return toResponse(driveFile);
        } catch (Exception exception) {
            if (driveFile != null) {
                markFailedWithoutMaskingOriginalFailure(driveFile, exception);
            }

            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new StorageException("Could not upload file " + filename, exception);
        } finally {
            if (temporaryObjectAttempted) {
                deleteTemporaryObject(temporaryKey);
            }
            deleteLocalTemporaryFile(localTemporaryFile);
        }
    }

    private String validateAndExtractFilename(MultipartFile multipartFile) {
        if (multipartFile == null) {
            throw new IllegalArgumentException("A file is required");
        }
        if (multipartFile.getSize() < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }

        String originalName = multipartFile.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Filename is required");
        }

        String normalized = originalName.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (filename.isBlank() || ".".equals(filename) || "..".equals(filename)) {
            throw new IllegalArgumentException("Filename is invalid");
        }
        if (filename.length() > 255) {
            throw new IllegalArgumentException("Filename is longer than 255 characters");
        }
        return filename;
    }

    private String normalizeContentType(String contentType) {
        String normalized = contentType == null || contentType.isBlank()
                ? DEFAULT_CONTENT_TYPE
                : contentType;
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("Content type is longer than 255 characters");
        }
        return normalized;
    }

    private void validateParentOwnership(UUID parentFolderId, UUID ownerId) {
        if (parentFolderId != null
                && folderRepository.findByIdAndOwnerId(parentFolderId, ownerId).isEmpty()) {
            throw new IllegalArgumentException("Parent folder does not belong to the current user");
        }
    }

    private void verifyFinalObject(String finalKey, long expectedSize) throws IOException {
        if (!storageService.exists(finalKey)) {
            throw new StorageException("Final object was not created: " + finalKey);
        }

        long actualSize;
        try (InputStream input = storageService.load(finalKey)) {
            actualSize = input.transferTo(OutputStream.nullOutputStream());
        }
        if (actualSize != expectedSize) {
            throw new StorageException(
                    "Final object size mismatch: expected " + expectedSize + " but was " + actualSize
            );
        }
    }

    private void markFailedWithoutMaskingOriginalFailure(
            DriveFile driveFile,
            Exception originalFailure) {
        try {
            transactionTemplate.execute(status -> {
                driveFile.markFailed();
                return driveFileRepository.save(driveFile);
            });
        } catch (RuntimeException markFailedException) {
            originalFailure.addSuppressed(markFailedException);
        }
    }

    private void deleteTemporaryObject(String temporaryKey) {
        try {
            storageService.delete(temporaryKey);
        } catch (RuntimeException exception) {
            logger.warn("Could not delete temporary upload object {}", temporaryKey, exception);
        }
    }

    private void deleteLocalTemporaryFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            logger.warn("Could not delete local temporary upload file", exception);
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
