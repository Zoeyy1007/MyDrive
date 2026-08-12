package com.mydrive.sync.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStateStoreTests {
    @TempDir Path directory;

    @Test
    void createsSchemaAndStartsCursorAtZero() throws Exception {
        try (LocalStateStore store = store()) {
            assertThat(store.findAll()).isEmpty();
            assertThat(store.loadCursor()).isZero();
        }
    }

    @Test
    void upsertsFindsAndDeletesState() throws Exception {
        try (LocalStateStore store = store()) {
            LocalFileState first = state("folder/file.txt", 1);
            store.upsert(first);
            assertThat(store.find(first.relativePath())).contains(first);

            LocalFileState updated = new LocalFileState(
                    first.relativePath(), first.remoteResourceId(), "b".repeat(64), 2,
                    20, 30, SyncStatus.SYNCED, Instant.parse("2026-01-02T00:00:00Z"));
            store.upsert(updated);
            assertThat(store.findAll()).containsExactly(updated);

            store.delete(updated.relativePath());
            assertThat(store.find(updated.relativePath())).isEmpty();
        }
    }

    @Test
    void persistsCursorAcrossStoreInstances() throws Exception {
        Path database = directory.resolve("state.db");
        try (LocalStateStore store = new LocalStateStore(database)) {
            store.saveCursor(42);
        }
        try (LocalStateStore reopened = new LocalStateStore(database)) {
            assertThat(reopened.loadCursor()).isEqualTo(42);
        }
    }

    @Test
    void transactionRollbackLeavesRowsAndCursorUnchanged() throws Exception {
        try (LocalStateStore store = store()) {
            assertThatThrownBy(() -> store.inTransaction(transaction -> {
                transaction.upsert(state("will-not-commit.txt", 1));
                transaction.saveCursor(99);
                throw new IOExceptionForTest();
            })).isInstanceOf(IOExceptionForTest.class);

            assertThat(store.findAll()).isEmpty();
            assertThat(store.loadCursor()).isZero();
        }
    }

    private LocalStateStore store() throws Exception {
        return new LocalStateStore(directory.resolve("state.db"));
    }

    private LocalFileState state(String path, int version) {
        return new LocalFileState(path, UUID.randomUUID(), "a".repeat(64), version,
                10, 20, SyncStatus.SYNCED, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static class IOExceptionForTest extends Exception {}
}
