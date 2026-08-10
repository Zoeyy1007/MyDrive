
package com.mydrive.drive.storage;

public interface StorageService {
    StoredObject save(String key, java.io.InputStream content, long size, String contentType);
    java.io.InputStream load(String key);
    void copy(String sourceKey, String destinationKey);
    void delete(String key);
    boolean exists(String key);
}
