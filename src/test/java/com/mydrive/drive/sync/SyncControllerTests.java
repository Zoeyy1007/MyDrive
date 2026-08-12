package com.mydrive.drive.sync;

import com.mydrive.drive.security.SecurityConfig;
import com.mydrive.drive.sync.dto.SyncChangeBatchResponse;
import com.mydrive.drive.sync.dto.SyncChangeResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
@Import(SecurityConfig.class)
class SyncControllerTests {
    @Autowired MockMvc mockMvc;
    @MockitoBean SyncChangeService syncChangeService;

    @Test
    @WithMockUser
    void returnsAuthenticatedCursorBatchAndDelegatesParameters() throws Exception {
        UUID resourceId = UUID.randomUUID();
        when(syncChangeService.poll(8, 25)).thenReturn(new SyncChangeBatchResponse(
                List.of(new SyncChangeResponse(9, "FILE", resourceId, "UPDATED",
                        "notes.txt", null, 3, null, Instant.parse("2026-01-01T00:00:00Z"))),
                9,
                false));

        mockMvc.perform(get("/api/sync/changes").param("after", "8").param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].sequence").value(9))
                .andExpect(jsonPath("$.changes[0].resourceId").value(resourceId.toString()))
                .andExpect(jsonPath("$.changes[0].relativePath").value("notes.txt"))
                .andExpect(jsonPath("$.nextSequence").value(9))
                .andExpect(jsonPath("$.hasMore").value(false));
        verify(syncChangeService).poll(8, 25);
    }

    @Test
    @WithMockUser
    void usesDocumentedDefaults() throws Exception {
        when(syncChangeService.poll(0, 100))
                .thenReturn(new SyncChangeBatchResponse(List.of(), 0, false));

        mockMvc.perform(get("/api/sync/changes"))
                .andExpect(status().isOk());

        verify(syncChangeService).poll(0, 100);
    }

    @Test
    @WithMockUser
    void invalidQueryValuesReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/sync/changes").param("after", "not-a-number"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(syncChangeService);
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/sync/changes"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(syncChangeService);
    }
}
