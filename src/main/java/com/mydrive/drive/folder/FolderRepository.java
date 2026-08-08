
package com.mydrive.drive.folder;

import java.util.List;

public interface FolderRepository extends org.springframework.data.jpa.repository.JpaRepository<Folder, java.util.UUID>{
    List<Folder> findAllByOwnerId(java.util.UUID ownerId);
}


