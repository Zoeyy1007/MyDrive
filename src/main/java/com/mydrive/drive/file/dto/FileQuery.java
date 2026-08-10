
package com.mydrive.drive.file.dto;

import com.mydrive.drive.file.FileSortField;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.UUID;

public record FileQuery(
        UUID parentFolderId,
        String search,
        String contentType,
        Long minSize,
        Long maxSize,
        Instant createdAfter,
        Instant createdBefore,
        boolean deleted,
        int page,
        int size,
        FileSortField sort,
        Sort.Direction direction) {
}