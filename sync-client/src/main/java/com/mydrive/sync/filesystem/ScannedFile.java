package com.mydrive.sync.filesystem;

import java.nio.file.Path;

public record ScannedFile(
        String relativePath,
        Path absolutePath,
        long size,
        long modifiedMillis,
        String checksum) {
}
