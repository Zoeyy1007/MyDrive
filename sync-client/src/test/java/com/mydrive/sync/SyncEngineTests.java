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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncEngineTests {
    @TempDir Path root;
    @Mock LocalFileScanner scanner;
    @Mock SyncApiClient apiClient;
    @Mock AtomicFileWriter writer;

    private UUID rootFolderId;
    private UUID deviceId;
    private SyncClientConfig config;
    private LocalStateStore stateStore;
    private SyncEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        rootFolderId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
        config = new SyncClientConfig(
                URI.create("http://localhost:8080"), root, rootFolderId, deviceId,
                "secret", Duration.ofSeconds(10), Duration.ofSeconds(60), 100, List.of());
        stateStore = new LocalStateStore(root.resolve(".mydrive/state.db"));
        engine = new SyncEngine(
                config, scanner, stateStore, apiClient, writer, new PortablePathResolver());
        when(apiClient.listFolders()).thenReturn(List.of(
                new RemoteFolder(rootFolderId, null, "SyncRoot")));
    }

    @AfterEach
    void tearDown() {
        stateStore.close();
    }

    @Test
    void uploadsNewLocalFileAndStoresSyncedState() throws Exception {
        Path file = Files.writeString(root.resolve("hello.txt"), "hello");
        ScannedFile scanned = new ScannedFile(
                "hello.txt", file, Files.size(file), Files.getLastModifiedTime(file).toMillis(),
                "a".repeat(64));
        UUID remoteId = UUID.randomUUID();
        when(scanner.scan(root)).thenReturn(Map.of(scanned.relativePath(), scanned));
        when(apiClient.uploadNewFile("hello.txt", file, rootFolderId)).thenReturn(remoteId);
        emptyPoll();

        assertThat(engine.syncOnce()).isTrue();

        LocalFileState saved = stateStore.find("hello.txt").orElseThrow();
        assertThat(saved.remoteResourceId()).isEqualTo(remoteId);
        assertThat(saved.remoteVersion()).isEqualTo(1);
        assertThat(saved.status()).isEqualTo(SyncStatus.SYNCED);
    }

    @Test
    void propagatesDeletionOfPreviouslySyncedFile() throws Exception {
        UUID remoteId = UUID.randomUUID();
        stateStore.upsert(state("gone.txt", remoteId));
        when(scanner.scan(root)).thenReturn(Map.of());
        emptyPoll();

        engine.syncOnce();

        verify(apiClient).deleteFile(remoteId);
        assertThat(stateStore.find("gone.txt")).isEmpty();
    }

    @Test
    void downloadsRemoteFileAndAdvancesCursor() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(scanner.scan(root)).thenReturn(Map.of());
        when(apiClient.getChanges(0, 100)).thenReturn(new RemoteChangeBatch(
                List.of(change(5, fileId, null, "CREATED")), 5, false));
        when(apiClient.getFile(fileId)).thenReturn(
                new RemoteFileMetadata(fileId, "remote.txt", "b".repeat(64), 6, 2));
        when(apiClient.downloadFile(fileId)).thenReturn(
                new ByteArrayInputStream("remote".getBytes()));
        when(writer.write(eq(root), eq("remote.txt"), any(), eq("b".repeat(64))))
                .thenAnswer(invocation -> Files.writeString(root.resolve("remote.txt"), "remote"));

        engine.syncOnce();

        assertThat(stateStore.loadCursor()).isEqualTo(5);
        assertThat(stateStore.find("remote.txt").orElseThrow().remoteVersion()).isEqualTo(2);
        verify(apiClient).reportSyncProgress(5);
    }

    @Test
    void skipsOwnDeviceEventButAdvancesCursor() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(scanner.scan(root)).thenReturn(Map.of());
        when(apiClient.getChanges(0, 100)).thenReturn(new RemoteChangeBatch(
                List.of(change(7, fileId, deviceId, "UPDATED")), 7, false));

        engine.syncOnce();

        assertThat(stateStore.loadCursor()).isEqualTo(7);
        verify(apiClient).reportSyncProgress(7);
        verify(apiClient, never()).downloadFile(any());
    }

    @Test
    void failedRemoteBatchKeepsOldCursor() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(scanner.scan(root)).thenReturn(Map.of());
        when(apiClient.getChanges(0, 100)).thenReturn(new RemoteChangeBatch(
                List.of(change(9, fileId, null, "UPDATED")), 9, false));
        when(apiClient.getFile(fileId)).thenReturn(
                new RemoteFileMetadata(fileId, "remote.txt", "c".repeat(64), 4, 3));
        when(apiClient.downloadFile(fileId)).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(writer.write(any(), any(), any(), any())).thenThrow(new IOException("disk full"));

        assertThatThrownBy(() -> engine.syncOnce()).isInstanceOf(IOException.class);
        assertThat(stateStore.loadCursor()).isZero();
    }

    @Test
    void overlappingWatcherCycleIsSkipped() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(scanner.scan(root)).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return Map.of();
        });
        emptyPoll();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> engine.syncOnce());
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(engine.syncOnce()).isFalse();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void emptyPoll() throws Exception {
        when(apiClient.getChanges(0, 100))
                .thenReturn(new RemoteChangeBatch(List.of(), 0, false));
    }

    private LocalFileState state(String path, UUID remoteId) {
        return new LocalFileState(path, remoteId, "a".repeat(64), 1,
                1, 1, SyncStatus.SYNCED, Instant.now());
    }

    private RemoteChange change(long sequence, UUID resourceId, UUID source, String operation) {
        return new RemoteChange(sequence, "FILE", resourceId, operation,
                "SyncRoot/remote.txt", null, 2, source, Instant.now());
    }
}
