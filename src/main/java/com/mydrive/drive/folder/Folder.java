
package com.mydrive.drive.folder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name="folders")
public class Folder {
    @Id
    @Column(nullable=false, updatable=false)
    private UUID id;

    @Column(name="parent_id")
    private UUID parentId;

    @Column(nullable = false, length=255)
    private String name;

    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt;

    @Column(name="updated_at", nullable=false)
    private Instant updatedAt;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Folder() {
        // JPA requires a no-argument constructor
    }

    public Folder(UUID id, UUID parentId, String name, Instant createdAt, Instant updatedAt, UUID ownerId, Instant deletedAt) {
        this.id = id;
        this.parentId = parentId;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
        this.ownerId = ownerId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void rename(String new_name, Instant updatedAt) {
        this.name = new_name;
        this.updatedAt = updatedAt;
    }

    public void move(Instant updatedAt, UUID newParentId) {
        this.parentId = newParentId;
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

}

