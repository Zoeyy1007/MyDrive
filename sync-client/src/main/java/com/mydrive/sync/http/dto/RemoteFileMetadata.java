package com.mydrive.sync.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteFileMetadata(
        UUID id,
        String name,
        String checksum,
        long size,
        int currentVersion) {
}
