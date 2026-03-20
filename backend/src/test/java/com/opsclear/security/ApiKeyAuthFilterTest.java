package com.opsclear.security;

import com.opsclear.model.ApiKeyModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApiKeyRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock private ApiKeyRepository apiKeyRepository;
    @Mock private UserRepository userRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private ApiKeyAuthFilter filter;

    private UUID userId;
    private UUID keyId;
    private ApiKeyModel activeKey;
    private UserModel user;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(apiKeyRepository, userRepository);
        SecurityContextHolder.clearContext();

        userId = UUID.randomUUID();
        keyId = UUID.randomUUID();

        activeKey = ApiKeyModel.builder()
                .id(keyId)
                .userId(userId)
                .name("test key")
                .keyHash(ApiKeyService.sha256("opck_validrawkey"))
                .keyPrefix("opck_validr")
                .build();

        user = UserModel.builder()
                .id(userId)
                .email("user@example.com")
                .name("User")
                .build();
    }

    @Test
    @DisplayName("Should skip filter and pass through when no X-Api-Key header present")
    void doFilterInternal_shouldPassThrough_whenNoApiKeyHeader() throws Exception {
        when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Should skip filter and pass through when X-Api-Key header is blank")
    void doFilterInternal_shouldPassThrough_whenApiKeyHeaderIsBlank() throws Exception {
        when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    @DisplayName("Should return 401 when API key is not found in the database")
    void doFilterInternal_shouldReturn401_whenKeyNotFound() throws Exception {
        when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn("opck_unknown");
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should return 401 when API key is revoked")
    void doFilterInternal_shouldReturn401_whenKeyRevoked() throws Exception {
        when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn("opck_validrawkey");
        ApiKeyModel revokedKey = ApiKeyModel.builder()
                .id(keyId).userId(userId).name("revoked")
                .keyHash(ApiKeyService.sha256("opck_validrawkey"))
                .keyPrefix("opck_validr")
                .revokedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(revokedKey));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should return 401 when API key is expired")
    void doFilterInternal_shouldReturn401_whenKeyExpired() throws Exception {
        when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn("opck_validrawkey");
        ApiKeyModel expiredKey = ApiKeyModel.builder()
                .id(keyId).userId(userId).name("expired")
                .keyHash(ApiKeyService.sha256("opck_validrawkey"))
                .keyPrefix("opck_validr")
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(expiredKey));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or revoked API key");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should return 401 when user associated with API key does not exist")
    void doFilterInternal_shouldReturn401_whenUserNotFound() throws Exception {
        when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn("opck_validrawkey");
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(activeKey));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "API key user not found");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Should authenticate, update lastUsedAt and pass through for valid key")
    void doFilterInternal_shouldAuthenticateAndPassThrough_withValidKey() throws Exception {
        when(request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER)).thenReturn("opck_validrawkey");
        when(apiKeyRepository.findByKeyHash(anyString())).thenReturn(Optional.of(activeKey));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        filter.doFilterInternal(request, response, filterChain);

        verify(response, never()).sendError(anyInt(), anyString());
        verify(apiKeyRepository).updateLastUsedAt(keyId);
        verify(filterChain).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(userId.toString());
        assertThat(auth.isAuthenticated()).isTrue();
    }
}
