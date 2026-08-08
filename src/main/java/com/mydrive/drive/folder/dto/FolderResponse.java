
package com.mydrive.drive.folder.dto;

import java.time.Instant;
import java.util.UUID;

public record FolderResponse(UUID id, UUID parentId, String name, Instant createdAt, Instant updatedAt){}

/*
 * PHASE 2C TODO: Decide whether clients need ownerId in this response. It is
 * safe to include the authenticated owner's UUID, but it is not required for
 * folder navigation. Regardless, Folder must store ownerId internally.
 */
