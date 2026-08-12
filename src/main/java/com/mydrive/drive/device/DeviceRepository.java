/*
 * PHASE 7 SERVER repository.
 *
 * Extend JpaRepository<Device, UUID>.
 * Suggested queries:
 *   Optional<Device> findByTokenHashAndRevokedAtIsNull(String tokenHash)
 *   Optional<Device> findByIdAndUserId(UUID id, UUID userId)
 *   List<Device> findAllByUserIdOrderByCreatedAtDesc(UUID userId)
 *
 * Repository methods only query data. Token validation, ownership decisions,
 * last-seen updates, and revocation rules belong in services.
 */

package com.mydrive.drive.device;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, java.util.UUID> {
    java.util.Optional<Device> findByTokenHashAndRevokedAtIsNull(String tokenHash);
    java.util.Optional<Device> findByIdAndUserId(java.util.UUID id, java.util.UUID userId);
    java.util.List<Device> findAllByUserIdOrderByCreatedAtDesc(java.util.UUID userId);
}