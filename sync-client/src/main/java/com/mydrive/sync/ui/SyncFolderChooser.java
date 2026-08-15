package com.mydrive.sync.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/** Selects the local sync directory on the computer where the client is running. */
public final class SyncFolderChooser {
    private static final Logger logger = LoggerFactory.getLogger(SyncFolderChooser.class);
    private static final String LOCAL_ROOT_PROPERTY = "sync.local-root";

    private SyncFolderChooser() {}

    /**
     * Opens the chooser when the local root is missing or when selection was explicitly
     * requested. All other properties, including the existing device token, are preserved.
     */
    public static void configure(Path propertiesFile, boolean selectionRequested)
            throws IOException {
        Path absoluteProperties = propertiesFile.toAbsolutePath().normalize();
        Properties properties = load(absoluteProperties);
        Optional<Path> existing = optionalPath(properties.getProperty(LOCAL_ROOT_PROPERTY));
        if (!selectionRequested && existing.isPresent()) return;

        if (GraphicsEnvironment.isHeadless()) {
            throw new IOException(
                    "A graphical folder chooser is unavailable. Set sync.local-root in "
                            + absoluteProperties);
        }

        Path selected = showChooser(existing.orElse(null))
                .orElseThrow(() -> new IOException("Sync folder selection was cancelled"));
        saveSelection(absoluteProperties, properties, selected);
        logger.info("Local sync folder selected path={}", selected);
    }

    static void saveSelection(Path propertiesFile, Path selected) throws IOException {
        Path absoluteProperties = propertiesFile.toAbsolutePath().normalize();
        saveSelection(absoluteProperties, load(absoluteProperties), selected);
    }

    private static void saveSelection(
            Path propertiesFile,
            Properties properties,
            Path selected) throws IOException {
        Path normalized = selected.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IOException("Selected sync folder does not exist: " + normalized);
        }
        properties.setProperty(LOCAL_ROOT_PROPERTY, normalized.toString());
        saveAtomically(propertiesFile, properties);
    }

    private static Properties load(Path propertiesFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        }
        return properties;
    }

    private static Optional<Path> showChooser(Path existing) throws IOException {
        AtomicReference<Optional<Path>> result =
                new AtomicReference<>(Optional.empty());
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFileChooser chooser = existing == null
                        ? new JFileChooser()
                        : new JFileChooser(existing.toFile());
                chooser.setDialogTitle("Choose your MyDrive sync folder");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.setMultiSelectionEnabled(false);
                if (existing != null) chooser.setSelectedFile(existing.toFile());
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    result.set(Optional.of(
                            chooser.getSelectedFile().toPath().toAbsolutePath().normalize()));
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while selecting the sync folder", exception);
        } catch (InvocationTargetException exception) {
            throw new IOException("Could not open the sync folder chooser", exception.getCause());
        }
        return result.get();
    }

    private static void saveAtomically(Path propertiesFile, Properties properties)
            throws IOException {
        Path parent = propertiesFile.getParent();
        if (parent == null) {
            throw new IOException("Properties file must have a parent directory");
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".mydrive-sync-", ".properties.tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output,
                        "MyDrive sync client settings; keep device.token private");
            }
            try {
                Files.move(temporary, propertiesFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, propertiesFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Optional<Path> optionalPath(String value) {
        return Optional.ofNullable(value)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(Path::of)
                .map(path -> path.toAbsolutePath().normalize());
    }
}
