/*
 * PHASE 5: Version-history HTTP endpoints.
 *
 * Suggested package:
 *   com.mydrive.drive.file
 *
 * Useful imports/annotations:
 *   com.mydrive.drive.common.page.PageResponse
 *   com.mydrive.drive.file.dto.FileVersionResponse
 *   org.springframework.core.io.InputStreamResource
 *   org.springframework.http.ContentDisposition / HttpHeaders / ResponseEntity
 *   org.springframework.web.bind.annotation.*
 *   org.springframework.web.multipart.MultipartFile
 *   java.util.UUID
 *
 * Use:
 *   @RestController
 *   @RequestMapping("/api/files/{fileId}/versions")
 *
 * Dependencies:
 *   FileVersionQueryService
 *   FileVersionCommandService
 *   FileVersionDownloadService
 *
 * Endpoints:
 *
 *   GET /api/files/{fileId}/versions?page=0&size=20
 *     -> paginated FileVersionResponse history, newest first
 *
 *   POST /api/files/{fileId}/versions
 *     consumes multipart/form-data with @RequestPart("file") MultipartFile
 *     -> upload a new version and return 201
 *
 *   GET /api/files/{fileId}/versions/{versionNumber}/download
 *     -> stream the selected version using safe Content-Disposition handling
 *        like FileController.download()
 *
 *   POST /api/files/{fileId}/versions/{versionNumber}/restore
 *     -> restore the selected version as a NEW version and return 201
 *
 * Controllers should only translate HTTP input/output. Keep ownership,
 * version numbering, MinIO calls, and transactions in the services.
 */

package com.mydrive.drive.file;

import com.mydrive.drive.common.page.PageResponse;
import com.mydrive.drive.file.dto.FileVersionResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/files/{fileId}/versions")
public class FileVersionController {
    private final FileVersionQueryService fileVersionQueryService;
    private final FileVersionCommandService fileVersionCommandService;
    private final FileVersionDownloadService fileVersionDownloadService;

    public FileVersionController(
            FileVersionQueryService fileVersionQueryService,
            FileVersionCommandService fileVersionCommandService,
            FileVersionDownloadService fileVersionDownloadService) {
        this.fileVersionQueryService = fileVersionQueryService;
        this.fileVersionCommandService = fileVersionCommandService;
        this.fileVersionDownloadService = fileVersionDownloadService;
    }

    @GetMapping
    public PageResponse<FileVersionResponse> listVersions(
            @PathVariable UUID fileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return fileVersionQueryService.listVersions(fileId, page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileVersionResponse uploadVersion(
            @PathVariable UUID fileId,
            @RequestPart("file") MultipartFile file) {
        return fileVersionCommandService.uploadVersion(fileId, file);
    }

    @GetMapping("/{versionNumber}/download")
    public ResponseEntity<InputStreamResource> downloadVersion(
            @PathVariable UUID fileId,
            @PathVariable int versionNumber) {
        FileDownload download = fileVersionDownloadService.downloadVersion(fileId, versionNumber);

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

    @PostMapping("/{versionNumber}/restore")
    @ResponseStatus(HttpStatus.CREATED)
    public FileVersionResponse restoreVersion(
            @PathVariable UUID fileId,
            @PathVariable int versionNumber) {
        return fileVersionCommandService.restoreVersion(fileId, versionNumber);
    }
}
