package com.mydrive.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mydrive.sync.checksum.Sha256FileChecksum;
import com.mydrive.sync.config.SyncClientConfig;
import com.mydrive.sync.filesystem.AtomicFileWriter;
import com.mydrive.sync.filesystem.DirectoryWatcher;
import com.mydrive.sync.filesystem.IgnoreMatcher;
import com.mydrive.sync.filesystem.LocalFileScanner;
import com.mydrive.sync.filesystem.PortablePathResolver;
import com.mydrive.sync.http.SyncApiClient;
import com.mydrive.sync.state.LocalStateStore;
import com.mydrive.sync.ui.SyncFolderChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SyncClientApplication {
    private static final Logger logger = LoggerFactory.getLogger(SyncClientApplication.class);

    private SyncClientApplication() {}

    public static void main(String[] args) {
        try {
            LaunchOptions options = LaunchOptions.parse(args);
            start(options.propertiesFile(), options.chooseFolder());
        } catch (Exception exception) {
            System.err.println("MyDrive sync could not start: " + exception.getMessage());
            System.exit(1);
        }
    }

    static void start(Path propertiesFile) throws Exception {
        start(propertiesFile, false);
    }

    static void start(Path propertiesFile, boolean chooseFolder) throws Exception {
        SyncFolderChooser.configure(propertiesFile, chooseFolder);
        SyncClientConfig config = SyncClientConfig.load(propertiesFile);
        Files.createDirectories(config.localRoot());
        Path internalDirectory = config.localRoot().resolve(".mydrive");
        Files.createDirectories(internalDirectory);

        LocalStateStore stateStore = new LocalStateStore(internalDirectory.resolve("state.db"));
        IgnoreMatcher ignores = new IgnoreMatcher(config.ignorePatterns());
        PortablePathResolver pathResolver = new PortablePathResolver();
        Sha256FileChecksum checksum = new Sha256FileChecksum();
        LocalFileScanner scanner = new LocalFileScanner(
                ignores, pathResolver, checksum, stateStore);
        AtomicFileWriter writer = new AtomicFileWriter(pathResolver);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SyncApiClient apiClient = new SyncApiClient(config, httpClient, mapper);
        SyncEngine engine = new SyncEngine(
                config, scanner, stateStore, apiClient, writer, pathResolver);

        engine.syncOnce("startup");
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        Runnable safeScheduledSync = () -> {
            try {
                engine.syncOnce("scheduled-full-scan");
            } catch (Exception exception) {
                logger.error("Synchronization cycle failed", exception);
            }
        };
        java.util.function.BooleanSupplier safeWatcherSync = () -> {
            try {
                return engine.syncOnce("filesystem-watcher");
            } catch (Exception exception) {
                logger.error("Watcher-triggered synchronization failed", exception);
                return false;
            }
        };
        Runnable safePoll = () -> {
            try {
                engine.pollRemoteOnce();
            } catch (Exception exception) {
                logger.error("Remote change poll failed", exception);
            }
        };
        executor.scheduleWithFixedDelay(
                safePoll,
                config.pollInterval().toSeconds(),
                config.pollInterval().toSeconds(),
                TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(
                safeScheduledSync,
                config.fullScanInterval().toSeconds(),
                config.fullScanInterval().toSeconds(),
                TimeUnit.SECONDS);

        DirectoryWatcher watcher = new DirectoryWatcher(
                config.localRoot(), ignores, safeWatcherSync);
        watcher.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                watcher.close();
            } catch (Exception exception) {
                logger.warn("Could not close directory watcher", exception);
            }
            executor.shutdownNow();
            stateStore.close();
        }, "mydrive-shutdown"));

        logger.info("MyDrive sync started: {}", config.safeSummary());
    }

    private record LaunchOptions(Path propertiesFile, boolean chooseFolder) {
        private static LaunchOptions parse(String[] args) {
            Path propertiesFile = null;
            boolean chooseFolder = false;
            for (String argument : args) {
                if ("--choose-folder".equals(argument)) {
                    chooseFolder = true;
                } else if (propertiesFile == null) {
                    propertiesFile = Path.of(argument);
                } else {
                    throw new IllegalArgumentException(
                            "Usage: java -jar sync-client.jar [properties-file] [--choose-folder]");
                }
            }
            return new LaunchOptions(
                    propertiesFile == null
                            ? Path.of("sync-client.properties")
                            : propertiesFile,
                    chooseFolder);
        }
    }
}
