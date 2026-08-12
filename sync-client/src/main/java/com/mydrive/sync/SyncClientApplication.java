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
        Path properties = args.length == 0
                ? Path.of("sync-client.properties")
                : Path.of(args[0]);
        try {
            start(properties);
        } catch (Exception exception) {
            System.err.println("MyDrive sync could not start: " + exception.getMessage());
            System.exit(1);
        }
    }

    static void start(Path propertiesFile) throws Exception {
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

        engine.syncOnce();
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        Runnable safeSync = () -> {
            try {
                engine.syncOnce();
            } catch (Exception exception) {
                logger.error("Synchronization cycle failed", exception);
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
                safeSync,
                config.fullScanInterval().toSeconds(),
                config.fullScanInterval().toSeconds(),
                TimeUnit.SECONDS);

        DirectoryWatcher watcher = new DirectoryWatcher(
                config.localRoot(), ignores, () -> executor.execute(safeSync));
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
}
