package com.opsclear.security;

import com.opsclear.service.UserSyncService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserSyncFilterTest {

    @Mock private UserSyncService userSyncService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private UserSyncFilter filter;

    @BeforeEach
    void setUp() {
        filter = new UserSyncFilter(userSyncService);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should sync user from JWT when authentication is JwtAuthenticationToken")
    void doFilterInternal_shouldSyncUser_whenJwtAuth() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(UUID.randomUUID().toString())
                .claim("email", "user@example.com")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        filter.doFilterInternal(request, response, filterChain);

        verify(userSyncService).syncFromJwt(jwt);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should skip sync and pass through when authentication is not JWT (e.g. API key)")
    void doFilterInternal_shouldSkipSync_whenApiKeyAuth() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null, Collections.emptyList()));

        filter.doFilterInternal(request, response, filterChain);

        verify(userSyncService, never()).syncFromJwt(org.mockito.ArgumentMatchers.any());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should skip sync and pass through when there is no authentication")
    void doFilterInternal_shouldSkipSync_whenNoAuth() throws Exception {
        filter.doFilterInternal(request, response, filterChain);

        verify(userSyncService, never()).syncFromJwt(org.mockito.ArgumentMatchers.any());
        verify(filterChain).doFilter(request, response);
    }
}
