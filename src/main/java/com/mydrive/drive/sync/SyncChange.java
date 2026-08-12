/*
 * PHASE 7 SERVER immutable @Entity mapped to sync_changes.
 *
 * Fields: Long sequence (@GeneratedValue IDENTITY), UUID userId,
 * UUID sourceDeviceId nullable, SyncResourceType resourceType (@Enumerated),
 * UUID resourceId, SyncOperation operation (@Enumerated), String relativePath,
 * String previousRelativePath nullable, Integer versionNumber nullable,
 * Instant occurredAt.
 *
 * Do not add setters. Change-log rows are append-only.
 */

package com.mydrive.drive.sync;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_changes")
public class SyncChange{

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "sequence", nullable = false, updatable = false)
    private Long sequence;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "source_device_id")
    private UUID sourceDeviceId;

    @Column(name = "resource_type", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private SyncResourceType resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "operation", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private SyncOperation operation;

    @Column(name = "relative_path", nullable = false, updatable = false)
    private String relativePath;

    @Column(name = "previous_relative_path", updatable = false)
    private String previousRelativePath;

    @Column(name = "version_number", updatable = false)
    private Integer versionNumber;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected SyncChange() {
        // JPA requires a no-argument constructor.
    }

    public SyncChange(
            Long sequence,
            UUID userId,
            UUID sourceDeviceId,
            SyncResourceType resourceType,
            UUID resourceId,
            SyncOperation operation,
            String relativePath,
            String previousRelativePath,
            Integer versionNumber,
            Instant occurredAt
    ) {
        this.sequence = sequence;
        this.userId = userId;
        this.sourceDeviceId = sourceDeviceId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.operation = operation;
        this.relativePath = relativePath;
        this.previousRelativePath = previousRelativePath;
        this.versionNumber = versionNumber;
        this.occurredAt = occurredAt;
    }

    public Long getSequence() { return sequence; }
    public UUID getUserId() { return userId; }
    public UUID getSourceDeviceId() { return sourceDeviceId; }
    public SyncResourceType getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public SyncOperation getOperation() { return operation; }
    public String getRelativePath() { return relativePath; }
    public String getPreviousRelativePath() { return previousRelativePath; }
    public Integer getVersionNumber() { return versionNumber; }
    public Instant getOccurredAt() { return occurredAt; }

}
