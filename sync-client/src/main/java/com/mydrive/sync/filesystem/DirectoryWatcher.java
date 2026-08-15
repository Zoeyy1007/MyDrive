package com.mydrive.sync.filesystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public class DirectoryWatcher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DirectoryWatcher.class);
    private static final long DEBOUNCE_MILLIS = 300;

    private final Path root;
    private final IgnoreMatcher ignoreMatcher;
    private final BooleanSupplier syncRequest;
    private final WatchService watchService;
    private final Map<WatchKey, Path> directories = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debounceExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "mydrive-watch-debounce"));
    private final AtomicBoolean debouncePending = new AtomicBoolean();
    private final AtomicInteger pendingEventCount = new AtomicInteger();
    private final AtomicLong firstPendingEventNanos = new AtomicLong();
    private volatile boolean running;
    private Thread thread;

    public DirectoryWatcher(Path root, IgnoreMatcher ignoreMatcher, BooleanSupplier syncRequest)
            throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.ignoreMatcher = ignoreMatcher;
        this.syncRequest = syncRequest;
        this.watchService = FileSystems.getDefault().newWatchService();
    }

    public synchronized void start() throws IOException {
        if (running) return;
        registerRecursively(root);
        running = true;
        thread = new Thread(this::watchLoop, "mydrive-directory-watcher");
        thread.setDaemon(true);
        thread.start();
        logger.info(
                "Filesystem watcher started implementation={} root={} registeredDirectories={}",
                watchService.getClass().getName(), root, directories.size());
    }

    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.take();
                Path directory = directories.get(key);
                boolean usefulEvent = false;
                int usefulEventCount = 0;
                if (directory != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            usefulEvent = true;
                            usefulEventCount++;
                            logger.warn(
                                    "Filesystem watcher overflowed; a full scan was requested");
                            continue;
                        }
                        Path child = directory.resolve((Path) event.context());
                        String relative = root.equals(child)
                                ? ""
                                : root.relativize(child).toString().replace('\\', '/');
                        if (ignoreMatcher.isIgnored(relative)) continue;
                        usefulEvent = true;
                        usefulEventCount++;
                        logger.debug("Filesystem event kind={} path={}",
                                event.kind().name(), relative);
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                                && Files.isDirectory(child)) {
                            registerRecursively(child);
                        }
                    }
                }
                if (!key.reset()) directories.remove(key);
                if (usefulEvent) requestDebouncedSync(usefulEventCount);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException exception) {
                logger.warn(
                        "Filesystem watcher encountered an I/O error; requesting a recovery scan",
                        exception);
                requestDebouncedSync(1);
            } catch (java.nio.file.ClosedWatchServiceException exception) {
                break;
            }
        }
    }

    private void requestDebouncedSync(int eventCount) {
        pendingEventCount.addAndGet(Math.max(1, eventCount));
        firstPendingEventNanos.compareAndSet(0, System.nanoTime());
        if (debouncePending.compareAndSet(false, true)) {
            debounceExecutor.schedule(() -> {
                long detectedAt = firstPendingEventNanos.getAndSet(0);
                int events = pendingEventCount.getAndSet(0);
                debouncePending.set(false);
                logger.info(
                        "Filesystem changes detected events={} debounceMs={} detectionToSyncStartMs={}",
                        events, DEBOUNCE_MILLIS, elapsedMillis(detectedAt));
                boolean completed = syncRequest.getAsBoolean();
                if (completed) {
                    logger.info(
                            "Watcher-triggered sync finished events={} detectionToCompletionMs={}",
                            events, elapsedMillis(detectedAt));
                } else {
                    logger.info(
                            "Watcher-triggered sync did not complete events={} elapsedMs={}",
                            events, elapsedMillis(detectedAt));
                }
            }, DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return startedNanos == 0
                ? 0
                : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private void registerRecursively(Path start) throws IOException {
        try (var paths = Files.walk(start)) {
            for (Path directory : paths.filter(Files::isDirectory).toList()) {
                if (!directory.equals(root)) {
                    String relative = root.relativize(directory).toString().replace('\\', '/');
                    if (ignoreMatcher.isIgnored(relative)) continue;
                }
                WatchKey key = directory.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                directories.put(key, directory);
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        running = false;
        watchService.close();
        debounceExecutor.shutdownNow();
        if (thread != null) thread.interrupt();
    }
}
