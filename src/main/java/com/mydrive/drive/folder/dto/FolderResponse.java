
package com.mydrive.drive.folder.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Folder data needed by a client to render folder navigation.
 *
 * ownerId is deliberately omitted. The server gets the owner from the logged-in
 * session and applies it in repository queries; clients neither need nor control it.
 */
public record FolderResponse(UUID id, UUID parentId, String name, Instant createdAt, Instant updatedAt){}
