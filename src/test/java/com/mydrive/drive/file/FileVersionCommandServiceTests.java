/*
 * PHASE 5 TESTS for FileVersionCommandService.
 *
 * Use JUnit 5 + MockitoExtension. Mock storage, repositories, current user,
 * checksum, key factory, and TransactionTemplate.
 *
 * Upload tests:
 *   - version 1 becomes 2 and uses the version-2 storage key
 *   - old FileVersion row and old object are never changed/deleted
 *   - DriveFile metadata/currentVersion advances only after storage succeeds
 *   - storage failure does not return success
 *   - foreign owner is rejected before any StorageService interaction
 *
 * Restore tests:
 *   - restoring old version 2 while current is 5 creates version 6
 *   - selected object is copied to a new version-6 key
 *   - new version inherits selected checksum/size
 *   - createdBy is the authenticated user and sourceDeviceId is null
 *   - missing/foreign version returns FileVersionNotFoundException
 */
package com.mydrive.drive.file;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.device.CurrentDeviceService;
import com.mydrive.drive.checksum.ChecksumService;
import com.mydrive.drive.file.dto.FileVersionResponse;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.storage.StorageException;
import com.mydrive.drive.storage.StorageKeyFactory;
import com.mydrive.drive.storage.StorageService;
import com.mydrive.drive.sync.SyncChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileVersionCommandServiceTests {
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FILE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String VERSION_TWO_KEY = "users/owner/files/file/versions/2";
    private static final String VERSION_SIX_KEY = "users/owner/files/file/versions/6";
    private static final String CHECKSUM = "a".repeat(64);

    @Mock
    private DriveFileRepository driveFileRepository;
    @Mock
    private FileVersionRepository fileVersionRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private StorageService storageService;
    @Mock
    private StorageKeyFactory storageKeyFactory;
    @Mock
    private ChecksumService checksumService;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private CurrentDeviceService currentDeviceService;
    @Mock
    private SyncChangeService syncChangeService;

    @InjectMocks
    private FileVersionCommandService service;

    @BeforeEach
    void setUp() {
        when(currentUserService.requireCurrentUser()).thenReturn(
                new AppUser(OWNER_ID, "user@example.com", "hash", Instant.now()));
    }

    @Test
    void uploadStoresVersionTwoThenPromotesDriveFile() {
        DriveFile driveFile = readyFile();
        byte[] bytes = "new content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "ignored-name.txt", "text/plain", bytes);

        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(driveFile));
        when(storageKeyFactory.versionKey(OWNER_ID, FILE_ID, 2))
                .thenReturn(VERSION_TWO_KEY);
        when(checksumService.sha256(any(InputStream.class))).thenReturn(CHECKSUM);
        when(fileVersionRepository.save(any(FileVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(driveFileRepository.save(any(DriveFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        executeTransactionsImmediately();

        FileVersionResponse response = service.uploadVersion(FILE_ID, multipartFile);

        ArgumentCaptor<FileVersion> versionCaptor = ArgumentCaptor.forClass(FileVersion.class);
        verify(fileVersionRepository).save(versionCaptor.capture());
        FileVersion savedVersion = versionCaptor.getValue();

        verify(storageService).save(
                eq(VERSION_TWO_KEY),
                any(InputStream.class),
                eq((long) bytes.length),
                eq("text/plain"));
        assertThat(savedVersion.getVersionNumber()).isEqualTo(2);
        assertThat(savedVersion.getStorageKey()).isEqualTo(VERSION_TWO_KEY);
        assertThat(savedVersion.getSourceDeviceId()).isNull();
        assertThat(driveFile.getCurrentVersion()).isEqualTo(2);
        assertThat(driveFile.getChecksum()).isEqualTo(CHECKSUM);
        assertThat(response.current()).isTrue();
    }

    @Test
    void storageFailureDoesNotAdvanceMetadata() {
        DriveFile driveFile = readyFile();
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "ignored.txt", "text/plain", "new".getBytes());

        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(driveFile));
        when(storageKeyFactory.versionKey(OWNER_ID, FILE_ID, 2))
                .thenReturn(VERSION_TWO_KEY);
        when(checksumService.sha256(any(InputStream.class))).thenReturn(CHECKSUM);
        when(storageService.save(
                eq(VERSION_TWO_KEY), any(InputStream.class), eq(3L), eq("text/plain")))
                .thenThrow(new StorageException("storage failed"));

        assertThatThrownBy(() -> service.uploadVersion(FILE_ID, multipartFile))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("storage failed");

        assertThat(driveFile.getCurrentVersion()).isEqualTo(1);
        verify(fileVersionRepository, never()).save(any());
        verify(driveFileRepository, never()).save(any());
        verify(storageService).delete(VERSION_TWO_KEY);
    }

    @Test
    void foreignOrMissingFileIsRejectedBeforeStorage() {
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadVersion(
                FILE_ID,
                new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1})))
                .isInstanceOf(FileNotFoundException.class);

        verifyNoInteractions(storageService, fileVersionRepository);
    }

    @Test
    void restoreVersionTwoCreatesVersionSixAndPromotesIt() {
        DriveFile driveFile = readyFile(5);
        FileVersion selectedVersion = new FileVersion(
                UUID.randomUUID(),
                FILE_ID,
                2,
                "users/owner/files/file/versions/2",
                CHECKSUM,
                42,
                OWNER_ID,
                Instant.now(),
                null);
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(driveFile));
        when(fileVersionRepository.findByFileIdAndVersionNumber(FILE_ID, 2))
                .thenReturn(Optional.of(selectedVersion));
        when(storageKeyFactory.versionKey(OWNER_ID, FILE_ID, 6))
                .thenReturn(VERSION_SIX_KEY);
        when(fileVersionRepository.save(any(FileVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(driveFileRepository.save(any(DriveFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        executeTransactionsImmediately();

        FileVersionResponse response = service.restoreVersion(FILE_ID, 2);

        ArgumentCaptor<FileVersion> versionCaptor = ArgumentCaptor.forClass(FileVersion.class);
        verify(storageService).copy(selectedVersion.getStorageKey(), VERSION_SIX_KEY);
        verify(fileVersionRepository).save(versionCaptor.capture());
        FileVersion restoredVersion = versionCaptor.getValue();

        assertThat(restoredVersion.getVersionNumber()).isEqualTo(6);
        assertThat(restoredVersion.getStorageKey()).isEqualTo(VERSION_SIX_KEY);
        assertThat(restoredVersion.getChecksum()).isEqualTo(CHECKSUM);
        assertThat(restoredVersion.getSize()).isEqualTo(42);
        assertThat(restoredVersion.getCreatedBy()).isEqualTo(OWNER_ID);
        assertThat(restoredVersion.getSourceDeviceId()).isNull();
        assertThat(driveFile.getCurrentVersion()).isEqualTo(6);
        assertThat(response.versionNumber()).isEqualTo(6);
        assertThat(response.current()).isTrue();
    }

    @Test
    void missingVersionIsRejectedBeforeStorage() {
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(readyFile(5)));
        when(fileVersionRepository.findByFileIdAndVersionNumber(FILE_ID, 4))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreVersion(FILE_ID, 4))
                .isInstanceOf(FileVersionNotFoundException.class);

        verifyNoInteractions(storageService);
    }

    @Test
    void restoreStorageFailureDoesNotAdvanceCurrentVersion() {
        DriveFile driveFile = readyFile(5);
        FileVersion selectedVersion = new FileVersion(
                UUID.randomUUID(), FILE_ID, 2, "source-key", CHECKSUM,
                42, OWNER_ID, Instant.now(), null);
        when(driveFileRepository.findByIdAndOwnerId(FILE_ID, OWNER_ID))
                .thenReturn(Optional.of(driveFile));
        when(fileVersionRepository.findByFileIdAndVersionNumber(FILE_ID, 2))
                .thenReturn(Optional.of(selectedVersion));
        when(storageKeyFactory.versionKey(OWNER_ID, FILE_ID, 6))
                .thenReturn(VERSION_SIX_KEY);
        org.mockito.Mockito.doThrow(new StorageException("copy failed"))
                .when(storageService).copy("source-key", VERSION_SIX_KEY);

        assertThatThrownBy(() -> service.restoreVersion(FILE_ID, 2))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("copy failed");

        assertThat(driveFile.getCurrentVersion()).isEqualTo(5);
        verify(fileVersionRepository, never()).save(any());
        verify(driveFileRepository, never()).save(any());
        verify(storageService).delete(VERSION_SIX_KEY);
    }

    private void executeTransactionsImmediately() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private DriveFile readyFile() {
        return readyFile(1);
    }

    private DriveFile readyFile(int currentVersion) {
        Instant now = Instant.now();
        return new DriveFile(
                FILE_ID,
                OWNER_ID,
                null,
                "document.txt",
                "text/plain",
                3,
                "b".repeat(64),
                currentVersion,
                UploadStatus.READY,
                now,
                now,
                null);
    }
}
