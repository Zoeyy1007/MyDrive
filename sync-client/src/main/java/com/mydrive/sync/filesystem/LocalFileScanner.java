package com.mydrive.sync.filesystem;

import com.mydrive.sync.checksum.Sha256FileChecksum;
import com.mydrive.sync.state.LocalFileState;
import com.mydrive.sync.state.LocalStateStore;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocalFileScanner {
    private final IgnoreMatcher ignoreMatcher;
    private final PortablePathResolver pathResolver;
    private final Sha256FileChecksum checksum;
    private final LocalStateStore stateStore;

    public LocalFileScanner(
            IgnoreMatcher ignoreMatcher,
            PortablePathResolver pathResolver,
            Sha256FileChecksum checksum,
            LocalStateStore stateStore) {
        this.ignoreMatcher = ignoreMatcher;
        this.pathResolver = pathResolver;
        this.checksum = checksum;
        this.stateStore = stateStore;
    }

    public Map<String, ScannedFile> scan(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Map<String, ScannedFile> files = new LinkedHashMap<>();
        Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                if (directory.equals(normalizedRoot)) return FileVisitResult.CONTINUE;
                String relative = pathResolver.toPortableRelative(normalizedRoot, directory);
                return ignoreMatcher.isIgnored(relative)
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                String relative = pathResolver.toPortableRelative(normalizedRoot, file);
                if (ignoreMatcher.isIgnored(relative)) return FileVisitResult.CONTINUE;

                long size = attrs.size();
                long modified = attrs.lastModifiedTime().toMillis();
                LocalFileState previous = stateStore.find(relative).orElse(null);
                String hash = previous != null
                        && previous.size() == size
                        && previous.modifiedMillis() == modified
                        && previous.checksum() != null
                        ? previous.checksum()
                        : checksum.sha256(file);
                files.put(relative, new ScannedFile(relative, file, size, modified, hash));
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }
}
