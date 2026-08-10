package com.mydrive.drive.folder;

import java.util.UUID;

public class FolderNotFoundException extends RuntimeException {

    public FolderNotFoundException(UUID folderId) {
        super("Folder not found: " + folderId);
    }
}
