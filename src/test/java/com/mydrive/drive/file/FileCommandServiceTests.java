package com.mydrive.drive.file;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.file.dto.CopyFileRequest;
import com.mydrive.drive.file.dto.MoveFileRequest;
import com.mydrive.drive.file.dto.RenameFileRequest;
import com.mydrive.drive.folder.FolderNotFoundException;
import com.mydrive.drive.folder.FolderRepository;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.storage.StorageKeyFactory;
import com.mydrive.drive.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileCommandServiceTests {

    @Mock DriveFileRepository driveFileRepository;
    @Mock CurrentUserService currentUserService;
    @Mock StorageService storageService;
    @Mock StorageKeyFactory storageKeyFactory;
    @Mock FileVersionRepository fileVersionRepository;
    @Mock FolderRepository folderRepository;

    @InjectMocks FileCommandService fileCommandService;

    @Test
    void renameUpdatesOwnedReadyFile() {
        UUID ownerId = UUID.randomUUID();
        DriveFile file = readyFile(UUID.randomUUID(), ownerId, null);
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(file.getId(), ownerId)).thenReturn(Optional.of(file));
        when(driveFileRepository.save(file)).thenReturn(file);

        var response = fileCommandService.rename(file.getId(), new RenameFileRequest("renamed.txt"));

        assertThat(response.name()).isEqualTo("renamed.txt");
    }

    @Test
    void foreignFileIsHiddenAsNotFound() {
        UUID ownerId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(fileId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileCommandService.rename(fileId, new RenameFileRequest("name.txt")))
                .isInstanceOf(FileNotFoundException.class);

        verify(driveFileRepository, never()).save(any());
    }

    @Test
    void moveRejectsMissingOrForeignDestinationFolder() {
        UUID ownerId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        DriveFile file = readyFile(UUID.randomUUID(), ownerId, null);
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(file.getId(), ownerId)).thenReturn(Optional.of(file));
        when(folderRepository.findByIdAndOwnerId(destinationId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileCommandService.move(
                file.getId(), new MoveFileRequest(destinationId)))
                .isInstanceOf(FolderNotFoundException.class);

        verify(driveFileRepository, never()).save(any());
    }

    @Test
    void copySavesPendingThenReadyAndCopiesObjectOnce() {
        UUID ownerId = UUID.randomUUID();
        DriveFile source = readyFile(UUID.randomUUID(), ownerId, null);
        FileVersion sourceVersion = version(source.getId(), ownerId, "source-key");
        List<UploadStatus> copiedStatuses = new ArrayList<>();
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(source.getId(), ownerId)).thenReturn(Optional.of(source));
        when(fileVersionRepository.findByFileIdAndVersionNumber(source.getId(), 1))
                .thenReturn(Optional.of(sourceVersion));
        when(storageKeyFactory.versionKey(eq(ownerId), any(UUID.class), eq(1))).thenReturn("copied-key");
        when(driveFileRepository.save(any(DriveFile.class))).thenAnswer(invocation -> {
            DriveFile saved = invocation.getArgument(0);
            if (!saved.getId().equals(source.getId())) {
                copiedStatuses.add(saved.getUploadStatus());
            }
            return saved;
        });

        var response = fileCommandService.copy(source.getId(), new CopyFileRequest(null, "copy.txt"));

        assertThat(response.name()).isEqualTo("copy.txt");
        assertThat(response.uploadStatus()).isEqualTo(UploadStatus.READY);
        assertThat(copiedStatuses).containsExactly(UploadStatus.PENDING, UploadStatus.READY);
        verify(storageService, times(1)).copy("source-key", "copied-key");
        verify(fileVersionRepository).save(any(FileVersion.class));
    }

    @Test
    void softDeleteDoesNotDeleteStoredObject() {
        UUID ownerId = UUID.randomUUID();
        DriveFile file = readyFile(UUID.randomUUID(), ownerId, null);
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(file.getId(), ownerId)).thenReturn(Optional.of(file));
        when(driveFileRepository.save(file)).thenReturn(file);

        fileCommandService.moveToTrash(file.getId());

        assertThat(file.isDeleted()).isTrue();
        verifyNoInteractions(storageService);
    }

    @Test
    void restoreClearsDeletedAt() {
        UUID ownerId = UUID.randomUUID();
        DriveFile file = readyFile(UUID.randomUUID(), ownerId, Instant.now());
        stubUser(ownerId);
        when(driveFileRepository.findByIdAndOwnerId(file.getId(), ownerId)).thenReturn(Optional.of(file));
        when(driveFileRepository.save(file)).thenReturn(file);

        var response = fileCommandService.restore(file.getId());

        assertThat(file.isDeleted()).isFalse();
        assertThat(response.id()).isEqualTo(file.getId());
    }

    private void stubUser(UUID ownerId) {
        when(currentUserService.requireCurrentUser())
                .thenReturn(new AppUser(ownerId, "user@example.com", "hash", Instant.now()));
    }

    private DriveFile readyFile(UUID id, UUID ownerId, Instant deletedAt) {
        Instant now = Instant.now();
        return new DriveFile(
                id, ownerId, null, "file.txt", "text/plain", 4,
                "a".repeat(64), 1, UploadStatus.READY, now, now, deletedAt);
    }

    private FileVersion version(UUID fileId, UUID ownerId, String key) {
        return new FileVersion(
                UUID.randomUUID(), fileId, 1, key, "a".repeat(64),
                4, ownerId, Instant.now());
    }
}
