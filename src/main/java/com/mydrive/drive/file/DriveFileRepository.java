
package com.mydrive.drive.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface DriveFileRepository extends JpaRepository<DriveFile, java.util.UUID>,
        JpaSpecificationExecutor<DriveFile> {
    java.util.List<DriveFile> findAllByOwnerIdAndParentFolderId(java.util.UUID ownerId, java.util.UUID parentFolderId);
    java.util.Optional<DriveFile> findByIdAndOwnerId(java.util.UUID id, java.util.UUID ownerId);
    Page<DriveFile> findAllByDeletedAtBefore(Instant cutoff, Pageable pageable);

}
