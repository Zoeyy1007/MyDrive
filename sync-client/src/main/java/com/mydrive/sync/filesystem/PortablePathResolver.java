package com.mydrive.sync.filesystem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PortablePathResolver {
    public String toPortableRelative(Path root, Path localPath) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = localPath.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot) || normalizedPath.equals(normalizedRoot)) {
            throw new IllegalArgumentException("Local path must be a child of the sync root");
        }
        Path relative = normalizedRoot.relativize(normalizedPath);
        List<String> segments = new ArrayList<>();
        for (Path part : relative) {
            validateSegment(part.toString());
            segments.add(part.toString());
        }
        return String.join("/", segments);
    }

    public Path toLocalPath(Path root, String remoteRelativePath) {
        if (remoteRelativePath == null || remoteRelativePath.isBlank()) {
            throw new IllegalArgumentException("Remote relative path must not be blank");
        }
        if (remoteRelativePath.startsWith("/")
                || remoteRelativePath.startsWith("\\")
                || remoteRelativePath.matches("^[A-Za-z]:.*")
                || remoteRelativePath.indexOf('\0') >= 0
                || remoteRelativePath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Remote path is not portable and relative");
        }

        String[] rawSegments = remoteRelativePath.split("/", -1);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path result = normalizedRoot;
        for (String segment : rawSegments) {
            validateSegment(segment);
            result = result.resolve(segment);
        }
        result = result.normalize();
        if (!result.startsWith(normalizedRoot) || result.equals(normalizedRoot)) {
            throw new IllegalArgumentException("Remote path escapes the sync root");
        }
        return result;
    }

    private void validateSegment(String segment) {
        if (segment == null || segment.isEmpty() || segment.equals(".") || segment.equals("..")
                || segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0
                || segment.chars().anyMatch(character -> character < 32)
                || segment.chars().anyMatch(character -> "<>:\"|?*".indexOf(character) >= 0)
                || segment.endsWith(".") || segment.endsWith(" ")) {
            throw new IllegalArgumentException("Invalid path segment");
        }
        String baseName = segment.contains(".")
                ? segment.substring(0, segment.indexOf('.'))
                : segment;
        if (WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Path segment is reserved on Windows");
        }
    }

    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
}
