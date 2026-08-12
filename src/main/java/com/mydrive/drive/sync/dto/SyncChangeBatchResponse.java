/*
 * PHASE 7 cursor response record:
 *   List<SyncChangeResponse> changes
 *   long nextSequence
 *   boolean hasMore
 *
 * nextSequence is the last returned sequence, or the input cursor when empty.
 * The client saves it to SQLite only after applying the whole batch.
 */

package com.mydrive.drive.sync.dto;

import java.util.List;

public record SyncChangeBatchResponse(
        List<SyncChangeResponse> changes,
        long nextSequence,
        boolean hasMore
){}