
package com.mydrive.drive.account;

import com.mydrive.drive.account.dto.RegisterRequest;
import com.mydrive.drive.account.dto.UserResponse;
import com.mydrive.drive.security.SecurityConfig;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AccountService accountService;

    @Test
    void registerReturns201WithoutPasswordOrPasswordHash() throws Exception {
        UserResponse userResponse = new UserResponse(UUID.randomUUID(), "user@example.com", Instant.now());

        when(accountService.register(any(RegisterRequest.class))).thenReturn(userResponse);
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"plaintextPassword\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerRejectsInvalidEmailWith400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"plaintextPassword\"}"))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).register(any(RegisterRequest.class));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void meReturnsAuthenticatedUser() throws Exception {
        UUID id = UUID.randomUUID();
        // Stub accountService.requireByEmail(...) to return a known AppUser
        when(accountService.requireByEmail("user@example.com")).thenReturn(new AppUser(id, "user@example.com", "plaintextPassword", Instant.now()));
        // Perform GET /api/auth/me
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Verify requireByEmail received the authenticated username
        verify(accountService).requireByEmail("user@example.com");
    }

    @Test
    void meWithoutAuthenticationReturns401() throws Exception {
        // Perform GET /api/auth/me without authentication
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
        // Verify there were no interactions with accountService
        verifyNoInteractions(accountService);
    }

}
