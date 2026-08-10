
package com.mydrive.drive.folder;

import com.mydrive.drive.folder.dto.FolderResponse;
import com.mydrive.drive.folder.dto.MoveFolderRequest;
import com.mydrive.drive.folder.dto.RenameFolderRequest;
import com.mydrive.drive.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class FolderCommandService{
    private final FolderRepository folderRepository;
    private final CurrentUserService currentUserService;

    public FolderCommandService(FolderRepository folderRepository, CurrentUserService currentUserService){
        this.folderRepository = folderRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public FolderResponse rename(UUID folderId, RenameFolderRequest request){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        Folder folder = requireOwnedFolder(folderId, ownerId, false);
        folder.rename(request.name(), Instant.now());
        return toResponse(folderRepository.save(folder));
    }

    @Transactional
    public FolderResponse move(UUID folderId, MoveFolderRequest request){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        Folder folder = requireOwnedFolder(folderId, ownerId, false);
        validateMoveDestination(folderId, request.parentId(), ownerId);
        folder.move(Instant.now(), request.parentId());
        return toResponse(folderRepository.save(folder));
    }

    @Transactional
    public void moveToTrash(UUID folderId){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        Folder folder = requireOwnedFolder(folderId, ownerId, false);
        folder.moveToTrash(Instant.now());
        folderRepository.save(folder);
    }

    @Transactional
    public FolderResponse restore(UUID folderId){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        Folder folder = requireOwnedFolder(folderId, ownerId, true);
        validateActiveParent(folder.getParentId(), ownerId);
        folder.restore(Instant.now());
        return toResponse(folderRepository.save(folder));
    }

    private Folder requireOwnedFolder(UUID folderId, UUID ownerId, boolean allowDeleted) {
        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));
        if (!allowDeleted && folder.isDeleted()) {
            throw new FolderNotFoundException(folderId);
        }
        return folder;
    }

    private void validateActiveParent(UUID parentId, UUID ownerId) {
        if (parentId == null) {
            return;
        }
        requireOwnedFolder(parentId, ownerId, false);
    }

    private void validateMoveDestination(UUID folderId, UUID newParentId, UUID ownerId) {
        if (newParentId == null) {
            return;
        }
        if (folderId.equals(newParentId)) {
            throw new IllegalArgumentException("A folder cannot be moved into itself");
        }

        Set<UUID> visited = new HashSet<>();
        UUID currentId = newParentId;
        while (currentId != null) {
            if (folderId.equals(currentId)) {
                throw new IllegalArgumentException("A folder cannot be moved into its descendant");
            }
            if (!visited.add(currentId)) {
                throw new IllegalStateException("Existing folder hierarchy contains a cycle");
            }

            Folder current = requireOwnedFolder(currentId, ownerId, false);
            currentId = current.getParentId();
        }
    }

    private FolderResponse toResponse(Folder folder){
        return new FolderResponse(folder.getId(), folder.getParentId(), folder.getName(), folder.getCreatedAt(), folder.getUpdatedAt());
    }
}
