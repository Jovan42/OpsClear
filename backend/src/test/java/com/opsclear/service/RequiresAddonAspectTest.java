package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.aop.RequiresAddonAspect;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequiresAddonAspect")
class RequiresAddonAspectTest {

    @Mock private OrgSubscriptionRepository subscriptionRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private ProceedingJoinPoint pjp;

    private RequiresAddonAspect aspect;
    private UUID userId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        aspect = new RequiresAddonAspect(subscriptionRepository, organisationRepository);

        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        when(organisationRepository.findByMember(userId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should proceed when add-on is active")
    void checkAddon_shouldProceed_whenAddonActive() throws Throwable {
        RequiresAddon annotation = mock(RequiresAddon.class);
        when(annotation.value()).thenReturn(AddonCode.DASHBOARD);
        when(subscriptionRepository.isInternal(orgId)).thenReturn(false);
        when(subscriptionRepository.hasAddon(orgId, "DASHBOARD")).thenReturn(true);

        aspect.checkAddon(pjp, annotation);

        verify(pjp).proceed();
    }

    @Test
    @DisplayName("Should bypass add-on check when org is internal")
    void checkAddon_shouldProceed_whenOrgIsInternal() throws Throwable {
        RequiresAddon annotation = mock(RequiresAddon.class);
        when(subscriptionRepository.isInternal(orgId)).thenReturn(true);

        aspect.checkAddon(pjp, annotation);

        verify(pjp).proceed();
        verify(subscriptionRepository, never()).hasAddon(any(), any());
    }

    @Test
    @DisplayName("Should throw ForbiddenException when add-on is not active")
    void checkAddon_shouldThrowForbidden_whenAddonNotActive() {
        RequiresAddon annotation = mock(RequiresAddon.class);
        when(annotation.value()).thenReturn(AddonCode.DASHBOARD);
        when(subscriptionRepository.isInternal(orgId)).thenReturn(false);
        when(subscriptionRepository.hasAddon(orgId, "DASHBOARD")).thenReturn(false);

        assertThatThrownBy(() -> aspect.checkAddon(pjp, annotation))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("DASHBOARD");
    }

    @Test
    @DisplayName("Should throw ForbiddenException when no subscription exists")
    void checkAddon_shouldThrowForbidden_whenNoSubscription() {
        RequiresAddon annotation = mock(RequiresAddon.class);
        when(annotation.value()).thenReturn(AddonCode.DASHBOARD);
        when(subscriptionRepository.isInternal(orgId)).thenReturn(false);
        when(subscriptionRepository.hasAddon(orgId, "DASHBOARD")).thenReturn(false);

        assertThatThrownBy(() -> aspect.checkAddon(pjp, annotation))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Should throw ForbiddenException when user belongs to no organisation")
    void checkAddon_shouldThrowForbidden_whenNoOrganisation() {
        RequiresAddon annotation = mock(RequiresAddon.class);
        when(organisationRepository.findByMember(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aspect.checkAddon(pjp, annotation))
                .isInstanceOf(ForbiddenException.class);
    }
}
