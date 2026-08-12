
package com.mydrive.drive.folder;

import com.mydrive.drive.folder.dto.CreateFolderRequest;
import com.mydrive.drive.folder.dto.FolderResponse;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.sync.SyncChangeService;
import com.mydrive.drive.sync.SyncOperation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FolderService{
    /* Folder creation records its sync event in the same transaction. */
    private final FolderRepository folderRepository;
    private final CurrentUserService currentUserService;
    private final SyncChangeService syncChangeService;

    public FolderService(
            FolderRepository folderRepository,
            CurrentUserService currentUserService,
            SyncChangeService syncChangeService){
        this.folderRepository = folderRepository;
        this.currentUserService = currentUserService;
        this.syncChangeService = syncChangeService;
    }

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request){
        var now = java.time.Instant.now();
        var currentUser = currentUserService.requireCurrentUser();
        UUID ownerId = currentUser.getId();
        validateActiveParent(request.parentId(), ownerId);

        var folder = new Folder(java.util.UUID.randomUUID(), request.parentId(), request.name(), now, now, ownerId, null);
        var savedFolder = folderRepository.save(folder);
        syncChangeService.recordFolderChange(savedFolder, SyncOperation.CREATED, null);
        return toResponse(savedFolder);
    }

    @Transactional(readOnly=true)
    public List<FolderResponse> listFolders(){
        var currentUser = currentUserService.requireCurrentUser();
        return folderRepository.findAllByOwnerIdAndDeletedAtIsNull(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateActiveParent(UUID parentId, UUID ownerId) {
        if (parentId == null) {
            return;
        }

        Folder parent = folderRepository.findByIdAndOwnerId(parentId, ownerId)
                .orElseThrow(() -> new FolderNotFoundException(parentId));
        if (parent.isDeleted()) {
            throw new FolderNotFoundException(parentId);
        }
    }

    private FolderResponse toResponse(Folder folder){
        return new FolderResponse(folder.getId(), folder.getParentId(), folder.getName(), folder.getCreatedAt(), folder.getUpdatedAt());
    }
}
