package com.mydrive.sync.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public record SyncClientConfig(
        URI serverBaseUrl,
        Path localRoot,
        UUID remoteFolderId,
        UUID deviceId,
        String deviceToken,
        Duration pollInterval,
        Duration fullScanInterval,
        int maxChangeBatch,
        List<String> ignorePatterns) {

    public SyncClientConfig {
        if (serverBaseUrl == null || localRoot == null || remoteFolderId == null
                || deviceId == null || deviceToken == null || deviceToken.isBlank()) {
            throw new IllegalArgumentException("Required sync configuration is missing");
        }
        if (!List.of("http", "https").contains(serverBaseUrl.getScheme())) {
            throw new IllegalArgumentException("server.base-url must use http or https");
        }
        if (pollInterval == null || pollInterval.isZero() || pollInterval.isNegative()
                || fullScanInterval == null || fullScanInterval.isZero()
                || fullScanInterval.isNegative()) {
            throw new IllegalArgumentException("Sync intervals must be positive");
        }
        if (maxChangeBatch < 1 || maxChangeBatch > 500) {
            throw new IllegalArgumentException("sync.max-change-batch must be between 1 and 500");
        }
        localRoot = localRoot.toAbsolutePath().normalize();
        ignorePatterns = ignorePatterns == null ? List.of() : List.copyOf(ignorePatterns);
    }

    public static SyncClientConfig load(Path propertiesFile) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        }

        URI baseUrl = URI.create(required(properties, "server.base-url"));
        Path root = Path.of(required(properties, "sync.local-root"));
        UUID remoteFolder = parseUuid(properties, "sync.remote-folder-id");
        UUID deviceId = parseUuid(properties, "device.id");
        String token = required(properties, "device.token");
        Duration poll = Duration.ofSeconds(positiveLong(properties, "sync.poll-seconds"));
        Duration fullScan = Duration.ofSeconds(positiveLong(properties, "sync.full-scan-seconds"));
        int batch = Integer.parseInt(required(properties, "sync.max-change-batch"));
        String rawIgnores = properties.getProperty("sync.ignore", "");
        List<String> ignores = Arrays.stream(rawIgnores.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        return new SyncClientConfig(baseUrl, root, remoteFolder, deviceId, token,
                poll, fullScan, batch, ignores);
    }

    public String safeSummary() {
        return "server=" + serverBaseUrl + ", localRoot=" + localRoot
                + ", remoteFolderId=" + remoteFolderId + ", deviceId=" + deviceId;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing configuration property: " + key);
        }
        return value.trim();
    }

    private static UUID parseUuid(Properties properties, String key) {
        try {
            return UUID.fromString(required(properties, key));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid UUID in " + key, exception);
        }
    }

    private static long positiveLong(Properties properties, String key) {
        try {
            long value = Long.parseLong(required(properties, key));
            if (value <= 0) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " must be a positive integer", exception);
        }
    }
}
