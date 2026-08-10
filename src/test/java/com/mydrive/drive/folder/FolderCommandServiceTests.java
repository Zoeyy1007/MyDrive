package com.mydrive.drive.folder;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.folder.dto.MoveFolderRequest;
import com.mydrive.drive.folder.dto.RenameFolderRequest;
import com.mydrive.drive.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderCommandServiceTests{

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private FolderCommandService folderCommandService;

    @Test
    void renameUpdatesOwnedActiveFolder() {
        UUID ownerId = UUID.randomUUID();
        Folder folder = folder(UUID.randomUUID(), null, ownerId, null);
        stubUser(ownerId);
        when(folderRepository.findByIdAndOwnerId(folder.getId(), ownerId)).thenReturn(Optional.of(folder));
        when(folderRepository.save(folder)).thenReturn(folder);

        var result = folderCommandService.rename(folder.getId(), new RenameFolderRequest("Renamed"));

        assertThat(result.name()).isEqualTo("Renamed");
        verify(folderRepository).save(folder);
    }

    @Test
    void foreignFolderIsHiddenAsNotFound() {
        UUID ownerId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        stubUser(ownerId);
        when(folderRepository.findByIdAndOwnerId(folderId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> folderCommandService.rename(folderId, new RenameFolderRequest("Name")))
                .isInstanceOf(FolderNotFoundException.class);

        verify(folderRepository, never()).save(any());
    }

    @Test
    void moveRejectsMovingFolderIntoItself() {
        UUID ownerId = UUID.randomUUID();
        Folder folder = folder(UUID.randomUUID(), null, ownerId, null);
        stubUser(ownerId);
        when(folderRepository.findByIdAndOwnerId(folder.getId(), ownerId)).thenReturn(Optional.of(folder));

        assertThatThrownBy(() -> folderCommandService.move(
                folder.getId(), new MoveFolderRequest(folder.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itself");

        verify(folderRepository, never()).save(any());
    }

    @Test
    void moveRejectsMovingFolderIntoDescendant() {
        UUID ownerId = UUID.randomUUID();
        Folder parent = folder(UUID.randomUUID(), null, ownerId, null);
        Folder child = folder(UUID.randomUUID(), parent.getId(), ownerId, null);
        stubUser(ownerId);
        when(folderRepository.findByIdAndOwnerId(parent.getId(), ownerId)).thenReturn(Optional.of(parent));
        when(folderRepository.findByIdAndOwnerId(child.getId(), ownerId)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> folderCommandService.move(
                parent.getId(), new MoveFolderRequest(child.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descendant");

        verify(folderRepository, never()).save(any());
    }

    @Test
    void trashAndRestoreChangeSoftDeleteState() {
        UUID ownerId = UUID.randomUUID();
        Folder folder = folder(UUID.randomUUID(), null, ownerId, null);
        stubUser(ownerId);
        when(folderRepository.findByIdAndOwnerId(folder.getId(), ownerId)).thenReturn(Optional.of(folder));
        when(folderRepository.save(folder)).thenReturn(folder);

        folderCommandService.moveToTrash(folder.getId());
        assertThat(folder.isDeleted()).isTrue();

        var restored = folderCommandService.restore(folder.getId());
        assertThat(folder.isDeleted()).isFalse();
        assertThat(restored.id()).isEqualTo(folder.getId());
    }

    private void stubUser(UUID ownerId) {
        when(currentUserService.requireCurrentUser())
                .thenReturn(new AppUser(ownerId, "user@example.com", "hash", Instant.now()));
    }

    private Folder folder(UUID id, UUID parentId, UUID ownerId, Instant deletedAt) {
        Instant now = Instant.now();
        return new Folder(id, parentId, "Folder", now, now, ownerId, deletedAt);
    }
}
