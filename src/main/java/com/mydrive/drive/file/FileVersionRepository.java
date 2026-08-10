
package com.mydrive.drive.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {
    Optional<FileVersion> findByFileIdAndVersionNumber(UUID fileId, int versionNumber);
    List<FileVersion> findAllByFileId(UUID fileId);
}
