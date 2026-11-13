package com.opsclear.service;

import com.opsclear.aop.AddonCode;
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
@DisplayName("AddonAccessChecker (JOB-285)")
class AddonAccessCheckerTest {

    @Mock private OrgSubscriptionRepository subscriptionRepository;

    private AddonAccessChecker checker;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        checker = new AddonAccessChecker(subscriptionRepository);
        orgId = UUID.randomUUID();
    }

    private void givenPaidAddon(boolean billed, boolean addon) {
        when(subscriptionRepository.isInternal(orgId)).thenReturn(false);
        when(subscriptionRepository.hasRealBilling(orgId)).thenReturn(billed);
        if (billed) {
            when(subscriptionRepository.hasAddon(orgId, "APPROVALS")).thenReturn(addon);
        }
    }

    // --- isAddonUsableForWrites ---

    @Test
    @DisplayName("internal org can use every add-on for writes without billing or status being checked")
    void isAddonUsableForWrites_shouldReturnTrue_whenOrgInternal() {
        when(subscriptionRepository.isInternal(orgId)).thenReturn(true);

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isTrue();
        verify(subscriptionRepository, never()).hasRealBilling(any());
        verify(subscriptionRepository, never()).hasAddon(any(), anyString());
        verify(subscriptionRepository, never()).findSubscriptionStatus(any());
    }

    @Test
    @DisplayName("real billing with the add-on and an ACTIVE subscription is usable for writes")
    void isAddonUsableForWrites_shouldReturnTrue_whenBilledAddonAndActive() {
        givenPaidAddon(true, true);
        when(subscriptionRepository.findSubscriptionStatus(orgId)).thenReturn(Optional.of("ACTIVE"));

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isTrue();
    }

    @Test
    @DisplayName("real billing with the add-on but a PAST_DUE subscription is not usable for writes")
    void isAddonUsableForWrites_shouldReturnFalse_whenPastDue() {
        givenPaidAddon(true, true);
        when(subscriptionRepository.findSubscriptionStatus(orgId)).thenReturn(Optional.of("PAST_DUE"));

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isFalse();
    }

    @Test
    @DisplayName("real billing without the add-on is not usable, without the status being checked")
    void isAddonUsableForWrites_shouldReturnFalse_whenAddonMissing() {
        givenPaidAddon(true, false);

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isFalse();
        verify(subscriptionRepository, never()).findSubscriptionStatus(any());
    }

    @Test
    @DisplayName("a staged add-on without real billing is not usable")
    void isAddonUsableForWrites_shouldReturnFalse_whenNoRealBilling() {
        givenPaidAddon(false, false);

        assertThat(checker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).isFalse();
        verify(subscriptionRepository, never()).hasAddon(any(), anyString());
    }

    // --- building blocks ---

    @Test
    @DisplayName("isInternal delegates to the subscription repository")
    void isInternal_shouldDelegate() {
        when(subscriptionRepository.isInternal(orgId)).thenReturn(true);

        assertThat(checker.isInternal(orgId)).isTrue();
    }

    @Test
    @DisplayName("hasPaidAddon needs both real billing and the add-on")
    void hasPaidAddon_shouldNeedBillingAndAddon() {
        when(subscriptionRepository.hasRealBilling(orgId)).thenReturn(true);
        when(subscriptionRepository.hasAddon(orgId, "NOTES")).thenReturn(true);

        assertThat(checker.hasPaidAddon(orgId, AddonCode.NOTES)).isTrue();
    }

    @Test
    @DisplayName("isPastDue is false for a missing or non-PAST_DUE status")
    void isPastDue_shouldBeFalse_whenStatusMissingOrOther() {
        when(subscriptionRepository.findSubscriptionStatus(orgId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of("ACTIVE"));

        assertThat(checker.isPastDue(orgId)).isFalse();
        assertThat(checker.isPastDue(orgId)).isFalse();
    }
}
