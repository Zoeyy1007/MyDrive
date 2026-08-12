/*
 * PHASE 7: Build portable logical paths for synchronization events.
 *
 * Purpose
 * -------
 * Files and folders are stored in the database using ids and parent ids, not a
 * complete path. A sync client needs a portable path to know where an item
 * belongs on its local disk. This service converts a hierarchy such as:
 *
 *   Photos (parentId=null)
 *     Trips
 *       image.jpg
 *
 * into:
 *
 *   Photos/Trips/image.jpg
 *
 * This is only a logical remote path. It must not open, create, move, or delete
 * anything on the server filesystem.
 *
 * Suggested class setup
 * ---------------------
 * - Keep @Service.
 * - Inject FolderRepository through the constructor.
 * - Useful imports: ArrayDeque, Deque, HashSet, Set, UUID.
 *
 * Suggested public methods
 * ------------------------
 * Choose names consistently with SyncChangeService, for example:
 *
 *   String pathForFile(
 *       UUID ownerId,
 *       UUID syncRootFolderId,   // null means the user's whole remote root
 *       UUID parentFolderId,     // null means the file is in the remote root
 *       String filename)
 *
 *   String pathForFolder(
 *       UUID ownerId,
 *       UUID syncRootFolderId,   // null means the user's whole remote root
 *       UUID folderId)
 *
 * If this project always synchronizes the user's entire root initially, you
 * may omit syncRootFolderId from the public methods and treat null parentId as
 * the stopping point. Keeping it as a parameter prepares for syncing only one
 * selected folder later.
 *
 * File-path algorithm
 * -------------------
 * 1. Require a non-null ownerId.
 * 2. Validate filename with validateSegment(...).
 * 3. Create a Deque<String> and add filename to the front.
 * 4. Set currentFolderId = parentFolderId.
 * 5. While currentFolderId is not null and is not syncRootFolderId:
 *    a. Add its id to a Set<UUID> visited.
 *       If add(...) returns false, the database contains a parent cycle;
 *       throw IllegalStateException instead of looping forever.
 *    b. Load it using:
 *         folderRepository.findByIdAndOwnerId(currentFolderId, ownerId)
 *       Never use findById alone. A missing or foreign folder must fail rather
 *       than allowing another user's folder name into the path.
 *    c. Validate the folder's name.
 *    d. Add the folder name to the front of the deque.
 *    e. Continue with folder.getParentId().
 * 6. If syncRootFolderId is non-null, verify that it belongs to ownerId and
 *    that the walk actually reached it. Reaching null first means the resource
 *    is outside the selected sync root, so reject it.
 * 7. Return String.join("/", segments).
 *
 * Examples when syncRootFolderId is null:
 *   parentFolderId=null, filename="notes.txt"       -> "notes.txt"
 *   parent=Trips under Photos, filename="image.jpg" ->
 *       "Photos/Trips/image.jpg"
 *
 * Examples when Photos is the selected sync root:
 *   parent=Photos, filename="cover.jpg"             -> "cover.jpg"
 *   parent=Trips under Photos, filename="image.jpg" -> "Trips/image.jpg"
 *   a file under unrelated Documents                -> reject
 *
 * Folder-path algorithm
 * ---------------------
 * Use the same parent walk, but begin with folderId itself. Load each folder
 * with findByIdAndOwnerId, validate each name, prepend it, then follow
 * getParentId(). Do not include the selected root folder's own name because
 * the returned path is relative to that root.
 *
 * Segment validation
 * ------------------
 * Add a private helper such as:
 *
 *   private void validateSegment(String value)
 *
 * Reject a segment when it is:
 * - null or blank;
 * - exactly "." or "..";
 * - longer than the database's 255-character name limit;
 * - contains '/', '\\', or the NUL character '\0'.
 *
 * Rejecting slash and backslash prevents one database name from pretending to
 * be multiple directories. Rejecting dot segments prevents traversal-like
 * paths. Do not silently replace dangerous characters because the server and
 * client would then disagree about the real name.
 *
 * Why not java.nio.file.Path?
 * --------------------------
 * Path uses operating-system rules: Windows treats '\\' as a separator while
 * Unix normally does not. Sync paths must mean the same thing on Windows,
 * macOS, and Linux, so build them as validated strings joined with '/'. The
 * desktop client can safely translate the validated segments to its local
 * filesystem later.
 *
 * Rename and move events
 * ----------------------
 * For rename/move operations, calculate previousRelativePath before changing
 * the entity, then calculate relativePath after the change. Store both in the
 * same SyncChange event so clients know which old local path should be moved.
 */

