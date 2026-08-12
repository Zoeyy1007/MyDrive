

package com.mydrive.drive.file;

import com.mydrive.drive.common.page.PageResponse;
import com.mydrive.drive.file.dto.FileVersionResponse;
import com.mydrive.drive.security.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FileVersionQueryService {

    private final DriveFileRepository driveFileRepository;
    private final FileVersionRepository fileVersionRepository;
    private final CurrentUserService currentUserService;

    public FileVersionQueryService(DriveFileRepository driveFileRepository, FileVersionRepository fileVersionRepository, CurrentUserService currentUserService) {
        this.driveFileRepository = driveFileRepository;
        this.fileVersionRepository = fileVersionRepository;
        this.currentUserService = currentUserService;
    }

    public PageResponse<FileVersionResponse> listVersions(UUID fileId, int page, int size) {
        if (fileId == null) {
            throw new IllegalArgumentException("File id cannot be null");
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page must be non-negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }

        UUID ownerId = currentUserService.requireCurrentUser().getId();
        DriveFile file = driveFileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
        if (file.isDeleted() || file.getUploadStatus() != UploadStatus.READY) {
            throw new FileNotFoundException(fileId);
        }

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "versionNumber"));
        var versionsPage = fileVersionRepository.findAllByFileId(fileId, pageable);

        return PageResponse.from(versionsPage, version -> {
            boolean isCurrent = version.getVersionNumber() == file.getCurrentVersion();
            return new FileVersionResponse(
                    version.getId(),
                    version.getFileId(),
                    version.getVersionNumber(),
                    version.getChecksum(),
                    version.getSize(),
                    version.getCreatedBy(),
                    version.getCreatedAt(),
                    version.getSourceDeviceId(),
                    isCurrent);
        });
    }
}
