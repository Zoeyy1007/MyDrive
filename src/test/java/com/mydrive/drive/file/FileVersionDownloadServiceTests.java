/*
 * PHASE 5 TESTS for historical downloads.
 *
 * Test:
 *   - owned active READY file loads the requested version's storage key
 *   - requested version size is returned in FileDownload
 *   - missing/foreign/deleted file never touches StorageService
 *   - missing version never touches StorageService
 *   - requesting version 1 still works when currentVersion is greater than 1
 */
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
class FileVersionDownloadServiceTests {
    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID FILE_ID = UUID.randomUUID();
    private static final String VERSION_ONE_KEY = "users/owner/files/file/versions/1";

    @Mock
    private DriveFileRepository driveFileRepository;
    @Mock
    private FileVersionRepository fileVersionRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private FileVersionDownloadService service;

    @Test
    void downloadsRequestedHistoricalVersionAndUsesItsSize() {
        DriveFile file = driveFile(false);
        FileVersion versionOne = version(1, 7);
        byte[] bytes = "version".getBytes();
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(file));
        when(fileVersionRepository.findByFileIdAndVersionNumber(FILE_ID, 1))
                .thenReturn(Optional.of(versionOne));
        when(storageService.load(VERSION_ONE_KEY))
                .thenReturn(new ByteArrayInputStream(bytes));

        FileDownload download = service.downloadVersion(FILE_ID, 1);

        verify(storageService).load(VERSION_ONE_KEY);
        assertThat(download.filename()).isEqualTo("document.txt");
        assertThat(download.contentType()).isEqualTo("text/plain");
        assertThat(download.size()).isEqualTo(7);
        assertThat(download.inputStream()).isNotNull();
    }

    @Test
    void missingOrForeignFileNeverTouchesStorage() {
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadVersion(FILE_ID, 1))
                .isInstanceOf(FileNotFoundException.class);

        verifyNoInteractions(fileVersionRepository, storageService);
    }

    @Test
    void deletedFileNeverTouchesStorage() {
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(driveFile(true)));

        assertThatThrownBy(() -> service.downloadVersion(FILE_ID, 1))
                .isInstanceOf(FileNotFoundException.class);

        verifyNoInteractions(fileVersionRepository, storageService);
    }

    @Test
    void missingVersionNeverTouchesStorage() {
        when(currentUserService.requireCurrentUser()).thenReturn(user());
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(driveFile(false)));
        when(fileVersionRepository.findByFileIdAndVersionNumber(FILE_ID, 99))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadVersion(FILE_ID, 99))
                .isInstanceOf(FileVersionNotFoundException.class);

        verifyNoInteractions(storageService);
    }

    private AppUser user() {
        return new AppUser(OWNER_ID, "user@example.com", "hash", Instant.now());
    }

    private DriveFile driveFile(boolean deleted) {
        Instant now = Instant.now();
        return new DriveFile(
                FILE_ID, OWNER_ID, null, "document.txt", "text/plain", 30,
                "a".repeat(64), 3, UploadStatus.READY, now, now,
                deleted ? now : null);
    }

    private FileVersion version(int number, long size) {
        return new FileVersion(
                UUID.randomUUID(), FILE_ID, number, VERSION_ONE_KEY,
                "b".repeat(64), size, OWNER_ID, Instant.now(), null);
    }
}