package com.mydrive.drive.sync;

import com.mydrive.drive.folder.FolderRepository;
import com.mydrive.drive.folder.Folder;
import com.mydrive.drive.folder.FolderNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class RelativePathService{
    private final FolderRepository folderRepository;

    public RelativePathService(FolderRepository folderRepository){
        this.folderRepository = folderRepository;
    }

    public String pathForFile(
            UUID ownerId,
            UUID syncRootFolderId,
            UUID parentFolderId,
            String filename
    ){
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        validateSegment(filename);

        Deque<String> segments = new ArrayDeque<>();
        segments.addFirst(filename);
        prependFolderPath(ownerId, syncRootFolderId, parentFolderId, segments);
        return String.join("/", segments);
    }

    public String pathForFolder(
            UUID ownerId,
            UUID syncRootFolderId,
            UUID folderId
    ) {
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(folderId, "folderId must not be null");

        Deque<String> segments = new ArrayDeque<>();
        prependFolderPath(ownerId, syncRootFolderId, folderId, segments);
        return String.join("/", segments);
    }

    private void prependFolderPath(
            UUID ownerId,
            UUID syncRootFolderId,
            UUID startingFolderId,
            Deque<String> segments
    ) {
        if (syncRootFolderId != null) {
            // This owner-scoped lookup both validates that the root exists and
            // prevents paths from being built relative to another user's root.
            requireOwnedFolder(syncRootFolderId, ownerId);
        }

        Set<UUID> visited = new HashSet<>();
        UUID currentFolderId = startingFolderId;

        while (currentFolderId != null
                && !currentFolderId.equals(syncRootFolderId)) {
            if (!visited.add(currentFolderId)) {
                throw new IllegalStateException(
                        "Folder hierarchy contains a cycle at: " + currentFolderId);
            }

            Folder folder = requireOwnedFolder(currentFolderId, ownerId);
            validateSegment(folder.getName());
            segments.addFirst(folder.getName());
            currentFolderId = folder.getParentId();
        }

        if (syncRootFolderId != null && currentFolderId == null) {
            throw new IllegalArgumentException(
                    "Resource is outside the selected sync root: " + syncRootFolderId);
        }
    }

    private Folder requireOwnedFolder(UUID folderId, UUID ownerId) {
        return folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() -> new FolderNotFoundException(folderId));
    }

    private void validateSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Path segment must not be blank");
        }
        if (value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("Path segment must not be '.' or '..'");
        }
        if (value.length() > 255) {
            throw new IllegalArgumentException(
                    "Path segment must not be longer than 255 characters");
        }
        if (value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.indexOf('\0') >= 0
                || value.chars().anyMatch(character -> "<>:\"|?*".indexOf(character) >= 0)) {
            throw new IllegalArgumentException(
                    "Path segment contains a forbidden character");
        }
        if (value.endsWith(".") || value.endsWith(" ")) {
            throw new IllegalArgumentException(
                    "Path segment must not end with a dot or space");
        }
        String baseName = value.contains(".")
                ? value.substring(0, value.indexOf('.'))
                : value;
        if (WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Path segment is reserved by Windows: " + value);
        }
    }

    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");
}
