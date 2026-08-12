package com.mydrive.sync.state;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LocalStateStore implements AutoCloseable {
    private final String jdbcUrl;
    private volatile boolean closed;

    public LocalStateStore(Path databasePath) throws IOException {
        Path absolute = databasePath.toAbsolutePath().normalize();
        if (absolute.getParent() != null) Files.createDirectories(absolute.getParent());
        this.jdbcUrl = "jdbc:sqlite:" + absolute;
        initializeSchema();
    }

    public Optional<LocalFileState> find(String relativePath) {
        requireOpen();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM local_files WHERE relative_path = ?")) {
            statement.setString(1, relativePath);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readState(result)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw databaseFailure("find local state", exception);
        }
    }

    public List<LocalFileState> findAll() {
        requireOpen();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM local_files ORDER BY relative_path");
             ResultSet result = statement.executeQuery()) {
            List<LocalFileState> states = new ArrayList<>();
            while (result.next()) states.add(readState(result));
            return states;
        } catch (SQLException exception) {
            throw databaseFailure("list local state", exception);
        }
    }

    public void upsert(LocalFileState state) {
        requireOpen();
        try (Connection connection = open()) {
            upsert(connection, state);
        } catch (SQLException exception) {
            throw databaseFailure("save local state", exception);
        }
    }

    public void delete(String relativePath) {
        requireOpen();
        try (Connection connection = open()) {
            delete(connection, relativePath);
        } catch (SQLException exception) {
            throw databaseFailure("delete local state", exception);
        }
    }

    public long loadCursor() {
        requireOpen();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT last_sequence FROM sync_cursor WHERE id = 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) throw new IllegalStateException("Sync cursor row is missing");
            return result.getLong(1);
        } catch (SQLException exception) {
            throw databaseFailure("load sync cursor", exception);
        }
    }

    public void saveCursor(long sequence) {
        requireOpen();
        if (sequence < 0) throw new IllegalArgumentException("Cursor cannot be negative");
        try (Connection connection = open()) {
            saveCursor(connection, sequence);
        } catch (SQLException exception) {
            throw databaseFailure("save sync cursor", exception);
        }
    }

    public void inTransaction(TransactionWork work) throws Exception {
        requireOpen();
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                work.execute(new Transaction(connection));
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void initializeSchema() throws IOException {
        String script;
        try (var input = LocalStateStore.class.getResourceAsStream("/sqlite-schema.sql")) {
            if (input == null) throw new IOException("sqlite-schema.sql is missing");
            script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            for (String sql : script.split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        } catch (SQLException exception) {
            throw new IOException("Could not initialize local sync database", exception);
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    private static void upsert(Connection connection, LocalFileState state) throws SQLException {
        String sql = """
                INSERT INTO local_files (
                    relative_path, remote_resource_id, checksum, remote_version,
                    size, modified_millis, sync_status, last_synced_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(relative_path) DO UPDATE SET
                    remote_resource_id=excluded.remote_resource_id,
                    checksum=excluded.checksum,
                    remote_version=excluded.remote_version,
                    size=excluded.size,
                    modified_millis=excluded.modified_millis,
                    sync_status=excluded.sync_status,
                    last_synced_at=excluded.last_synced_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, state.relativePath());
            statement.setString(2, state.remoteResourceId() == null ? null : state.remoteResourceId().toString());
            statement.setString(3, state.checksum());
            if (state.remoteVersion() == null) statement.setNull(4, java.sql.Types.INTEGER);
            else statement.setInt(4, state.remoteVersion());
            statement.setLong(5, state.size());
            statement.setLong(6, state.modifiedMillis());
            statement.setString(7, state.status().name());
            statement.setString(8, state.lastSyncedAt() == null ? null : state.lastSyncedAt().toString());
            statement.executeUpdate();
        }
    }

    private static void delete(Connection connection, String relativePath) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM local_files WHERE relative_path = ?")) {
            statement.setString(1, relativePath);
            statement.executeUpdate();
        }
    }

    private static void saveCursor(Connection connection, long sequence) throws SQLException {
        if (sequence < 0) throw new IllegalArgumentException("Cursor cannot be negative");
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE sync_cursor SET last_sequence = ? WHERE id = 1")) {
            statement.setLong(1, sequence);
            statement.executeUpdate();
        }
    }

    private static LocalFileState readState(ResultSet result) throws SQLException {
        String id = result.getString("remote_resource_id");
        int versionValue = result.getInt("remote_version");
        Integer version = result.wasNull() ? null : versionValue;
        String syncedAt = result.getString("last_synced_at");
        return new LocalFileState(
                result.getString("relative_path"),
                id == null ? null : UUID.fromString(id),
                result.getString("checksum"),
                version,
                result.getLong("size"),
                result.getLong("modified_millis"),
                SyncStatus.valueOf(result.getString("sync_status")),
                syncedAt == null ? null : Instant.parse(syncedAt));
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Local state store is closed");
    }

    private IllegalStateException databaseFailure(String action, SQLException exception) {
        return new IllegalStateException("Could not " + action, exception);
    }

    @Override
    public void close() {
        closed = true;
    }

    @FunctionalInterface
    public interface TransactionWork {
        void execute(Transaction transaction) throws Exception;
    }

    public static final class Transaction {
        private final Connection connection;

        private Transaction(Connection connection) {
            this.connection = connection;
        }

        public void upsert(LocalFileState state) throws SQLException {
            LocalStateStore.upsert(connection, state);
        }

        public void delete(String relativePath) throws SQLException {
            LocalStateStore.delete(connection, relativePath);
        }

        public void saveCursor(long sequence) throws SQLException {
            LocalStateStore.saveCursor(connection, sequence);
        }
    }
}
