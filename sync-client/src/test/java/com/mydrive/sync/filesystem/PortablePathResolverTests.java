package com.mydrive.sync.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortablePathResolverTests {
    @TempDir Path root;
    private final PortablePathResolver resolver = new PortablePathResolver();

    @Test
    void convertsNestedPathsInBothDirections() {
        Path local = root.resolve("Photos").resolve("Trips").resolve("image.jpg");

        assertThat(resolver.toPortableRelative(root, local))
                .isEqualTo("Photos/Trips/image.jpg");
        assertThat(resolver.toLocalPath(root, "Photos/Trips/image.jpg"))
                .isEqualTo(local.toAbsolutePath().normalize());
    }

    @Test
    void rejectsLocalPathOutsideRoot() {
        assertThatThrownBy(() -> resolver.toPortableRelative(root, root.resolve("..").resolve("outside")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTraversalAbsoluteDriveBackslashAndNulPaths() {
        for (String path : new String[]{
                "../secret.txt", "/etc/passwd", "C:/Windows/file.txt",
                "folder\\file.txt", "folder//file.txt", "folder/./file", "bad\0name",
                "CON.txt", "bad:name.txt", "trailing. "}) {
            assertThatThrownBy(() -> resolver.toLocalPath(root, path))
                    .as(path)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
