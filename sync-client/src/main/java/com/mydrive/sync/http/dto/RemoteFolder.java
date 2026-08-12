package com.mydrive.sync.http.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteFolder(UUID id, UUID parentId, String name) {
}
