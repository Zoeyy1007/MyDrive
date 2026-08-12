/*
 * PHASE 5 MVC tests for FileVersionController.
 *
 * Use @WebMvcTest(FileVersionController.class), @Import(SecurityConfig.class),
 * MockMvc, @MockitoBean for all three version services, and @WithMockUser.
 *
 * Test:
 *   - GET history returns pagination JSON
 *   - multipart POST returns 201 and delegates the selected file
 *   - restore POST returns 201
 *   - download returns bytes, Content-Type, Content-Length, and a safe
 *     Content-Disposition header
 *   - unauthenticated requests return 401
 */
package com.mydrive.drive.file;

import com.mydrive.drive.common.page.PageResponse;
import com.mydrive.drive.file.dto.FileVersionResponse;
import com.mydrive.drive.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileVersionController.class)
@Import(SecurityConfig.class)
class FileVersionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileVersionQueryService fileVersionQueryService;

    @MockitoBean
    private FileVersionCommandService fileVersionCommandService;

    @MockitoBean
    private FileVersionDownloadService fileVersionDownloadService;

    @Test
    @WithMockUser(username = "user@example.com")
    void historyReturnsPaginatedJson() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileVersionResponse response = response(fileId, 2, true);
        when(fileVersionQueryService.listVersions(fileId, 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(response), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/files/{fileId}/versions", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].versionNumber").value(2))
                .andExpect(jsonPath("$.content[0].current").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void multipartUploadReturns201() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileVersionResponse response = response(fileId, 2, true);
        when(fileVersionCommandService.uploadVersion(eq(fileId), any()))
                .thenReturn(response);
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.txt", "text/plain", "new content".getBytes());

        mockMvc.perform(multipart("/api/files/{fileId}/versions", fileId)
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.versionNumber").value(2));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void restoreReturns201() throws Exception {
        UUID fileId = UUID.randomUUID();
        when(fileVersionCommandService.restoreVersion(fileId, 1))
                .thenReturn(response(fileId, 3, true));

        mockMvc.perform(post("/api/files/{fileId}/versions/{versionNumber}/restore", fileId, 1)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(3))
                .andExpect(jsonPath("$.current").value(true));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void downloadReturnsBytesAndSafeHeaders() throws Exception {
        UUID fileId = UUID.randomUUID();
        byte[] bytes = "historical content".getBytes();
        when(fileVersionDownloadService.downloadVersion(fileId, 1))
                .thenReturn(new FileDownload(
                        new ByteArrayInputStream(bytes),
                        "report.txt",
                        "text/plain",
                        bytes.length));

        mockMvc.perform(get("/api/files/{fileId}/versions/{versionNumber}/download", fileId, 1))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void unauthenticatedHistoryReturns401() throws Exception {
        mockMvc.perform(get("/api/files/{fileId}/versions", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private FileVersionResponse response(UUID fileId, int versionNumber, boolean current) {
        return new FileVersionResponse(
                UUID.randomUUID(),
                fileId,
                versionNumber,
                "a".repeat(64),
                10,
                UUID.randomUUID(),
                Instant.now(),
                null,
                current);
    }
}
