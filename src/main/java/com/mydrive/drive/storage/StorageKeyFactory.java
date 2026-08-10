
package com.mydrive.drive.storage;

import org.springframework.stereotype.Component;

@Component
public class StorageKeyFactory {
    public String temporaryKey(java.util.UUID ownerId, java.util.UUID uploadId) {
        return String.format("temp/%s/%s", ownerId, uploadId);
    }

    public String versionKey(java.util.UUID ownerId, java.util.UUID fileId, int version) {
        return String.format("users/%s/files/%s/versions/%d", ownerId, fileId, version);
    }
}