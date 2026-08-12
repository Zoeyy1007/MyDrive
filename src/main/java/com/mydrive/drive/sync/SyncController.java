/*
 * PHASE 7 SERVER polling endpoint.
 *
 * @RestController, @RequestMapping("/api/sync").
 * GET /api/sync/changes?after=0&limit=100 -> SyncChangeBatchResponse.
 *
 * The first client may reuse existing authenticated endpoints:
 *   new file:       POST /api/files/upload
 *   updated file:   POST /api/files/{id}/versions
 *   download:       GET  /api/files/{id}/download
 *   delete:         DELETE /api/files/{id}
 *   create folder:  POST /api/folders
 *
 * Keeping transfers in existing services avoids duplicating MinIO logic.
 */

package com.mydrive.drive.sync;

import com.mydrive.drive.sync.dto.SyncChangeBatchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncController {
    private final SyncChangeService syncChangeService;

    public SyncController(SyncChangeService syncChangeService) {
        this.syncChangeService = syncChangeService;
    }

    @GetMapping("/changes")
    public SyncChangeBatchResponse changes(
            @RequestParam(defaultValue = "0") long after,
            @RequestParam(defaultValue = "100") int limit) {
        return syncChangeService.poll(after, limit);
    }
}
