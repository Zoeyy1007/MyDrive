
package com.mydrive.drive.folder;

import com.mydrive.drive.security.SecurityConfig;
import tools.jackson.databind.ObjectMapper;
import com.mydrive.drive.folder.dto.CreateFolderRequest;
import com.mydrive.drive.folder.dto.FolderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(FolderController.class)
@Import(SecurityConfig.class)
@WithMockUser(username = "user@example.com")
class FolderControllerTests{
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    FolderService folderService;

    @MockitoBean
    FolderCommandService folderCommandService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createFolderReturns201AndResponseJson() throws Exception{
        FolderResponse response = new FolderResponse(
                java.util.UUID.randomUUID(),
                null,
                "My Folder",
                Instant.now(),
                Instant.now()
        );

        when(folderService.createFolder(any())).thenReturn(response);

        CreateFolderRequest req = new CreateFolderRequest("My Folder", null);
        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.name").value("My Folder"))
                .andExpect(jsonPath("$.parentId").isEmpty());
    }

    @Test
    void createFolderRejectsBlankName() throws Exception{
        CreateFolderRequest req = new CreateFolderRequest("", null);
        String body = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/folders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFoldersReturns200AndArray() throws Exception{
        FolderResponse response = new FolderResponse(
                java.util.UUID.randomUUID(),
                null,
                "List Folder",
                Instant.now(),
                Instant.now()
        );

        when(folderService.listFolders()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("List Folder"));
    }
}
