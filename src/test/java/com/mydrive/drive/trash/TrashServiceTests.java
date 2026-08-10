package com.mydrive.drive.trash;

import com.mydrive.drive.file.DriveFile;
import com.mydrive.drive.file.DriveFileRepository;
import com.mydrive.drive.file.FileVersion;
import com.mydrive.drive.file.FileVersionRepository;
import com.mydrive.drive.file.UploadStatus;
import com.mydrive.drive.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrashServiceTests {

    @Mock DriveFileRepository driveFileRepository;
    @Mock FileVersionRepository fileVersionRepository;
    @Mock StorageService storageService;
    @Mock TransactionTemplate transactionTemplate;

    private TrashService trashService;

    @BeforeEach
    void setUp() {
        trashService = new TrashService(
                driveFileRepository,
                fileVersionRepository,
                storageService,
                transactionTemplate,
                30,
                100);
    }

    @Test
    void deletesStorageBeforeDatabaseMetadata() {
        DriveFile file = expiredFile();
        FileVersion version = version(file, "object-key");
        when(driveFileRepository.findAllByDeletedAtBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));
        when(fileVersionRepository.findAllByFileId(file.getId())).thenReturn(List.of(version));
        runTransactionCallbacksImmediately();

        int deletedCount = trashService.deleteExpiredTrash();

        assertThat(deletedCount).isEqualTo(1);
        InOrder order = inOrder(storageService, fileVersionRepository, driveFileRepository);
        order.verify(storageService).delete("object-key");
        order.verify(fileVersionRepository).deleteAll(List.of(version));
        order.verify(fileVersionRepository).flush();
        order.verify(driveFileRepository).delete(file);
    }

    @Test
    void storageFailureKeepsDatabaseMetadataForRetry() {
        DriveFile file = expiredFile();
        FileVersion version = version(file, "object-key");
        when(driveFileRepository.findAllByDeletedAtBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));
        when(fileVersionRepository.findAllByFileId(file.getId())).thenReturn(List.of(version));
        doThrow(new RuntimeException("MinIO unavailable"))
                .when(storageService).delete("object-key");

        int deletedCount = trashService.deleteExpiredTrash();

        assertThat(deletedCount).isZero();
        verify(fileVersionRepository, never()).deleteAll(any());
        verify(driveFileRepository, never()).delete(any(DriveFile.class));
    }

    private DriveFile expiredFile() {
        Instant now = Instant.now();
        return new DriveFile(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "old.txt",
                "text/plain",
                3,
                "a".repeat(64),
                1,
                UploadStatus.READY,
                now.minusSeconds(60),
                now,
                now.minusSeconds(31L * 24 * 60 * 60));
    }

    private FileVersion version(DriveFile file, String storageKey) {
        return new FileVersion(
                UUID.randomUUID(),
                file.getId(),
                1,
                storageKey,
                "a".repeat(64),
                3,
                file.getOwnerId(),
                Instant.now());
    }

    private TransactionStatus mockStatus() {
        return org.mockito.Mockito.mock(TransactionStatus.class);
    }

    private void runTransactionCallbacksImmediately() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mockStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }
}
