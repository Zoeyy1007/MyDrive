

package com.mydrive.drive.file;

public class FileVersionNotFoundException extends RuntimeException {

    public FileVersionNotFoundException(java.util.UUID fileId, int versionNumber) {
        super("Version " + versionNumber + " not found for file " + fileId);
    }
}