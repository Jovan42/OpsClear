package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.model.OrgAccessState;
import com.opsclear.repository.OrgSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddonAccessChecker (JOB-285, ADR-0067)")
class AddonAccessCheckerTest {

    @Mock private OrgSubscriptionRepository subscriptionRepository;
    @Mock private OrgAccessService orgAccessService;

    private AddonAccessChecker checker;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        checker = new AddonAccessChecker(subscriptionRepository, orgAccessService);
        orgId = UUID.randomUUID();
    }

    private void givenState(OrgAccessState state) {
        when(orgAccessService.state(orgId)).thenReturn(state);
    }

    // --- hasAddonAccess ---

    @Test
    @DisplayName("hasAddonAccess: an internal org reads every add-on without the subscription being checked")
    void hasAddonAccess_shouldReturnTrue_whenInternal() {
        givenState(OrgAccessState.INTERNAL);

        assertThat(checker.hasAddonAccess(orgId, AddonCode.NOTES)).isTrue();
        verify(subscriptionRepository, never()).hasAddon(any(), anyString());
    }

    @Test
    @DisplayName("hasAddonAccess: a trial org reads every add-on without the subscription being checked")
    void hasAddonAccess_shouldReturnTrue_whenTrial() {
        givenState(OrgAccessState.TRIAL);

        assertThat(checker.hasAddonAccess(orgId, AddonCode.NOTES)).isTrue();
        verify(subscriptionRepository, never()).hasAddon(any(), anyString());
    }

    @Test
    @DisplayName("hasAddonAccess: a paid org with the add-on can read it")
    void hasAddonAccess_shouldReturnTrue_whenPaidWithAddon() {
        givenState(OrgAccessState.PAID);
        when(subscriptionRepository.hasAddon(orgId, "NOTES")).thenReturn(true);

        assertThat(checker.hasAddonAccess(orgId, AddonCode.NOTES)).isTrue();
    }

    @Test
    @DisplayName("hasAddonAccess: a paid org without the add-on can't read it")
    void hasAddonAccess_shouldReturnFalse_whenPaidWithoutAddon() {
        givenState(OrgAccessState.PAID);
        when(subscriptionRepository.hasAddon(orgId, "NOTES")).thenReturn(false);

        assertThat(checker.hasAddonAccess(orgId, AddonCode.NOTES)).isFalse();
    }

    @Test
    @DisplayName("hasAddonAccess: a PAST_DUE paid org keeps reads, without the status being checked")
    void hasAddonAccess_shouldIgnorePastDue() {
        givenState(OrgAccessState.PAID);
        when(subscriptionRepository.hasAddon(orgId, "NOTES")).thenReturn(true);

        assertThat(checker.hasAddonAccess(orgId, AddonCode.NOTES)).isTrue();
        verify(subscriptionRepository, never()).findSubscriptionStatus(any());
    }

    @Test
    @DisplayName("hasAddonAccess: a locked org has no add-ons while the lock-out switch is on")
    void hasAddonAccess_shouldReturnFalse_whenLockedAndSwitchOn() {
        givenState(OrgAccessState.LOCKED);
        when(orgAccessService.isLockoutEnabled()).thenReturn(true);

        assertThat(checker.hasAddonAccess(orgId, AddonCode.NOTES)).isFalse();
    }

    @Test
    @DisplayName("hasAddonAccess: a locked org keeps every add-on while the lock-out switch is off")
    void hasAddonAccess_shouldReturnTrue_whenLockedAndSwitchOff() {
        givenState(OrgAccessState.LOCKED);
        when(orgAccessService.isLockoutEnabled()).thenReturn(false);

        assertThat(checker.hasAddonAccess(orgId, AddonCode.NOTES)).isTrue();
        verify(subscriptionRepository, never()).hasAddon(any(), anyString());
    }

    // --- isAddonUsableForWrites ---

    @Test
    @DisplayName("isAddonUsableForWrites: internal org, without billing or status being checked")
    void isAddonUsableForWrites_shouldReturnTrue_whenOrgInternal() {
        givenState(OrgAccessState.INTERNAL);

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isTrue();
        verify(subscriptionRepository, never()).hasAddon(any(), anyString());
        verify(subscriptionRepository, never()).findSubscriptionStatus(any());
    }

    @Test
    @DisplayName("isAddonUsableForWrites: trial org, like internal")
    void isAddonUsableForWrites_shouldReturnTrue_whenTrial() {
        givenState(OrgAccessState.TRIAL);

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isTrue();
        verify(subscriptionRepository, never()).findSubscriptionStatus(any());
    }

    @Test
    @DisplayName("isAddonUsableForWrites: paid with the add-on and an ACTIVE subscription")
    void isAddonUsableForWrites_shouldReturnTrue_whenBilledAddonAndActive() {
        givenState(OrgAccessState.PAID);
        when(subscriptionRepository.hasAddon(orgId, "APPROVALS")).thenReturn(true);
        when(subscriptionRepository.findSubscriptionStatus(orgId)).thenReturn(Optional.of("ACTIVE"));

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isTrue();
    }

    @Test
    @DisplayName("isAddonUsableForWrites: paid with the add-on but PAST_DUE is not usable")
    void isAddonUsableForWrites_shouldReturnFalse_whenPastDue() {
        givenState(OrgAccessState.PAID);
        when(subscriptionRepository.hasAddon(orgId, "APPROVALS")).thenReturn(true);
        when(subscriptionRepository.findSubscriptionStatus(orgId)).thenReturn(Optional.of("PAST_DUE"));

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isFalse();
    }

    @Test
    @DisplayName("isAddonUsableForWrites: paid without the add-on, without the status being checked")
    void isAddonUsableForWrites_shouldReturnFalse_whenAddonMissing() {
        givenState(OrgAccessState.PAID);
        when(subscriptionRepository.hasAddon(orgId, "APPROVALS")).thenReturn(false);

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isFalse();
        verify(subscriptionRepository, never()).findSubscriptionStatus(any());
    }

    @Test
    @DisplayName("isAddonUsableForWrites: locked with the switch on is not usable")
    void isAddonUsableForWrites_shouldReturnFalse_whenLockedAndSwitchOn() {
        givenState(OrgAccessState.LOCKED);
        when(orgAccessService.isLockoutEnabled()).thenReturn(true);

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isFalse();
    }

    @Test
    @DisplayName("isAddonUsableForWrites: locked with the switch off is usable, like a trial")
    void isAddonUsableForWrites_shouldReturnTrue_whenLockedAndSwitchOff() {
        givenState(OrgAccessState.LOCKED);
        when(orgAccessService.isLockoutEnabled()).thenReturn(false);

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isTrue();
    }

    // --- building blocks ---

    @Test
    @DisplayName("isInternal delegates to the subscription repository")
    void isInternal_shouldDelegate() {
        when(subscriptionRepository.isInternal(orgId)).thenReturn(true);

        assertThat(checker.isInternal(orgId)).isTrue();
    }

    @Test
    @DisplayName("isPastDue is false for a missing or non-PAST_DUE status, true for PAST_DUE")
    void isPastDue_shouldMatchOnlyPastDue() {
        when(subscriptionRepository.findSubscriptionStatus(orgId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("ACTIVE"))
                .thenReturn(Optional.of("PAST_DUE"));

        assertThat(checker.isPastDue(orgId)).isFalse();
        assertThat(checker.isPastDue(orgId)).isFalse();
        assertThat(checker.isPastDue(orgId)).isTrue();
    }
}
