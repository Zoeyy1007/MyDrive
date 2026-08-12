
package com.mydrive.drive.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "files")
public class DriveFile {

    @jakarta.persistence.Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "parent_folder_id")
    private UUID parentFolderId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String checksum;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private UploadStatus uploadStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected DriveFile() {
        // JPA requires a no-argument constructor.
    }

    public DriveFile(
            UUID id,
            UUID ownerId,
            UUID parentFolderId,
            String name,
            String contentType,
            long size,
            String checksum,
            int currentVersion,
            UploadStatus uploadStatus,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.parentFolderId = parentFolderId;
        this.name = name;
        this.contentType = contentType;
        this.size = size;
        this.checksum = checksum;
        this.currentVersion = currentVersion;
        this.uploadStatus = uploadStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public void markReady() {
        this.uploadStatus = UploadStatus.READY;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.uploadStatus = UploadStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getParentFolderId() {
        return parentFolderId;
    }

    public String getName() {
        return name;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    public String getChecksum() {
        return checksum;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public UploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void rename(String new_name, Instant updatedAt) {
        this.name = new_name;
        this.updatedAt = updatedAt;
    }

    public void move(UUID new_parent_folder_id, Instant updatedAt) {
        this.parentFolderId = new_parent_folder_id;
        this.updatedAt = updatedAt;
    }

    public void moveToTrash(Instant updatedAt) {
        this.deletedAt = updatedAt;
        this.updatedAt = updatedAt;
    }

    public void restore(Instant updatedAt) {
        this.deletedAt = null;
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    public void promoteVersion(
            int newVersion,
            String newChecksum,
            long newSize,
            String newContentType,
            Instant updatedAt) {
        if (newVersion != currentVersion + 1) {
            throw new IllegalArgumentException(
                    "New version must be exactly one greater than the current version");
        }
        if (newChecksum == null || newChecksum.length() != 64) {
            throw new IllegalArgumentException("Checksum must contain exactly 64 characters");
        }
        if (newSize < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        if (newContentType == null || newContentType.isBlank()) {
            throw new IllegalArgumentException("Content type cannot be blank");
        }

        this.currentVersion = newVersion;
        this.checksum = newChecksum;
        this.size = newSize;
        this.contentType = newContentType;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
    }
}
