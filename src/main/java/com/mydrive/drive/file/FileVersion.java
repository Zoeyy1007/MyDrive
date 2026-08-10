
package com.mydrive.drive.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_versions")
public class FileVersion {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "file_id", nullable = false, updatable = false)
    private UUID fileId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(name = "storage_key", nullable = false, unique = true, length = 1024, updatable = false)
    private String storageKey;

    @Column(nullable = false, length = 64, columnDefinition = "char(64)", updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String checksum;

    @Column(nullable = false, updatable = false)
    private long size;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FileVersion() {
        // JPA requires a no-argument constructor.
    }

    public FileVersion(
            UUID id,
            UUID fileId,
            int versionNumber,
            String storageKey,
            String checksum,
            long size,
            UUID createdBy,
            Instant createdAt) {
        this.id = id;
        this.fileId = fileId;
        this.versionNumber = versionNumber;
        this.storageKey = storageKey;
        this.checksum = checksum;
        this.size = size;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFileId() {
        return fileId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getChecksum() {
        return checksum;
    }

    public long getSize() {
        return size;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
