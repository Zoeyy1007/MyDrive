
package com.mydrive.drive.folder;

import com.mydrive.drive.folder.dto.CreateFolderRequest;
import com.mydrive.drive.folder.dto.FolderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/folders")
public class FolderController{
    private final FolderService folderService;
    private final FolderCommandService folderCommandService;
    public FolderController(FolderService folderService, FolderCommandService folderCommandService){
        this.folderService = folderService;
        this.folderCommandService = folderCommandService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse createFolder(@Valid @RequestBody CreateFolderRequest request){
        return folderService.createFolder(request);
    }

    @GetMapping
    public List<FolderResponse> listFolders(){
        return folderService.listFolders();
    }

    @PatchMapping(path = "/{id}/rename")
    public FolderResponse renameFolder(@PathVariable java.util.UUID id, @Valid @RequestBody com.mydrive.drive.folder.dto.RenameFolderRequest request){
        return folderCommandService.rename(id, request);
    }

    @PatchMapping(path = "/{id}/move")
    public FolderResponse moveFolder(@PathVariable java.util.UUID id, @Valid @RequestBody com.mydrive.drive.folder.dto.MoveFolderRequest request){
        return folderCommandService.move(id, request);
    }

    @DeleteMapping(path = "/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFolder(@PathVariable java.util.UUID id) {
        folderCommandService.moveToTrash(id);
    }

    @PostMapping(path = "/{id}/restore")
    public FolderResponse restoreFolder(@PathVariable java.util.UUID id) {
        return folderCommandService.restore(id);
    }
}
