
package com.mydrive.drive.file;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.checksum.ChecksumService;
import com.mydrive.drive.checksum.Sha256ChecksumService;
import com.mydrive.drive.file.dto.FileResponse;
import com.mydrive.drive.folder.FolderRepository;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.storage.StorageException;
import com.mydrive.drive.storage.StorageKeyFactory;
import com.mydrive.drive.storage.StorageService;
import com.mydrive.drive.storage.StoredObject;
import com.mydrive.drive.sync.SyncChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTests {
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TEMPORARY_KEY = "temp/owner/upload";
    private static final String FINAL_KEY = "users/owner/files/file/versions/1";

    @Mock
    private FileVersionRepository fileVersionRepository;

    @Mock
    private DriveFileRepository driveFileRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private StorageService storageService;

    @Mock
    private StorageKeyFactory storageKeyFactory;

    @Spy
    private ChecksumService checksumService = new Sha256ChecksumService();

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private SyncChangeService syncChangeService;

    @InjectMocks
    private FileUploadService fileUploadService;

    @BeforeEach
    void setUp() {
        AppUser currentUser = new AppUser(
                OWNER_ID,
                "user@example.com",
                "hashedPassword",
                Instant.now()
        );
        lenient().when(currentUserService.requireCurrentUser()).thenReturn(currentUser);
        lenient().when(storageKeyFactory.temporaryKey(eq(OWNER_ID), any(UUID.class)))
                .thenReturn(TEMPORARY_KEY);
        lenient().when(storageKeyFactory.versionKey(eq(OWNER_ID), any(UUID.class), eq(1)))
                .thenReturn(FINAL_KEY);
        lenient().when(driveFileRepository.save(any(DriveFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(fileVersionRepository.save(any(FileVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(mock(TransactionStatus.class));
                });
    }

    @Test
    void successfulUploadStoresTempThenFinalizesAndReturnsReadyMetadata() {
        byte[] content = "hello upload".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile multipartFile = multipartFile(content);
        List<UploadStatus> savedStatuses = new ArrayList<>();

        when(driveFileRepository.save(any(DriveFile.class))).thenAnswer(invocation -> {
            DriveFile file = invocation.getArgument(0);
            savedStatuses.add(file.getUploadStatus());
            return file;
        });
        stubSuccessfulStorage(content);

        FileResponse response = fileUploadService.upload(null, multipartFile);

        assertThat(response.name()).isEqualTo("hello.txt");
        assertThat(response.size()).isEqualTo(content.length);
        assertThat(response.uploadStatus()).isEqualTo(UploadStatus.READY);
        assertThat(savedStatuses).containsExactly(UploadStatus.PENDING, UploadStatus.READY);

        verify(storageService).save(
                eq(TEMPORARY_KEY),
                any(InputStream.class),
                eq((long) content.length),
                eq("text/plain")
        );
        verify(storageService).copy(TEMPORARY_KEY, FINAL_KEY);
        verify(storageService).exists(FINAL_KEY);
        verify(storageService).load(FINAL_KEY);
        verify(storageService).delete(TEMPORARY_KEY);
    }

    @Test
    void uploadRejectsParentOwnedByAnotherUser() {
        UUID parentFolderId = UUID.randomUUID();
        when(folderRepository.findByIdAndOwnerId(parentFolderId, OWNER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileUploadService.upload(
                parentFolderId,
                multipartFile("content".getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");

        verifyNoInteractions(storageService, driveFileRepository, fileVersionRepository);
    }

    @Test
    void storageFailureDoesNotReturnSuccess() {
        byte[] content = "failure".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        List<UploadStatus> savedStatuses = new ArrayList<>();

        when(storageService.save(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenReturn(new StoredObject(TEMPORARY_KEY, content.length, "text/plain"));
        doThrow(new StorageException("copy failed"))
                .when(storageService).copy(TEMPORARY_KEY, FINAL_KEY);
        when(driveFileRepository.save(any(DriveFile.class))).thenAnswer(invocation -> {
            DriveFile file = invocation.getArgument(0);
            savedStatuses.add(file.getUploadStatus());
            return file;
        });

        assertThatThrownBy(() -> fileUploadService.upload(null, multipartFile(content)))
                .isInstanceOf(StorageException.class)
                .hasMessage("copy failed");

        assertThat(savedStatuses).containsExactly(UploadStatus.PENDING, UploadStatus.FAILED);
        verify(storageService).delete(TEMPORARY_KEY);
        verify(storageService, never()).exists(FINAL_KEY);
    }

    @Test
    void checksumAndRecordedSizeMatchTheUploadedContent() {
        byte[] content = "checksum content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        stubSuccessfulStorage(content);
        ArgumentCaptor<FileVersion> versionCaptor = ArgumentCaptor.forClass(FileVersion.class);

        FileResponse response = fileUploadService.upload(null, multipartFile(content));

        verify(fileVersionRepository).save(versionCaptor.capture());
        String expectedChecksum = new Sha256ChecksumService()
                .sha256(new ByteArrayInputStream(content));

        assertThat(versionCaptor.getValue().getChecksum()).isEqualTo(expectedChecksum);
        assertThat(versionCaptor.getValue().getSize()).isEqualTo(content.length);
        assertThat(response.checksum()).isEqualTo(expectedChecksum);
        assertThat(response.size()).isEqualTo(content.length);
    }

    private void stubSuccessfulStorage(byte[] content) {
        when(storageService.save(anyString(), any(InputStream.class), anyLong(), anyString()))
                .thenReturn(new StoredObject(TEMPORARY_KEY, content.length, "text/plain"));
        when(storageService.exists(FINAL_KEY)).thenReturn(true);
        when(storageService.load(FINAL_KEY)).thenAnswer(
                invocation -> new ByteArrayInputStream(content)
        );
    }

    private MockMultipartFile multipartFile(byte[] content) {
        return new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                content
        );
    }
}
