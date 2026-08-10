package com.mydrive.drive.trash;

import com.mydrive.drive.file.DriveFile;
import com.mydrive.drive.file.DriveFileRepository;
import com.mydrive.drive.file.FileVersion;
import com.mydrive.drive.file.FileVersionRepository;
import com.mydrive.drive.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TrashService {
    private static final Logger logger = LoggerFactory.getLogger(TrashService.class);

    private final DriveFileRepository driveFileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final StorageService storageService;
    private final TransactionTemplate transactionTemplate;
    private final int retentionDays;
    private final int batchSize;

    public TrashService(
            DriveFileRepository driveFileRepository,
            FileVersionRepository fileVersionRepository,
            StorageService storageService,
            TransactionTemplate transactionTemplate,
            @Value("${app.trash.retention-days:30}") int retentionDays,
            @Value("${app.trash.cleanup-batch-size:100}") int batchSize) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("Trash retention days must be at least 1");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Trash cleanup batch size must be between 1 and 1000");
        }
        this.driveFileRepository = driveFileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.storageService = storageService;
        this.transactionTemplate = transactionTemplate;
        this.retentionDays = retentionDays;
        this.batchSize = batchSize;
    }

    /**
     * Deletes one small batch. Failed files keep their metadata and are retried
     * during a later run.
     */
    public int deleteExpiredTrash() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        var page = PageRequest.of(
                0,
                batchSize,
                Sort.by(Sort.Direction.ASC, "deletedAt"));
        List<DriveFile> expiredFiles = driveFileRepository
                .findAllByDeletedAtBefore(cutoff, page)
                .getContent();

        int deletedCount = 0;
        for (DriveFile file : expiredFiles) {
            try {
                permanentlyDelete(file);
                deletedCount++;
            } catch (RuntimeException exception) {
                logger.error(
                        "Could not permanently delete expired file {}; it will be retried",
                        file.getId(),
                        exception);
            }
        }
        return deletedCount;
    }

    private void permanentlyDelete(DriveFile file) {
        List<FileVersion> versions = fileVersionRepository.findAllByFileId(file.getId());

        // Delete bytes first. If this fails, database metadata remains so the
        // storage keys are still available for a later retry.
        for (FileVersion version : versions) {
            storageService.delete(version.getStorageKey());
        }

        transactionTemplate.executeWithoutResult(status -> {
            fileVersionRepository.deleteAll(versions);
            fileVersionRepository.flush();
            driveFileRepository.delete(file);
        });
    }
}
