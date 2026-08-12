 /*
 * PHASE 5: Upload-new-version and restore-version business logic.
 *
 * Suggested package:
 *   com.mydrive.drive.file
 *
 * Useful imports/annotations:
 *   com.mydrive.drive.checksum.ChecksumService
 *   com.mydrive.drive.file.dto.FileVersionResponse
 *   com.mydrive.drive.security.CurrentUserService
 *   com.mydrive.drive.storage.StorageKeyFactory
 *   com.mydrive.drive.storage.StorageService
 *   org.springframework.stereotype.Service
 *   org.springframework.transaction.support.TransactionTemplate
 *   org.springframework.web.multipart.MultipartFile
 *   java.time.Instant
 *   java.util.UUID
 *
 * Dependencies:
 *   DriveFileRepository
 *   FileVersionRepository
 *   CurrentUserService
 *   StorageService
 *   StorageKeyFactory
 *   ChecksumService
 *   TransactionTemplate
 *
 * Method 1:
 *   FileVersionResponse uploadVersion(UUID fileId, MultipartFile file)
 *
 * Upload flow:
 *   1. Resolve the authenticated owner and load their active READY DriveFile.
 *   2. Safely copy the MultipartFile to one local temporary file.
 *   3. Calculate checksum and size from that temporary file.
 *   4. Choose nextVersion = currentVersion + 1 while using optimistic locking.
 *   5. Create a NEW immutable MinIO key with StorageKeyFactory.versionKey().
 *   6. Save bytes without overwriting any previous version object.
 *   7. Insert a new FileVersion row.
 *   8. Update DriveFile currentVersion, checksum, size, contentType, and
 *      updatedAt in one metadata transaction.
 *   9. Clean up the local temporary file in finally.
 *
 * Method 2:
 *   FileVersionResponse restoreVersion(UUID fileId, int versionNumber)
 *
 * Critical restore rule:
 *   Do NOT merely point currentVersion backward. Restoring version 2 when the
 *   current version is 5 should create NEW version 6 by copying version 2's
 *   object to the version-6 key. This preserves immutable audit history.
 *
 * Restore flow:
 *   1. Verify file ownership before reading a version/storage key.
 *   2. Find the selected version by fileId + versionNumber.
 *   3. Copy its MinIO object to a new next-version key.
 *   4. Insert the new FileVersion with createdBy=current user.
 *   5. Promote the new version in DriveFile.
 *   6. If storage fails, do not report success or advance currentVersion.
 *
 * sourceDeviceId is null for now. Phase 7 can populate it for sync clients.
 */
 package com.mydrive.drive.file;

 import com.mydrive.drive.checksum.ChecksumService;
 import com.mydrive.drive.file.dto.FileVersionResponse;
 import com.mydrive.drive.security.CurrentUserService;
 import com.mydrive.drive.device.CurrentDeviceService;
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
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.nio.file.StandardCopyOption;
 import java.time.Instant;
 import java.util.Objects;
 import java.util.UUID;

 @Service
 public class FileVersionCommandService {
     /* New versions record their source device and an UPDATED sync event. */
     private static final Logger logger = LoggerFactory.getLogger(FileVersionCommandService.class);
     private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

     private final DriveFileRepository driveFileRepository;
     private final FileVersionRepository fileVersionRepository;
     private final CurrentUserService currentUserService;
     private final StorageService storageService;
     private final StorageKeyFactory storageKeyFactory;
     private final ChecksumService checksumService;
     private final TransactionTemplate transactionTemplate;
     private final CurrentDeviceService currentDeviceService;
     private final SyncChangeService syncChangeService;

     public FileVersionCommandService(
             DriveFileRepository driveFileRepository,
             FileVersionRepository fileVersionRepository,
             CurrentUserService currentUserService,
             StorageService storageService,
             StorageKeyFactory storageKeyFactory,
             ChecksumService checksumService,
             TransactionTemplate transactionTemplate,
             CurrentDeviceService currentDeviceService,
             SyncChangeService syncChangeService) {
         this.driveFileRepository = driveFileRepository;
         this.fileVersionRepository = fileVersionRepository;
         this.currentUserService = currentUserService;
         this.storageService = storageService;
         this.storageKeyFactory = storageKeyFactory;
         this.checksumService = checksumService;
         this.transactionTemplate = transactionTemplate;
         this.currentDeviceService = currentDeviceService;
         this.syncChangeService = syncChangeService;
     }

     public FileVersionResponse uploadVersion(UUID fileId, MultipartFile multipartFile) {
         if (fileId == null) {
             throw new IllegalArgumentException("File id cannot be null");
         }
         if (multipartFile == null) {
             throw new IllegalArgumentException("A file is required");
         }

         UUID ownerId = currentUserService.requireCurrentUser().getId();
         DriveFile driveFile = requireOwnedActiveReadyFile(fileId, ownerId);
         int nextVersion = driveFile.getCurrentVersion() + 1;
         String contentType = normalizeContentType(multipartFile.getContentType());
         String storageKey = storageKeyFactory.versionKey(ownerId, fileId, nextVersion);

         Path localTemporaryFile = null;
         boolean storageAttempted = false;
         boolean metadataCommitted = false;

         try {
             localTemporaryFile = Files.createTempFile("drive-version-upload-", ".tmp");
             try (InputStream input = multipartFile.getInputStream()) {
                 Files.copy(input, localTemporaryFile, StandardCopyOption.REPLACE_EXISTING);
             }

             long size = Files.size(localTemporaryFile);
             String checksum;
             try (InputStream input = Files.newInputStream(localTemporaryFile)) {
                 checksum = checksumService.sha256(input);
             }

             storageAttempted = true;
             try (InputStream input = Files.newInputStream(localTemporaryFile)) {
                 storageService.save(storageKey, input, size, contentType);
             }

             Instant now = Instant.now();
             FileVersion newVersion = new FileVersion(
                     UUID.randomUUID(),
                     fileId,
                     nextVersion,
                     storageKey,
                     checksum,
                     size,
                     ownerId,
                     now,
                     currentDeviceService.currentDeviceId().orElse(null));

             FileVersion savedVersion = Objects.requireNonNull(
                     transactionTemplate.execute(status -> {
                         FileVersion saved = fileVersionRepository.save(newVersion);
                         driveFile.promoteVersion(
                                 nextVersion,
                                 checksum,
                                 size,
                                 contentType,
                                 now);
                         driveFileRepository.save(driveFile);
                         syncChangeService.recordFileChange(
                                 driveFile, SyncOperation.UPDATED, null);
                         return saved;
                     }),
                     "Version metadata transaction returned null");

             metadataCommitted = true;
             return toResponse(savedVersion, true);
         } catch (IOException exception) {
             throw new StorageException("Could not upload a new version for file " + fileId, exception);
         } finally {
             if (storageAttempted && !metadataCommitted) {
                 deleteStoredVersion(storageKey);
             }
             deleteLocalTemporaryFile(localTemporaryFile);
         }
     }

     public FileVersionResponse restoreVersion(UUID fileId, int versionNumber) {
         if (fileId == null) {
             throw new IllegalArgumentException("File id cannot be null");
         }
         if (versionNumber < 1) {
             throw new IllegalArgumentException("Version number must be 1 or greater");
         }

         UUID ownerId = currentUserService.requireCurrentUser().getId();
         DriveFile driveFile = requireOwnedActiveReadyFile(fileId, ownerId);
         FileVersion selectedVersion = fileVersionRepository
                 .findByFileIdAndVersionNumber(fileId, versionNumber)
                 .orElseThrow(() -> new FileVersionNotFoundException(fileId, versionNumber));

         int nextVersion = driveFile.getCurrentVersion() + 1;
         String newStorageKey = storageKeyFactory.versionKey(ownerId, fileId, nextVersion);
         Instant now = Instant.now();
         boolean storageAttempted = false;
         boolean metadataCommitted = false;

         try {
             storageAttempted = true;
             storageService.copy(selectedVersion.getStorageKey(), newStorageKey);

             FileVersion restoredVersion = new FileVersion(
                     UUID.randomUUID(),
                     fileId,
                     nextVersion,
                     newStorageKey,
                     selectedVersion.getChecksum(),
                     selectedVersion.getSize(),
                     ownerId,
                     now,
                     currentDeviceService.currentDeviceId().orElse(null));

             FileVersion savedVersion = Objects.requireNonNull(
                     transactionTemplate.execute(status -> {
                         FileVersion saved = fileVersionRepository.save(restoredVersion);
                         driveFile.promoteVersion(
                                 nextVersion,
                                 selectedVersion.getChecksum(),
                                 selectedVersion.getSize(),
                                 driveFile.getContentType(),
                                 now);
                         driveFileRepository.save(driveFile);
                         syncChangeService.recordFileChange(
                                 driveFile, SyncOperation.UPDATED, null);
                         return saved;
                     }),
                     "Restored version metadata transaction returned null");

             metadataCommitted = true;
             return toResponse(savedVersion, true);
         } finally {
             if (storageAttempted && !metadataCommitted) {
                 deleteStoredVersion(newStorageKey);
             }
         }
     }

     private DriveFile requireOwnedActiveReadyFile(UUID fileId, UUID ownerId) {
         DriveFile driveFile = driveFileRepository.findByIdAndOwnerId(fileId, ownerId)
                 .orElseThrow(() -> new FileNotFoundException(fileId));
         if (driveFile.isDeleted() || driveFile.getUploadStatus() != UploadStatus.READY) {
             throw new FileNotFoundException(fileId);
         }
         return driveFile;
     }

     private String normalizeContentType(String contentType) {
         String normalized = contentType == null || contentType.isBlank()
                 ? DEFAULT_CONTENT_TYPE
                 : contentType.trim();
         if (normalized.length() > 255) {
             throw new IllegalArgumentException("Content type is longer than 255 characters");
         }
         return normalized;
     }

     private FileVersionResponse toResponse(FileVersion version, boolean current) {
         return new FileVersionResponse(
                 version.getId(),
                 version.getFileId(),
                 version.getVersionNumber(),
                 version.getChecksum(),
                 version.getSize(),
                 version.getCreatedBy(),
                 version.getCreatedAt(),
                 version.getSourceDeviceId(),
                 current);
     }

     private void deleteStoredVersion(String storageKey) {
         try {
             storageService.delete(storageKey);
         } catch (RuntimeException exception) {
             logger.warn("Could not clean up uncommitted version object {}", storageKey, exception);
         }
     }

     private void deleteLocalTemporaryFile(Path path) {
         if (path == null) {
             return;
         }
         try {
             Files.deleteIfExists(path);
         } catch (IOException exception) {
             logger.warn("Could not delete local version-upload temporary file", exception);
         }
     }
 }
