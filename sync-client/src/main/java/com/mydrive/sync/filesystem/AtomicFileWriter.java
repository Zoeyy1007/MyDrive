package com.mydrive.sync.filesystem;

import com.mydrive.sync.checksum.Sha256FileChecksum;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

public class AtomicFileWriter {
    private final PortablePathResolver pathResolver;

    public AtomicFileWriter(PortablePathResolver pathResolver) {
        this.pathResolver = pathResolver;
    }

    public Path write(
            Path root,
            String relativePath,
            InputStream source,
            String expectedChecksum) throws IOException {
        validateChecksum(expectedChecksum);
        Path destination = pathResolver.toLocalPath(root, relativePath);
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(
                "." + destination.getFileName() + "." + UUID.randomUUID() + ".mydrive.tmp");
        boolean moved = false;
        try {
            MessageDigest digest = Sha256FileChecksum.newDigest();
            try (InputStream input = source;
                 FileChannel channel = FileChannel.open(temporary,
                         StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                 OutputStream output = new DigestOutputStream(
                         Channels.newOutputStream(channel), digest)) {
                input.transferTo(output);
                output.flush();
                channel.force(true);
            }

            String actualChecksum = HexFormat.of().formatHex(digest.digest());
            if (!actualChecksum.equalsIgnoreCase(expectedChecksum)) {
                throw new IOException("Downloaded file checksum does not match server metadata");
            }

            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            return destination;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    private void validateChecksum(String checksum) {
        if (checksum == null || !checksum.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected checksum must be 64 hexadecimal characters");
        }
    }
}
