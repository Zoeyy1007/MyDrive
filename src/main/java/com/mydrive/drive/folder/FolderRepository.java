
package com.mydrive.drive.folder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderRepository extends org.springframework.data.jpa.repository.JpaRepository<Folder, java.util.UUID>{
    List<Folder> findAllByOwnerIdAndDeletedAtIsNull(UUID ownerId);
    Optional<Folder> findByIdAndOwnerId(UUID id, UUID ownerId);
}
