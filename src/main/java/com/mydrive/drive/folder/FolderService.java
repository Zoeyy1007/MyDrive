
package com.mydrive.drive.folder;

import com.mydrive.drive.folder.dto.CreateFolderRequest;
import com.mydrive.drive.folder.dto.FolderResponse;
import com.mydrive.drive.security.CurrentUserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FolderService{
    private final FolderRepository folderRepository;
    private final CurrentUserService currentUserService;

    public FolderService(FolderRepository folderRepository, CurrentUserService currentUserService){
        this.folderRepository = folderRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public FolderResponse createFolder(CreateFolderRequest request){
        var now = java.time.Instant.now();
        var currentUser = currentUserService.requireCurrentUser();
        var folder = new Folder(java.util.UUID.randomUUID(), request.parentId(), request.name(), now, now, currentUser.getId());
        var savedFolder = folderRepository.save(folder);
        return toResponse(savedFolder);
    }

    @Transactional(readOnly=true)
    public List<FolderResponse> listFolders(){
        var currentUser = currentUserService.requireCurrentUser();
        return folderRepository.findAllByOwnerId(currentUser.getId()).stream().map(this::toResponse).toList();
    }

    private FolderResponse toResponse(Folder folder){
        return new FolderResponse(folder.getId(), folder.getParentId(), folder.getName(), folder.getCreatedAt(), folder.getUpdatedAt());
    }
}


