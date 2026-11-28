package com.opsclear.service;

import com.opsclear.config.LockoutProperties;
import com.opsclear.config.TrialProperties;
import com.opsclear.model.OrgAccessModel;
import com.opsclear.model.OrgAccessState;
import com.opsclear.model.OrgLimits;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.repository.OrgInviteRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrgAccessService (ADR-0067)")
class OrgAccessServiceTest {

    private static final TrialProperties TRIAL = new TrialProperties(30, 5, 3);

    @Mock private OrgSubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionTierRepository tierRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private OrgInviteRepository inviteRepository;
    @Mock private ProjectRepository projectRepository;

    private UUID orgId;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
    }

    private OrgAccessService service(boolean lockoutEnabled) {
        return new OrgAccessService(subscriptionRepository, tierRepository, organisationRepository,
                inviteRepository, projectRepository, TRIAL, new LockoutProperties(lockoutEnabled));
    }

    private void givenOrg(boolean internal, boolean realBilling, Instant trialEndsAt) {
        when(subscriptionRepository.isInternal(orgId)).thenReturn(internal);
        if (!internal) {
            when(subscriptionRepository.hasRealBilling(orgId)).thenReturn(realBilling);
        }
        when(organisationRepository.findTrialEndsAt(orgId)).thenReturn(Optional.ofNullable(trialEndsAt));
    }

    private static Instant inDays(long days) {
        return Instant.now().plus(Duration.ofDays(days));
    }

    private void givenTier(int maxMembers, Integer maxProjects) {
        UUID tierId = UUID.randomUUID();
        when(subscriptionRepository.findByOrgId(orgId))
                .thenReturn(Optional.of(OrgSubscriptionModel.builder().orgId(orgId).tierId(tierId).build()));
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(
                SubscriptionTierModel.builder().id(tierId).maxMembers(maxMembers).maxProjects(maxProjects).build()));
    }

    // --- state ---

    @Test
    @DisplayName("state: an internal org is INTERNAL, even with a running trial, without billing being checked")
    void state_shouldBeInternal_beforeTrial() {
        givenOrg(true, false, inDays(10));

        assertThat(service(true).state(orgId)).isEqualTo(OrgAccessState.INTERNAL);
        verify(subscriptionRepository, never()).hasRealBilling(any());
    }

    @Test
    @DisplayName("state: real billing beats a running trial (PAID)")
    void state_shouldBePaid_beforeTrial() {
        givenOrg(false, true, inDays(10));

        assertThat(service(true).state(orgId)).isEqualTo(OrgAccessState.PAID);
    }

    @Test
    @DisplayName("state: PAST_DUE is paid (hasRealBilling counts every status but CANCELED)")
    void state_shouldBePaid_whenPastDue() {
        // hasRealBilling is true for PAST_DUE (OrgSubscriptionRepository); the integration test checks the SQL.
        givenOrg(false, true, null);

        assertThat(service(true).state(orgId)).isEqualTo(OrgAccessState.PAID);
    }

    @Test
    @DisplayName("state: no billing and a trial end in the future is TRIAL")
    void state_shouldBeTrial_whenTrialRunning() {
        givenOrg(false, false, inDays(1));

        assertThat(service(true).state(orgId)).isEqualTo(OrgAccessState.TRIAL);
    }

    @Test
    @DisplayName("state: a trial end in the past is LOCKED (also a CANCELED subscription, which isn't real billing)")
    void state_shouldBeLocked_whenTrialOver() {
        givenOrg(false, false, inDays(-1));

        assertThat(service(true).state(orgId)).isEqualTo(OrgAccessState.LOCKED);
    }

    @Test
    @DisplayName("state: no trial at all (trial_ends_at NULL), not internal or paid, is LOCKED")
    void state_shouldBeLocked_whenNeverHadATrial() {
        givenOrg(false, false, null);

        assertThat(service(true).state(orgId)).isEqualTo(OrgAccessState.LOCKED);
    }

    @Test
    @DisplayName("state: the switch doesn't change the state, an expired trial is LOCKED with it off")
    void state_shouldBeLocked_whenSwitchOff() {
        givenOrg(false, false, inDays(-1));

        assertThat(service(false).state(orgId)).isEqualTo(OrgAccessState.LOCKED);
    }

    // --- limits ---

    @Test
    @DisplayName("limits: an internal org is unlimited")
    void limits_shouldBeUnlimited_whenInternal() {
        givenOrg(true, false, null);

        OrgLimits limits = service(true).limits(orgId);

        assertThat(limits.getMaxMembers()).isNull();
        assertThat(limits.getMaxProjects()).isNull();
    }

    @Test
    @DisplayName("limits: a paid org gets its tier's caps")
    void limits_shouldBeTheTier_whenPaidLimitedTier() {
        givenOrg(false, true, null);
        givenTier(10, 7);

        OrgLimits limits = service(true).limits(orgId);

        assertThat(limits.getMaxMembers()).isEqualTo(10);
        assertThat(limits.getMaxProjects()).isEqualTo(7);
    }

    @Test
    @DisplayName("limits: a paid org on a tier with unlimited projects gets null maxProjects")
    void limits_shouldHaveNullProjects_whenPaidUnlimitedTier() {
        givenOrg(false, true, null);
        givenTier(50, null);

        OrgLimits limits = service(true).limits(orgId);

        assertThat(limits.getMaxMembers()).isEqualTo(50);
        assertThat(limits.getMaxProjects()).isNull();
    }

    @Test
    @DisplayName("limits: a trial org gets the trial properties")
    void limits_shouldBeTrialProperties_whenTrial() {
        givenOrg(false, false, inDays(5));

        OrgLimits limits = service(true).limits(orgId);

        assertThat(limits.getMaxMembers()).isEqualTo(5);
        assertThat(limits.getMaxProjects()).isEqualTo(3);
    }

    @Test
    @DisplayName("limits: a locked org gets the trial properties")
    void limits_shouldBeTrialProperties_whenLocked() {
        givenOrg(false, false, null);

        OrgLimits limits = service(true).limits(orgId);

        assertThat(limits.getMaxMembers()).isEqualTo(5);
        assertThat(limits.getMaxProjects()).isEqualTo(3);
    }

    // --- access ---

    @Test
    @DisplayName("access: state, trial end, limits, counts and the switch")
    void access_shouldCombineStateLimitsAndUsage() {
        Instant trialEndsAt = inDays(12);
        givenOrg(false, false, trialEndsAt);
        when(organisationRepository.countMembers(orgId)).thenReturn(2);
        when(inviteRepository.countPendingByOrgId(orgId)).thenReturn(1);
        when(projectRepository.countActiveByOrgId(orgId)).thenReturn(3);

        OrgAccessModel access = service(false).access(orgId);

        assertThat(access.getState()).isEqualTo(OrgAccessState.TRIAL);
        assertThat(access.getTrialEndsAt()).isEqualTo(trialEndsAt);
        assertThat(access.getMaxMembers()).isEqualTo(5);
        assertThat(access.getMaxProjects()).isEqualTo(3);
        assertThat(access.getMemberCount()).isEqualTo(2);
        assertThat(access.getPendingInviteCount()).isEqualTo(1);
        assertThat(access.getActiveProjectCount()).isEqualTo(3);
        assertThat(access.isLockoutEnabled()).isFalse();
    }

    @Test
    @DisplayName("access: an internal org without a trial has a null trial end and no limits")
    void access_shouldHaveNullTrialEnd_whenInternal() {
        givenOrg(true, false, null);

        OrgAccessModel access = service(true).access(orgId);

        assertThat(access.getState()).isEqualTo(OrgAccessState.INTERNAL);
        assertThat(access.getTrialEndsAt()).isNull();
        assertThat(access.getMaxMembers()).isNull();
        assertThat(access.isLockoutEnabled()).isTrue();
    }

    // --- switch ---

    @Test
    @DisplayName("isLockoutEnabled reflects the property")
    void isLockoutEnabled_shouldReflectProperty() {
        assertThat(service(true).isLockoutEnabled()).isTrue();
        assertThat(service(false).isLockoutEnabled()).isFalse();
    }

    @Test
    @DisplayName("isLockedOut: LOCKED with the switch on")
    void isLockedOut_shouldBeTrue_whenLockedAndSwitchOn() {
        givenOrg(false, false, inDays(-1));

        assertThat(service(true).isLockedOut(orgId)).isTrue();
    }

    @Test
    @DisplayName("isLockedOut: a trial org with the switch on is not locked out")
    void isLockedOut_shouldBeFalse_whenTrial() {
        givenOrg(false, false, inDays(1));

        assertThat(service(true).isLockedOut(orgId)).isFalse();
    }

    @Test
    @DisplayName("isLockedOut: never with the switch off, without the state being read")
    void isLockedOut_shouldBeFalse_whenSwitchOff() {
        assertThat(service(false).isLockedOut(orgId)).isFalse();
        verify(subscriptionRepository, never()).isInternal(any());
    }
}
