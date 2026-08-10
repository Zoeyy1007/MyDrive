
package com.mydrive.drive.file;

import com.mydrive.drive.common.page.PageResponse;
import com.mydrive.drive.file.dto.CopyFileRequest;
import com.mydrive.drive.file.dto.FileQuery;
import com.mydrive.drive.file.dto.FileResponse;
import com.mydrive.drive.file.dto.MoveFileRequest;
import com.mydrive.drive.file.dto.RenameFileRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileUploadService fileUploadService;
    private final FileDownloadService fileDownloadService;
    private final FileCommandService fileCommandService;
    private final FileQueryService fileQueryService;

    public FileController(
            FileUploadService fileUploadService,
            FileDownloadService fileDownloadService,
            FileCommandService fileCommandService,
            FileQueryService fileQueryService) {
        this.fileUploadService = fileUploadService;
        this.fileDownloadService = fileDownloadService;
        this.fileCommandService = fileCommandService;
        this.fileQueryService = fileQueryService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse upload(
            @RequestParam(required = false) UUID parentFolderId,
            @RequestPart("file") MultipartFile file) {
        return fileUploadService.upload(parentFolderId, file);
    }

    @GetMapping
    public PageResponse<FileResponse> search(
            @RequestParam(required = false) UUID parentFolderId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) Long minSize,
            @RequestParam(required = false) Long maxSize,
            @RequestParam(required = false) Instant createdAfter,
            @RequestParam(required = false) Instant createdBefore,
            @RequestParam(defaultValue = "false") boolean deleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "NAME") FileSortField sort,
            @RequestParam(defaultValue = "ASC") Sort.Direction direction) {
        return fileQueryService.search(new FileQuery(
                parentFolderId, search, contentType, minSize, maxSize,
                createdAfter, createdBefore, deleted, page, size, sort, direction));
    }

    @GetMapping(path = "/{id}")
    public FileResponse details(@PathVariable UUID id) {
        return fileQueryService.details(id);
    }

    @GetMapping(path = "/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID id) {
        FileDownload download = fileDownloadService.download(id);
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(download.contentType());
        } catch (IllegalArgumentException exception) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(download.inputStream()));
    }

    @PatchMapping(path = "/{id}/rename")
    public FileResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameFileRequest request) {
        return fileCommandService.rename(id, request);
    }

    @PatchMapping(path = "/{id}/move")
    public FileResponse move(@PathVariable UUID id, @Valid @RequestBody MoveFileRequest request) {
        return fileCommandService.move(id, request);
    }

    @PostMapping(path = "/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse copy(@PathVariable UUID id, @Valid @RequestBody CopyFileRequest request) {
        return fileCommandService.copy(id, request);
    }

    @DeleteMapping(path = "/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        fileCommandService.moveToTrash(id);
    }

    @PostMapping(path = "/{id}/restore")
    public FileResponse restore(@PathVariable UUID id) {
        return fileCommandService.restore(id);
    }
}
