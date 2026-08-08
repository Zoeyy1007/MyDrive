
package com.mydrive.drive.folder;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.folder.dto.CreateFolderRequest;
import com.mydrive.drive.folder.dto.FolderResponse;
import com.mydrive.drive.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FolderServiceTests{
    @Mock
    private FolderRepository folderRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private FolderService folderService;

    @Test
    void createFolderSavesAndReturnsFolder(){
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        AppUser currentUser = new AppUser(
                ownerId,
                "user@example.com",
                "hashedPassword",
                Instant.now()
        );
        Folder savedFolder = new Folder(
                id,
                null,
                "Test Folder",
                Instant.now(),
                Instant.now(),
                ownerId
        );

        when(currentUserService.requireCurrentUser()).thenReturn(currentUser);
        when(folderRepository.save(any(Folder.class))).thenReturn(savedFolder);

        CreateFolderRequest request = new CreateFolderRequest("Test Folder", null);

        FolderResponse result = folderService.createFolder(request);
        ArgumentCaptor<Folder> folderCaptor = ArgumentCaptor.forClass(Folder.class);
        verify(folderRepository).save(folderCaptor.capture());

        assertThat(folderCaptor.getValue().getOwnerId()).isEqualTo(ownerId);
        assertThat(result.name()).isEqualTo(savedFolder.getName());
        assertThat(result.parentId()).isEqualTo(savedFolder.getParentId());
        assertThat(result.createdAt()).isEqualTo(savedFolder.getCreatedAt());
        assertThat(result.updatedAt()).isEqualTo(savedFolder.getUpdatedAt());
        assertThat(result.id()).isEqualTo(savedFolder.getId());

        verify(currentUserService).requireCurrentUser();
    }

    @Test
    void listFoldersConvertsEntitiesToResponses(){
        UUID ownerId = UUID.randomUUID();
        AppUser currentUser = new AppUser(
                ownerId,
                "user@example.com",
                "hashedPassword",
                Instant.now()
        );
        Folder folder = new Folder(
                UUID.randomUUID(),
                null,
                "Test Folder",
                Instant.now(),
                Instant.now(),
                ownerId
        );

        when(currentUserService.requireCurrentUser()).thenReturn(currentUser);
        when(folderRepository.findAllByOwnerId(ownerId)).thenReturn(java.util.List.of(folder));
        java.util.List<FolderResponse> result = folderService.listFolders();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo(folder.getName());
        assertThat(result.get(0).parentId()).isEqualTo(folder.getParentId());
        assertThat(result.get(0).createdAt()).isEqualTo(folder.getCreatedAt());
        assertThat(result.get(0).updatedAt()).isEqualTo(folder.getUpdatedAt());
        assertThat(result.get(0).id()).isEqualTo(folder.getId());

        verify(currentUserService).requireCurrentUser();
        verify(folderRepository).findAllByOwnerId(ownerId);
    }
}
