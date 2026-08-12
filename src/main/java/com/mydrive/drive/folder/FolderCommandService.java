
package com.mydrive.drive.folder;

import com.mydrive.drive.folder.dto.FolderResponse;
import com.mydrive.drive.folder.dto.MoveFolderRequest;
import com.mydrive.drive.folder.dto.RenameFolderRequest;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.sync.RelativePathService;
import com.mydrive.drive.sync.SyncChangeService;
import com.mydrive.drive.sync.SyncOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class FolderCommandService{
    /* Folder mutations and their sync events commit in the same transaction. */
    private final FolderRepository folderRepository;
    private final CurrentUserService currentUserService;
    private final RelativePathService relativePathService;
    private final SyncChangeService syncChangeService;

    public FolderCommandService(
            FolderRepository folderRepository,
            CurrentUserService currentUserService,
            RelativePathService relativePathService,
            SyncChangeService syncChangeService){
        this.folderRepository = folderRepository;
        this.currentUserService = currentUserService;
        this.relativePathService = relativePathService;
        this.syncChangeService = syncChangeService;
    }

    @Transactional
    public FolderResponse rename(UUID folderId, RenameFolderRequest request){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        Folder folder = requireOwnedFolder(folderId, ownerId, false);
        String previousPath = relativePathService.pathForFolder(ownerId, null, folderId);
        folder.rename(request.name(), Instant.now());
        Folder saved = folderRepository.save(folder);
        syncChangeService.recordFolderChange(saved, SyncOperation.RENAMED, previousPath);
        return toResponse(saved);
    }

    @Transactional
    public FolderResponse move(UUID folderId, MoveFolderRequest request){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        Folder folder = requireOwnedFolder(folderId, ownerId, false);
        String previousPath = relativePathService.pathForFolder(ownerId, null, folderId);
        validateMoveDestination(folderId, request.parentId(), ownerId);
        folder.move(Instant.now(), request.parentId());
        Folder saved = folderRepository.save(folder);
        syncChangeService.recordFolderChange(saved, SyncOperation.MOVED, previousPath);
        return toResponse(saved);
    }

    @Transactional
    public void moveToTrash(UUID folderId){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        Folder folder = requireOwnedFolder(folderId, ownerId, false);
        folder.moveToTrash(Instant.now());
        Folder saved = folderRepository.save(folder);
        syncChangeService.recordFolderChange(saved, SyncOperation.DELETED, null);
    }

    @Transactional
    public FolderResponse restore(UUID folderId){
        UUID ownerId = currentUserService.requireCurrentUser().getId();
        Folder folder = requireOwnedFolder(folderId, ownerId, true);
        validateActiveParent(folder.getParentId(), ownerId);
        folder.restore(Instant.now());
        Folder saved = folderRepository.save(folder);
        syncChangeService.recordFolderChange(saved, SyncOperation.RESTORED, null);
        return toResponse(saved);
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
