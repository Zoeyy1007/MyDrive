/*
 * PHASE 7 SERVER change-log service.
 *
 * Dependencies: SyncChangeRepository, CurrentUserService,
 * CurrentDeviceService, RelativePathService.
 *
 * poll(after, limit): validate after>=0 and limit 1..500, query only the
 * authenticated user's rows in ascending sequence, and return a cursor batch.
 *
 * recordFileChange(...) / recordFolderChange(...): create an immutable event
 * with currentDeviceId().orElse(null). These methods must run in the SAME
 * database transaction as the metadata change. Never publish an event before
 * a file/version operation is known to have succeeded.
 *
 * Browser/Postman changes use sourceDeviceId=null. A client should ignore an
 * event whose sourceDeviceId equals its own device id to avoid download/upload
 * loops, while still advancing its cursor.
 */

package com.mydrive.drive.sync;

import com.mydrive.drive.device.CurrentDeviceService;
import com.mydrive.drive.file.DriveFile;
import com.mydrive.drive.folder.Folder;
import com.mydrive.drive.security.CurrentUserService;
import com.mydrive.drive.sync.dto.SyncChangeBatchResponse;
import com.mydrive.drive.sync.dto.SyncChangeResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SyncChangeService {
    private static final int MAX_BATCH_SIZE = 500;

    private final SyncChangeRepository syncChangeRepository;
    private final CurrentUserService currentUserService;
    private final CurrentDeviceService currentDeviceService;
    private final RelativePathService relativePathService;

    public SyncChangeService(
            SyncChangeRepository syncChangeRepository,
            CurrentUserService currentUserService,
            CurrentDeviceService currentDeviceService,
            RelativePathService relativePathService) {
        this.syncChangeRepository = syncChangeRepository;
        this.currentUserService = currentUserService;
        this.currentDeviceService = currentDeviceService;
        this.relativePathService = relativePathService;
    }

    @Transactional(readOnly = true)
    public SyncChangeBatchResponse poll(long after, int limit) {
        if (after < 0) {
            throw new IllegalArgumentException("after must be zero or greater");
        }
        if (limit < 1 || limit > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and 500");
        }

        UUID userId = currentUserService.requireCurrentUser().getId();
        List<SyncChange> fetched = syncChangeRepository
                .findByUserIdAndSequenceGreaterThanOrderBySequenceAsc(
                        userId, after, PageRequest.of(0, limit + 1));
        boolean hasMore = fetched.size() > limit;
        List<SyncChange> page = hasMore
                ? new ArrayList<>(fetched.subList(0, limit))
                : fetched;
        List<SyncChangeResponse> responses = page.stream()
                .map(this::toResponse)
                .toList();
        long nextSequence = responses.isEmpty()
                ? after
                : responses.get(responses.size() - 1).sequence();
        return new SyncChangeBatchResponse(responses, nextSequence, hasMore);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SyncChange recordFileChange(
            DriveFile file,
            SyncOperation operation,
            String previousRelativePath) {
        Objects.requireNonNull(file, "file must not be null");
        return record(
                file.getOwnerId(),
                SyncResourceType.FILE,
                file.getId(),
                operation,
                relativePathService.pathForFile(
                        file.getOwnerId(), null, file.getParentFolderId(), file.getName()),
                previousRelativePath,
                file.getCurrentVersion());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SyncChange recordFolderChange(
            Folder folder,
            SyncOperation operation,
            String previousRelativePath) {
        Objects.requireNonNull(folder, "folder must not be null");
        return record(
                folder.getOwnerId(),
                SyncResourceType.FOLDER,
                folder.getId(),
                operation,
                relativePathService.pathForFolder(folder.getOwnerId(), null, folder.getId()),
                previousRelativePath,
                null);
    }

    private SyncChange record(
            UUID userId,
            SyncResourceType resourceType,
            UUID resourceId,
            SyncOperation operation,
            String relativePath,
            String previousRelativePath,
            Integer versionNumber) {
        Objects.requireNonNull(operation, "operation must not be null");
        SyncChange change = new SyncChange(
                null,
                userId,
                currentDeviceService.currentDeviceId().orElse(null),
                resourceType,
                resourceId,
                operation,
                relativePath,
                previousRelativePath,
                versionNumber,
                Instant.now());
        return syncChangeRepository.save(change);
    }

    private SyncChangeResponse toResponse(SyncChange change) {
        return new SyncChangeResponse(
                change.getSequence(),
                change.getResourceType().name(),
                change.getResourceId(),
                change.getOperation().name(),
                change.getRelativePath(),
                change.getPreviousRelativePath(),
                change.getVersionNumber(),
                change.getSourceDeviceId(),
                change.getOccurredAt());
    }
}
