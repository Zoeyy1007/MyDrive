package com.mydrive.sync;

import com.mydrive.sync.config.SyncClientConfig;
import com.mydrive.sync.filesystem.AtomicFileWriter;
import com.mydrive.sync.filesystem.LocalFileScanner;
import com.mydrive.sync.filesystem.PortablePathResolver;
import com.mydrive.sync.filesystem.ScannedFile;
import com.mydrive.sync.http.SyncApiClient;
import com.mydrive.sync.http.dto.RemoteChange;
import com.mydrive.sync.http.dto.RemoteChangeBatch;
import com.mydrive.sync.http.dto.RemoteFileMetadata;
import com.mydrive.sync.http.dto.RemoteFolder;
import com.mydrive.sync.state.LocalFileState;
import com.mydrive.sync.state.LocalStateStore;
import com.mydrive.sync.state.SyncStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class SyncEngine {
    private final SyncClientConfig config;
    private final LocalFileScanner scanner;
    private final LocalStateStore stateStore;
    private final SyncApiClient apiClient;
    private final AtomicFileWriter fileWriter;
    private final PortablePathResolver pathResolver;
    private final ReentrantLock cycleLock = new ReentrantLock();

    public SyncEngine(
            SyncClientConfig config,
            LocalFileScanner scanner,
            LocalStateStore stateStore,
            SyncApiClient apiClient,
            AtomicFileWriter fileWriter,
            PortablePathResolver pathResolver) {
        this.config = config;
        this.scanner = scanner;
        this.stateStore = stateStore;
        this.apiClient = apiClient;
        this.fileWriter = fileWriter;
        this.pathResolver = pathResolver;
    }

    /** Returns false when another watcher/timer cycle is already running. */
    public boolean syncOnce() throws Exception {
        if (!cycleLock.tryLock()) return false;
        try {
            RemoteFolderTree folders = RemoteFolderTree.from(
                    apiClient.listFolders(), config.remoteFolderId());
            synchronizeLocalChanges(folders);
            pollAndApplyRemoteChanges(folders.rootPath());
            return true;
        } finally {
            cycleLock.unlock();
        }
    }

    /** Polls remote changes without performing the more expensive local scan. */
    public boolean pollRemoteOnce() throws Exception {
        if (!cycleLock.tryLock()) return false;
        try {
            RemoteFolderTree folders = RemoteFolderTree.from(
                    apiClient.listFolders(), config.remoteFolderId());
            pollAndApplyRemoteChanges(folders.rootPath());
            return true;
        } finally {
            cycleLock.unlock();
        }
    }

    private void synchronizeLocalChanges(RemoteFolderTree folders) throws Exception {
        Map<String, ScannedFile> snapshot = scanner.scan(config.localRoot());
        Map<String, LocalFileState> stored = byPath(stateStore.findAll());

        for (ScannedFile local : snapshot.values()) {
            LocalFileState previous = stored.get(local.relativePath());
            if (previous == null || previous.remoteResourceId() == null) {
                UUID parentId = folders.ensureParentFor(local.relativePath(), apiClient);
                UUID remoteId = apiClient.uploadNewFile(
                        local.relativePath(), local.absolutePath(), parentId);
                stateStore.upsert(synced(local, remoteId, 1));
            } else if (!local.checksum().equalsIgnoreCase(previous.checksum())) {
                int version = apiClient.uploadNewVersion(
                        previous.remoteResourceId(), local.absolutePath());
                stateStore.upsert(synced(local, previous.remoteResourceId(), version));
            } else if (local.size() != previous.size()
                    || local.modifiedMillis() != previous.modifiedMillis()
                    || previous.status() != SyncStatus.SYNCED) {
                stateStore.upsert(synced(
                        local, previous.remoteResourceId(), previous.remoteVersion()));
            }
        }

        for (LocalFileState previous : stored.values()) {
            if (!snapshot.containsKey(previous.relativePath())
                    && previous.status() == SyncStatus.SYNCED
                    && previous.remoteResourceId() != null) {
                apiClient.deleteFile(previous.remoteResourceId());
                stateStore.delete(previous.relativePath());
            }
        }
    }

    private void pollAndApplyRemoteChanges(String rootPath) throws Exception {
        long cursor = stateStore.loadCursor();
        boolean more;
        do {
            RemoteChangeBatch batch = apiClient.getChanges(cursor, config.maxChangeBatch());
            applyRemoteBatch(batch, rootPath);
            cursor = batch.nextSequence();
            more = batch.hasMore();
        } while (more);
    }

    private void applyRemoteBatch(RemoteChangeBatch batch, String rootPath) throws Exception {
        Map<String, LocalFileState> before = byPath(stateStore.findAll());
        Map<String, LocalFileState> after = new LinkedHashMap<>(before);

        List<RemoteChange> ordered = new ArrayList<>(batch.changes());
        ordered.sort(Comparator
                .comparing((RemoteChange change) -> !"FOLDER".equals(change.resourceType()))
                .thenComparingLong(RemoteChange::sequence));

        for (RemoteChange change : ordered) {
            if (config.deviceId().equals(change.sourceDeviceId())) continue;
            Optional<String> current = scopedPath(change.relativePath(), rootPath);
            Optional<String> previous = scopedPath(change.previousRelativePath(), rootPath);
            if (current.isEmpty() && previous.isEmpty()) continue;

            if ("FOLDER".equals(change.resourceType())) {
                applyFolderChange(change, current.orElse(null), previous.orElse(null), after);
            } else if ("FILE".equals(change.resourceType())) {
                applyFileChange(change, current.orElse(null), previous.orElse(null), after);
            }
        }

        stateStore.inTransaction(transaction -> {
            for (String oldPath : before.keySet()) {
                if (!after.containsKey(oldPath)) transaction.delete(oldPath);
            }
            for (LocalFileState state : after.values()) transaction.upsert(state);
            transaction.saveCursor(batch.nextSequence());
        });
        // Report only after files and the local SQLite cursor committed. If this
        // request fails, the next poll safely reports the same cursor again.
        apiClient.reportSyncProgress(batch.nextSequence());
    }

    private void applyFileChange(
            RemoteChange change,
            String currentPath,
            String previousPath,
            Map<String, LocalFileState> states) throws Exception {
        switch (change.operation()) {
            case "DELETED" -> {
                String path = currentPath != null ? currentPath : previousPath;
                if (path != null && !path.isEmpty()) {
                    Files.deleteIfExists(pathResolver.toLocalPath(config.localRoot(), path));
                    states.remove(path);
                }
            }
            case "RENAMED", "MOVED" -> {
                if (previousPath != null && currentPath != null
                        && !previousPath.isEmpty() && !currentPath.isEmpty()
                        && Files.exists(pathResolver.toLocalPath(config.localRoot(), previousPath))) {
                    moveLocal(previousPath, currentPath);
                    LocalFileState old = states.remove(previousPath);
                    if (old != null) {
                        Path moved = pathResolver.toLocalPath(config.localRoot(), currentPath);
                        BasicFileAttributes attrs = Files.readAttributes(moved, BasicFileAttributes.class);
                        states.put(currentPath, new LocalFileState(
                                currentPath, change.resourceId(), old.checksum(),
                                change.versionNumber() == null ? old.remoteVersion() : change.versionNumber(),
                                attrs.size(), attrs.lastModifiedTime().toMillis(),
                                SyncStatus.SYNCED, Instant.now()));
                    }
                } else if (currentPath != null && !currentPath.isEmpty()) {
                    download(change, currentPath, states);
                } else if (previousPath != null && !previousPath.isEmpty()) {
                    Files.deleteIfExists(pathResolver.toLocalPath(config.localRoot(), previousPath));
                    states.remove(previousPath);
                }
            }
            case "CREATED", "UPDATED", "RESTORED" -> {
                if (currentPath != null && !currentPath.isEmpty()) {
                    download(change, currentPath, states);
                }
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported file sync operation: " + change.operation());
        }
    }

    private void download(
            RemoteChange change,
            String relativePath,
            Map<String, LocalFileState> states) throws Exception {
        RemoteFileMetadata metadata = apiClient.getFile(change.resourceId());
        Path destination;
        try (InputStream input = apiClient.downloadFile(change.resourceId())) {
            destination = fileWriter.write(
                    config.localRoot(), relativePath, input, metadata.checksum());
        }
        BasicFileAttributes attrs = Files.readAttributes(destination, BasicFileAttributes.class);
        states.put(relativePath, new LocalFileState(
                relativePath,
                change.resourceId(),
                metadata.checksum(),
                metadata.currentVersion(),
                attrs.size(),
                attrs.lastModifiedTime().toMillis(),
                SyncStatus.SYNCED,
                Instant.now()));
    }

    private void applyFolderChange(
            RemoteChange change,
            String currentPath,
            String previousPath,
            Map<String, LocalFileState> states) throws IOException {
        switch (change.operation()) {
            case "CREATED", "RESTORED" -> {
                if (currentPath != null && !currentPath.isEmpty()) {
                    Files.createDirectories(pathResolver.toLocalPath(config.localRoot(), currentPath));
                }
            }
            case "RENAMED", "MOVED" -> {
                if (previousPath != null && currentPath != null
                        && !previousPath.isEmpty() && !currentPath.isEmpty()) {
                    Path oldDirectory = pathResolver.toLocalPath(config.localRoot(), previousPath);
                    if (Files.exists(oldDirectory)) moveLocal(previousPath, currentPath);
                    remapStatePrefix(previousPath, currentPath, states);
                } else if (currentPath != null && !currentPath.isEmpty()) {
                    Files.createDirectories(pathResolver.toLocalPath(config.localRoot(), currentPath));
                } else if (previousPath != null && !previousPath.isEmpty()) {
                    deleteTree(previousPath);
                    removeStatePrefix(previousPath, states);
                }
            }
            case "DELETED" -> {
                String path = currentPath != null ? currentPath : previousPath;
                if (path != null && !path.isEmpty()) {
                    deleteTree(path);
                    removeStatePrefix(path, states);
                }
            }
            case "UPDATED" -> { /* folders currently have no content update */ }
            default -> throw new IllegalArgumentException(
                    "Unsupported folder sync operation: " + change.operation());
        }
    }

    private void moveLocal(String oldRelativePath, String newRelativePath) throws IOException {
        Path source = pathResolver.toLocalPath(config.localRoot(), oldRelativePath);
        Path destination = pathResolver.toLocalPath(config.localRoot(), newRelativePath);
        Files.createDirectories(destination.getParent());
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTree(String relativePath) throws IOException {
        Path target = pathResolver.toLocalPath(config.localRoot(), relativePath);
        if (!Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void remapStatePrefix(
            String oldPrefix,
            String newPrefix,
            Map<String, LocalFileState> states) {
        Map<String, LocalFileState> moved = new HashMap<>();
        for (LocalFileState state : List.copyOf(states.values())) {
            if (state.relativePath().startsWith(oldPrefix + "/")) {
                states.remove(state.relativePath());
                String newPath = newPrefix + state.relativePath().substring(oldPrefix.length());
                moved.put(newPath, new LocalFileState(
                        newPath, state.remoteResourceId(), state.checksum(), state.remoteVersion(),
                        state.size(), state.modifiedMillis(), state.status(), state.lastSyncedAt()));
            }
        }
        states.putAll(moved);
    }

    private void removeStatePrefix(String prefix, Map<String, LocalFileState> states) {
        states.keySet().removeIf(path -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private Optional<String> scopedPath(String userRelativePath, String rootPath) {
        if (userRelativePath == null) return Optional.empty();
        if (userRelativePath.equals(rootPath)) return Optional.of("");
        String prefix = rootPath + "/";
        return userRelativePath.startsWith(prefix)
                ? Optional.of(userRelativePath.substring(prefix.length()))
                : Optional.empty();
    }

    private LocalFileState synced(ScannedFile file, UUID remoteId, Integer version) {
        return new LocalFileState(
                file.relativePath(), remoteId, file.checksum(), version,
                file.size(), file.modifiedMillis(), SyncStatus.SYNCED, Instant.now());
    }

    private Map<String, LocalFileState> byPath(List<LocalFileState> states) {
        Map<String, LocalFileState> result = new LinkedHashMap<>();
        for (LocalFileState state : states) result.put(state.relativePath(), state);
        return result;
    }

    private static final class RemoteFolderTree {
        private final String rootPath;
        private final UUID rootId;
        private final Map<String, UUID> relativeIds;

        private RemoteFolderTree(String rootPath, UUID rootId, Map<String, UUID> relativeIds) {
            this.rootPath = rootPath;
            this.rootId = rootId;
            this.relativeIds = relativeIds;
        }

        static RemoteFolderTree from(List<RemoteFolder> folders, UUID rootId) {
            Map<UUID, RemoteFolder> byId = new HashMap<>();
            for (RemoteFolder folder : folders) byId.put(folder.id(), folder);
            if (!byId.containsKey(rootId)) {
                throw new IllegalArgumentException("Configured remote sync folder was not found");
            }
            Map<UUID, String> paths = new HashMap<>();
            for (RemoteFolder folder : folders) {
                paths.put(folder.id(), pathFor(folder.id(), byId, new java.util.HashSet<>()));
            }
            String rootPath = paths.get(rootId);
            Map<String, UUID> relative = new HashMap<>();
            relative.put("", rootId);
            for (RemoteFolder folder : folders) {
                String path = paths.get(folder.id());
                if (path.startsWith(rootPath + "/")) {
                    relative.put(path.substring(rootPath.length() + 1), folder.id());
                }
            }
            return new RemoteFolderTree(rootPath, rootId, relative);
        }

        UUID ensureParentFor(String filePath, SyncApiClient apiClient) throws Exception {
            int slash = filePath.lastIndexOf('/');
            if (slash < 0) return rootId;
            String parentPath = filePath.substring(0, slash);
            UUID parentId = rootId;
            String built = "";
            for (String segment : parentPath.split("/")) {
                built = built.isEmpty() ? segment : built + "/" + segment;
                UUID existing = relativeIds.get(built);
                if (existing == null) {
                    existing = apiClient.createFolder(segment, parentId);
                    relativeIds.put(built, existing);
                }
                parentId = existing;
            }
            return parentId;
        }

        String rootPath() { return rootPath; }

        private static String pathFor(
                UUID id,
                Map<UUID, RemoteFolder> folders,
                Set<UUID> visiting) {
            if (!visiting.add(id)) throw new IllegalStateException("Remote folder hierarchy has a cycle");
            RemoteFolder folder = folders.get(id);
            if (folder == null) throw new IllegalStateException("Remote folder parent is missing: " + id);
            String result = folder.parentId() == null
                    ? folder.name()
                    : pathFor(folder.parentId(), folders, visiting) + "/" + folder.name();
            visiting.remove(id);
            return result;
        }
    }
}
