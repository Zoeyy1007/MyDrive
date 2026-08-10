
package com.mydrive.drive.file;

import com.mydrive.drive.common.page.PageResponse;
import com.mydrive.drive.file.dto.FileQuery;
import com.mydrive.drive.file.dto.FileResponse;
import com.mydrive.drive.security.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FileQueryService {

    private final DriveFileRepository driveFileRepository;
    private final CurrentUserService currentUserService;

    public FileQueryService(DriveFileRepository driveFileRepository, CurrentUserService currentUserService) {
        this.driveFileRepository = driveFileRepository;
        this.currentUserService = currentUserService;
    }

    public PageResponse<FileResponse> search(FileQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("Query cannot be null");
        }

        validate(query);

        UUID ownerId = currentUserService.requireCurrentUser().getId();
        FileSortField sortField = query.sort() == null
                ? FileSortField.NAME
                : query.sort();
        Sort.Direction direction = query.direction() == null
                ? Sort.Direction.ASC
                : query.direction();

        Sort sort = Sort.by(direction, sortField.getEntityProperty());
        Pageable pageable = PageRequest.of(query.page(), query.size(), sort);
        Specification<DriveFile> specification =
                DriveFileSpecification.from(ownerId, query);

        Page<DriveFile> files = driveFileRepository.findAll(specification, pageable);
        return PageResponse.from(files, this::toResponse);
    }

    public FileResponse details(UUID fileId) {
        if (fileId == null) {
            throw new IllegalArgumentException("File id cannot be null");
        }

        UUID ownerId = currentUserService.requireCurrentUser().getId();
        DriveFile file = driveFileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new FileNotFoundException(fileId));

        if (file.isDeleted() || file.getUploadStatus() != UploadStatus.READY) {
            throw new FileNotFoundException(fileId);
        }

        return toResponse(file);
    }

    private void validate(FileQuery query) {
        if (query.page() < 0) {
            throw new IllegalArgumentException("Page must be 0 or greater");
        }
        if (query.size() < 1 || query.size() > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        if (query.minSize() != null && query.minSize() < 0) {
            throw new IllegalArgumentException("Minimum size cannot be negative");
        }
        if (query.maxSize() != null && query.maxSize() < 0) {
            throw new IllegalArgumentException("Maximum size cannot be negative");
        }
        if (query.minSize() != null && query.maxSize() != null
                && query.minSize() > query.maxSize()) {
            throw new IllegalArgumentException("Minimum size cannot be greater than maximum size");
        }
        if (query.createdAfter() != null && query.createdBefore() != null
                && query.createdAfter().isAfter(query.createdBefore())) {
            throw new IllegalArgumentException("createdAfter cannot be after createdBefore");
        }
    }

    private FileResponse toResponse(DriveFile file) {
        return new FileResponse(
                file.getId(),
                file.getParentFolderId(),
                file.getName(),
                file.getContentType(),
                file.getSize(),
                file.getChecksum(),
                file.getCurrentVersion(),
                file.getUploadStatus(),
                file.getCreatedAt(),
                file.getUpdatedAt());
    }
}
