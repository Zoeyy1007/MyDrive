/*
 * PHASE 7 SERVER repository extending JpaRepository<SyncChange, Long>.
 * Add a cursor query such as:
 *   List<SyncChange> findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(
 *       UUID userId, long after, Pageable pageable)
 *
 * Never return another user's events. Limit every poll to a small page.
 */

package com.mydrive.drive.sync;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncChangeRepository extends JpaRepository<SyncChange, Long> {
    java.util.List<SyncChange> findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(
            java.util.UUID userId, long after, org.springframework.data.domain.Pageable pageable);
    java.util.Optional<SyncChange> findFirstByUserIdOrderBySequenceDesc(java.util.UUID userId);
}
