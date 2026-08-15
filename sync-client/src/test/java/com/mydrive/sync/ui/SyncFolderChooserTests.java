package com.mydrive.sync.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class SyncFolderChooserTests {
    @TempDir Path temporaryDirectory;

    @Test
    void savingFolderPreservesManualConnectionAndToken() throws Exception {
        Path propertiesFile = temporaryDirectory.resolve("sync-client.properties");
        Files.writeString(propertiesFile, """
                server.base-url=http://192.168.1.20:8080
                device.token=keep-this-secret
                sync.local-root=C:/old
                """);
        Path selected = Files.createDirectory(temporaryDirectory.resolve("selected"));

        SyncFolderChooser.saveSelection(propertiesFile, selected);

        Properties saved = new Properties();
        try (var input = Files.newInputStream(propertiesFile)) {
            saved.load(input);
        }
        assertThat(saved.getProperty("server.base-url"))
                .isEqualTo("http://192.168.1.20:8080");
        assertThat(saved.getProperty("device.token")).isEqualTo("keep-this-secret");
        assertThat(Path.of(saved.getProperty("sync.local-root")))
                .isEqualTo(selected.toAbsolutePath().normalize());
    }

    @Test
    void configuredFolderDoesNotRewritePropertiesOrOpenChooser() throws Exception {
        Path selected = Files.createDirectory(temporaryDirectory.resolve("selected"));
        Path propertiesFile = temporaryDirectory.resolve("sync-client.properties");
        String original = "sync.local-root=" + selected + "\n";
        Files.writeString(propertiesFile, original);

        SyncFolderChooser.configure(propertiesFile, false);

        assertThat(Files.readString(propertiesFile)).isEqualTo(original);
    }
}
