/*
 * MockMvc/filter tests: valid Bearer token authenticates private endpoint,
 * invalid/revoked token returns 401, no token remains unauthenticated, and an
 * existing browser session still works.
 */
package com.mydrive.drive.device;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAuthenticationFilterTests {

    @Mock
    private DeviceTokenService deviceTokenService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private DeviceAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validBearerTokenAuthenticatesAndContinues() throws Exception {
        String rawToken = "valid-token";
        DevicePrincipal principal = new DevicePrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "user@example.com");
        when(request.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("Bearer " + rawToken);
        when(deviceTokenService.authenticate(rawToken))
                .thenReturn(Optional.of(principal));

        filter.doFilterInternal(request, response, filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(principal);
        assertThat(authentication.getName()).isEqualTo("user@example.com");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid device token");
    }

    @Test
    void invalidOrRevokedTokenReturns401AndStopsChain() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION))
                .thenReturn("Bearer invalid-token");
        when(deviceTokenService.authenticate("invalid-token"))
                .thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(response).sendError(
                HttpServletResponse.SC_UNAUTHORIZED,
                "Invalid device token");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void requestWithoutBearerTokenContinuesUnauthenticated() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(deviceTokenService);
    }

    @Test
    void existingBrowserSessionTakesPriorityOverBearerHeader() throws Exception {
        var sessionAuthentication = new UsernamePasswordAuthenticationToken(
                "browser@example.com",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(sessionAuthentication);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isSameAs(sessionAuthentication);
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(deviceTokenService);
        verifyNoInteractions(response);
    }
}
