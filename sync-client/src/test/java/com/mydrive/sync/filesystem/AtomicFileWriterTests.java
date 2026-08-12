package com.mydrive.sync.filesystem;

import com.mydrive.sync.checksum.Sha256FileChecksum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtomicFileWriterTests {
    @TempDir Path root;
    private final AtomicFileWriter writer = new AtomicFileWriter(new PortablePathResolver());
    private final Sha256FileChecksum checksums = new Sha256FileChecksum();

    @Test
    void writesVerifiedContentAndCreatesParents() throws Exception {
        byte[] content = "hello sync".getBytes(StandardCharsets.UTF_8);
        String checksum = checksums.sha256(new ByteArrayInputStream(content));

        Path result = writer.write(root, "nested/file.txt",
                new ByteArrayInputStream(content), checksum);

        assertThat(result).hasContent("hello sync");
    }

    @Test
    void replacesExistingFileOnlyAfterSuccessfulVerification() throws Exception {
        Path existing = root.resolve("file.txt");
        Files.writeString(existing, "old");
        byte[] replacement = "new".getBytes(StandardCharsets.UTF_8);

        writer.write(root, "file.txt", new ByteArrayInputStream(replacement),
                checksums.sha256(new ByteArrayInputStream(replacement)));

        assertThat(existing).hasContent("new");
    }

    @Test
    void checksumMismatchLeavesNoDestinationOrTemporaryFile() {
        assertThatThrownBy(() -> writer.write(root, "bad/file.txt",
                new ByteArrayInputStream("wrong".getBytes(StandardCharsets.UTF_8)),
                "0".repeat(64)))
                .isInstanceOf(IOException.class);

        assertThat(root.resolve("bad/file.txt")).doesNotExist();
        assertThat(root.resolve("bad")).isEmptyDirectory();
    }

    @Test
    void rejectsTraversalBeforeWriting() {
        assertThatThrownBy(() -> writer.write(root, "../outside.txt",
                new ByteArrayInputStream(new byte[0]), "0".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
