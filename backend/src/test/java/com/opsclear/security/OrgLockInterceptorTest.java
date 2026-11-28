package com.opsclear.security;

import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.OrgLockedException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.service.OrgAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrgLockInterceptor (ADR-0067)")
class OrgLockInterceptorTest {

    @Mock private OrganisationRepository organisationRepository;
    @Mock private OrgAccessService orgAccessService;

    private OrgLockInterceptor interceptor;
    private UUID userId;
    private UUID orgId;
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        interceptor = new OrgLockInterceptor(organisationRepository, orgAccessService);
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        // Same principal shape as an API key (ApiKeyAuthFilter): the user ID as a string.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, Collections.emptyList()));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    @Test
    @DisplayName("Switch off: passes without looking up the org")
    void preHandle_shouldPass_whenSwitchOff() {
        when(orgAccessService.isLockoutEnabled()).thenReturn(false);

        assertThat(interceptor.preHandle(request("GET", "/api/projects"), response, new Object())).isTrue();
        verify(organisationRepository, never()).findByMember(any());
    }

    @Test
    @DisplayName("Locked org on a non-allowlisted path: OrgLockedException")
    void preHandle_shouldThrow_whenLocked() {
        when(orgAccessService.isLockoutEnabled()).thenReturn(true);
        authenticate();
        when(organisationRepository.findByMember(userId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(orgAccessService.isLockedOut(orgId)).thenReturn(true);

        assertThatThrownBy(() -> interceptor.preHandle(request("GET", "/api/projects"), response, new Object()))
                .isInstanceOf(OrgLockedException.class)
                .hasMessage(ErrorMessages.Organisation.LOCKED);
    }

    @Test
    @DisplayName("An org that isn't locked out passes")
    void preHandle_shouldPass_whenNotLocked() {
        when(orgAccessService.isLockoutEnabled()).thenReturn(true);
        authenticate();
        when(organisationRepository.findByMember(userId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        when(orgAccessService.isLockedOut(orgId)).thenReturn(false);

        assertThat(interceptor.preHandle(request("POST", "/api/projects"), response, new Object())).isTrue();
    }

    @Test
    @DisplayName("A user without an org passes")
    void preHandle_shouldPass_whenNoOrg() {
        when(orgAccessService.isLockoutEnabled()).thenReturn(true);
        authenticate();
        when(organisationRepository.findByMember(userId)).thenReturn(Optional.empty());

        assertThat(interceptor.preHandle(request("POST", "/api/organisations"), response, new Object())).isTrue();
        verify(orgAccessService, never()).isLockedOut(any());
    }

    @Test
    @DisplayName("No authentication or an anonymous one passes without an org lookup")
    void preHandle_shouldPass_whenUnauthenticated() {
        when(orgAccessService.isLockoutEnabled()).thenReturn(true);

        assertThat(interceptor.preHandle(request("GET", "/api/projects"), response, new Object())).isTrue();

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(interceptor.preHandle(request("GET", "/api/projects"), response, new Object())).isTrue();

        UsernamePasswordAuthenticationToken unauthenticated =
                new UsernamePasswordAuthenticationToken(userId.toString(), null);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        assertThat(interceptor.preHandle(request("GET", "/api/projects"), response, new Object())).isTrue();

        verify(organisationRepository, never()).findByMember(any());
    }

    @Test
    @DisplayName("The org lookup is cached per request")
    void preHandle_shouldCacheTheOrgPerRequest() {
        when(orgAccessService.isLockoutEnabled()).thenReturn(true);
        authenticate();
        when(organisationRepository.findByMember(userId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        MockHttpServletRequest request = request("GET", "/api/projects");

        interceptor.preHandle(request, response, new Object());
        interceptor.preHandle(request, response, new Object());

        verify(organisationRepository, times(1)).findByMember(userId);
        verify(orgAccessService, times(2)).isLockedOut(orgId);
    }

    @ParameterizedTest(name = "{0} {1} is allowlisted: {2}")
    @CsvSource({
            "GET, /api/users/me, true",
            "PATCH, /api/users/me, true",
            "PUT, /api/user/preferences, true",
            "GET, /api/organisations/mine, true",
            "GET, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11, true",
            "PATCH, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11, false",
            "DELETE, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11, false",
            "GET, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11/subscription, true",
            "PUT, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11/subscription, true",
            "POST, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11/subscription/paddle, true",
            "POST, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11/subscription/paddle/preview, true",
            "GET, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11/credits/balance, true",
            "GET, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11/members, false",
            "POST, /api/organisations/0b8f6a3e-2f7c-4a8e-9d1a-0c2c1f0d9e11/invites, false",
            "GET, /api/subscriptions/catalog, true",
            "GET, /api/health, true",
            "POST, /api/webhooks/paddle, true",
            "GET, /api/invites/abc123, true",
            "POST, /api/invites/abc123/accept, false",
            "POST, /api/public/signups, true",
            "GET, /api/super-admin/organisations, true",
            "OPTIONS, /api/projects, true",
            "GET, /api/projects, false",
            "GET, /api/notifications/stream, false"
    })
    void isAllowlisted_shouldMatchTheAllowlist(String method, String uri, boolean allowed) {
        assertThat(OrgLockInterceptor.isAllowlisted(request(method, uri))).isEqualTo(allowed);
    }
}
