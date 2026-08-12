/*
 * PHASE 7 SERVER: JPA entity for one authorized sync client.
 *
 * Package: com.mydrive.drive.device
 * Annotations/imports: @Entity, @Table(name="devices"), @Id, @Column,
 * java.util.UUID, java.time.Instant.
 *
 * Fields must match V7: id, userId, name, tokenHash, createdAt, lastSeenAt,
 * revokedAt. tokenHash is internal and must never appear in a response DTO.
 *
 * Add methods:
 *   touch(Instant now)       -> update lastSeenAt
 *   revoke(Instant now)      -> set revokedAt
 *   isRevoked()
 * Keep id/userId/tokenHash immutable after insertion.
 */

package com.mydrive.drive.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "devices")
public class Device {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64,
            columnDefinition = "char(64)", updatable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "selected_folder_id")
    private UUID selectedFolderId;

    @Column(name = "last_processed_sequence", nullable = false)
    private long lastProcessedSequence;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    protected Device() {
        // JPA requires a no-argument constructor.
    }

    public Device(
            UUID id,
            UUID userId,
            String name,
            String tokenHash,
            Instant createdAt,
            Instant lastSeenAt,
            Instant revokedAt) {
        this(id, userId, name, tokenHash, createdAt, lastSeenAt, revokedAt,
                null, 0, null);
    }

    public Device(
            UUID id,
            UUID userId,
            String name,
            String tokenHash,
            Instant createdAt,
            Instant lastSeenAt,
            Instant revokedAt,
            UUID selectedFolderId,
            long lastProcessedSequence,
            Instant lastSyncAt) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
        this.revokedAt = revokedAt;
        this.selectedFolderId = selectedFolderId;
        this.lastProcessedSequence = lastProcessedSequence;
        this.lastSyncAt = lastSyncAt;
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    public void selectFolder(UUID folderId) {
        this.selectedFolderId = folderId;
    }

    public void acknowledge(long sequence, Instant now) {
        if (sequence < lastProcessedSequence) {
            throw new IllegalArgumentException("Sync cursor cannot move backwards");
        }
        this.lastProcessedSequence = sequence;
        this.lastSyncAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getSelectedFolderId() {
        return selectedFolderId;
    }

    public long getLastProcessedSequence() {
        return lastProcessedSequence;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }
}
