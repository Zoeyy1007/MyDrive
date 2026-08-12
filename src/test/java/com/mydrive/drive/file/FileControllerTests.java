package com.mydrive.drive.file;

import com.mydrive.drive.common.page.PageResponse;
import com.mydrive.drive.file.dto.FileResponse;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileController.class)
@Import(SecurityConfig.class)
class FileControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileUploadService fileUploadService;

    @MockitoBean
    private FileDownloadService fileDownloadService;

    @MockitoBean
    private FileCommandService fileCommandService;

    @MockitoBean
    private FileQueryService fileQueryService;

    @Test
    @WithMockUser(username = "user@example.com")
    void uploadReturns201AndSafeMetadata() throws Exception {
        UUID fileId = UUID.randomUUID();
        Instant now = Instant.now();
        FileResponse response = new FileResponse(
                fileId,
                null,
                "hello.txt",
                "text/plain",
                5,
                "a".repeat(64),
                1,
                UploadStatus.READY,
                now,
                now
        );
        when(fileUploadService.upload(isNull(), any())).thenReturn(response);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(fileId.toString()))
                .andExpect(jsonPath("$.name").value("hello.txt"))
                .andExpect(jsonPath("$.uploadStatus").value("READY"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void uploadWithoutAuthenticationReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void searchReturnsPaginatedMetadata() throws Exception {
        FileResponse response = response("photo.jpg");
        when(fileQueryService.search(any())).thenReturn(new PageResponse<>(
                List.of(response), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("photo.jpg"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void copyReturns201() throws Exception {
        UUID sourceId = UUID.randomUUID();
        FileResponse response = response("copy.txt");
        when(fileCommandService.copy(eq(sourceId), any())).thenReturn(response);

        mockMvc.perform(post("/api/files/{id}/copy", sourceId)
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"parentFolderId\":null,\"name\":\"copy.txt\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("copy.txt"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void downloadStreamsContentWithSafeHeaders() throws Exception {
        UUID fileId = UUID.randomUUID();
        byte[] bytes = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(fileDownloadService.download(fileId)).thenReturn(new FileDownload(
                new ByteArrayInputStream(bytes), "hello.txt", "text/plain", bytes.length));

        mockMvc.perform(get("/api/files/{id}/download", fileId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Type", "text/plain"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    private FileResponse response(String name) {
        Instant now = Instant.now();
        return new FileResponse(
                UUID.randomUUID(), null, name, "text/plain", 5,
                "a".repeat(64), 1, UploadStatus.READY, now, now);
    }
}
