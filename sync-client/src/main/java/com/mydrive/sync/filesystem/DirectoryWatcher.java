package com.mydrive.sync.filesystem;

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

public class DirectoryWatcher implements AutoCloseable {
    private final Path root;
    private final IgnoreMatcher ignoreMatcher;
    private final Runnable syncRequest;
    private final WatchService watchService;
    private final Map<WatchKey, Path> directories = new ConcurrentHashMap<>();
    private final ScheduledExecutorService debounceExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "mydrive-watch-debounce"));
    private final AtomicBoolean debouncePending = new AtomicBoolean();
    private volatile boolean running;
    private Thread thread;

    public DirectoryWatcher(Path root, IgnoreMatcher ignoreMatcher, Runnable syncRequest)
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
    }

    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.take();
                Path directory = directories.get(key);
                boolean usefulEvent = false;
                if (directory != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            usefulEvent = true;
                            continue;
                        }
                        Path child = directory.resolve((Path) event.context());
                        String relative = root.equals(child)
                                ? ""
                                : root.relativize(child).toString().replace('\\', '/');
                        if (ignoreMatcher.isIgnored(relative)) continue;
                        usefulEvent = true;
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                                && Files.isDirectory(child)) {
                            registerRecursively(child);
                        }
                    }
                }
                if (!key.reset()) directories.remove(key);
                if (usefulEvent) requestDebouncedSync();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException exception) {
                requestDebouncedSync();
            } catch (java.nio.file.ClosedWatchServiceException exception) {
                break;
            }
        }
    }

    private void requestDebouncedSync() {
        if (debouncePending.compareAndSet(false, true)) {
            debounceExecutor.schedule(() -> {
                debouncePending.set(false);
                syncRequest.run();
            }, 300, TimeUnit.MILLISECONDS);
        }
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
