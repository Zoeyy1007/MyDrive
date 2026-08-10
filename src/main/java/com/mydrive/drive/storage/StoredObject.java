
package com.mydrive.drive.storage;

public record StoredObject(
        String key,
        long size,
        String contentType
) {}