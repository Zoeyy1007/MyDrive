package com.mydrive.drive.file;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDownloadServiceTests {

    @Mock CurrentUserService currentUserService;
    @Mock DriveFileRepository driveFileRepository;
    @Mock FileVersionRepository fileVersionRepository;
    @Mock StorageService storageService;

    @InjectMocks FileDownloadService fileDownloadService;

    @Test
    void downloadLoadsCurrentVersionForOwnedActiveReadyFile() throws Exception {
        UUID ownerId = UUID.randomUUID();
        DriveFile file = file(ownerId, null, UploadStatus.READY);
        FileVersion version = version(file, ownerId, "current-key");
        byte[] content = "data".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(file.getId(), ownerId)).thenReturn(Optional.of(file));
        when(fileVersionRepository.findByFileIdAndVersionNumber(file.getId(), 1))
                .thenReturn(Optional.of(version));
        when(storageService.load("current-key")).thenReturn(new ByteArrayInputStream(content));

        FileDownload download = fileDownloadService.download(file.getId());

        assertThat(download.inputStream().readAllBytes()).isEqualTo(content);
        assertThat(download.filename()).isEqualTo("file.txt");
        verify(storageService).load("current-key");
    }

    @Test
    void missingOrForeignFileDoesNotTouchStorage() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(fileId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileDownloadService.download(fileId))
                .isInstanceOf(FileNotFoundException.class);

        verifyNoInteractions(fileVersionRepository, storageService);
    }

    @Test
    void trashedFileDoesNotTouchStorage() {
        UUID ownerId = UUID.randomUUID();
        DriveFile file = file(ownerId, Instant.now(), UploadStatus.READY);
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(file.getId(), ownerId)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileDownloadService.download(file.getId()))
                .isInstanceOf(FileNotFoundException.class);

        verifyNoInteractions(fileVersionRepository, storageService);
    }

    @Test
    void pendingFileDoesNotTouchStorage() {
        UUID ownerId = UUID.randomUUID();
        DriveFile file = file(ownerId, null, UploadStatus.PENDING);
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(file.getId(), ownerId)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileDownloadService.download(file.getId()))
                .isInstanceOf(FileNotFoundException.class);

        verifyNoInteractions(fileVersionRepository, storageService);
    }

    private void stubUser(UUID ownerId) {
        when(currentUserService.requireCurrentUser())
                .thenReturn(new AppUser(ownerId, "user@example.com", "hash", Instant.now()));
    }

    private DriveFile file(UUID ownerId, Instant deletedAt, UploadStatus status) {
        Instant now = Instant.now();
        return new DriveFile(
                UUID.randomUUID(), ownerId, null, "file.txt", "text/plain", 4,
                "a".repeat(64), 1, status, now, now, deletedAt);
    }

    private FileVersion version(DriveFile file, UUID ownerId, String key) {
        return new FileVersion(
                UUID.randomUUID(), file.getId(), 1, key, "a".repeat(64),
                4, ownerId, Instant.now());
    }
}
