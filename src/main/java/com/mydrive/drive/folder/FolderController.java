
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
    public FolderController(FolderService folderService){
        this.folderService = folderService;
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
}