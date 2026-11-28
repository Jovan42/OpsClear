package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.aop.RequiresAddonAspect;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.OrganisationModel;
import com.opsclear.repository.OrganisationRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
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

    @Mock private AddonAccessChecker addonAccessChecker;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private ProceedingJoinPoint pjp;

    private RequiresAddonAspect aspect;
    private UUID userId;
    private UUID orgId;
    private RequiresAddon annotation;

    @BeforeEach
    void setUp() {
        aspect = new RequiresAddonAspect(addonAccessChecker, organisationRepository);

        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        when(organisationRepository.findByMember(userId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).build()));
        annotation = mock(RequiresAddon.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should proceed with a read when the org has add-on access, without the write check")
    void checkAddon_shouldProceed_whenReadAndAccess() throws Throwable {
        when(annotation.value()).thenReturn(AddonCode.DASHBOARD);
        when(addonAccessChecker.hasAddonAccess(orgId, AddonCode.DASHBOARD)).thenReturn(true);
        stubSignature(true);

        aspect.checkAddon(pjp, annotation);

        verify(pjp).proceed();
        verify(addonAccessChecker, never()).isAddonUsableForWrites(any(), any());
    }

    @Test
    @DisplayName("Should proceed with a write when the add-on is usable for writes")
    void checkAddon_shouldProceed_whenWriteAndUsable() throws Throwable {
        when(annotation.value()).thenReturn(AddonCode.NOTES);
        when(addonAccessChecker.hasAddonAccess(orgId, AddonCode.NOTES)).thenReturn(true);
        when(addonAccessChecker.isAddonUsableForWrites(orgId, AddonCode.NOTES)).thenReturn(true);
        stubSignature(false);

        aspect.checkAddon(pjp, annotation);

        verify(pjp).proceed();
    }

    @Test
    @DisplayName("Should throw ForbiddenException naming the add-on when the org has no access")
    void checkAddon_shouldThrowForbidden_whenNoAccess() throws Throwable {
        when(annotation.value()).thenReturn(AddonCode.DASHBOARD);
        when(addonAccessChecker.hasAddonAccess(orgId, AddonCode.DASHBOARD)).thenReturn(false);

        assertThatThrownBy(() -> aspect.checkAddon(pjp, annotation))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("DASHBOARD add-on is required to access this feature");
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("Should throw the PAST_DUE ForbiddenException for a write the org can read but not write")
    void checkAddon_shouldThrowPastDue_whenWriteNotUsable() throws Throwable {
        when(annotation.value()).thenReturn(AddonCode.DASHBOARD);
        when(addonAccessChecker.hasAddonAccess(orgId, AddonCode.DASHBOARD)).thenReturn(true);
        when(addonAccessChecker.isAddonUsableForWrites(orgId, AddonCode.DASHBOARD)).thenReturn(false);
        stubSignature(false);

        assertThatThrownBy(() -> aspect.checkAddon(pjp, annotation))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Paddle.SUBSCRIPTION_PAST_DUE);
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("Should throw ForbiddenException when user belongs to no organisation")
    void checkAddon_shouldThrowForbidden_whenNoOrganisation() {
        when(organisationRepository.findByMember(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aspect.checkAddon(pjp, annotation))
                .isInstanceOf(ForbiddenException.class);
        verify(addonAccessChecker, never()).hasAddonAccess(any(), any());
    }

    // --- helpers ---

    private void stubSignature(boolean readOnly) throws NoSuchMethodException {
        MethodSignature signature = mock(MethodSignature.class);
        Method method = readOnly
                ? SampleEndpoint.class.getDeclaredMethod("read")
                : SampleEndpoint.class.getDeclaredMethod("write");
        when(signature.getMethod()).thenReturn(method);
        when(pjp.getSignature()).thenReturn(signature);
    }

    private static final class SampleEndpoint {
        @GetMapping
        void read() {
        }

        @PostMapping
        void write() {
        }
    }
}
