package com.mydrive.sync.http.dto;

import java.util.List;

public record RemoteChangeBatch(
        List<RemoteChange> changes,
        long nextSequence,
        boolean hasMore) {
    public RemoteChangeBatch {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
