package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.repository.OrgSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    @DisplayName("internal org has every add-on without billing being checked")
    void hasAddon_shouldReturnTrue_whenOrgInternal() {
        when(subscriptionRepository.isInternal(orgId)).thenReturn(true);

        assertThat(checker.hasAddon(orgId, AddonCode.APPROVALS)).isTrue();
        verify(subscriptionRepository, never()).hasRealBilling(any());
        verify(subscriptionRepository, never()).hasAddon(any(), anyString());
    }

    @Test
    @DisplayName("real billing with the add-on in the subscription passes")
    void hasAddon_shouldReturnTrue_whenBilledAndAddonPresent() {
        when(subscriptionRepository.isInternal(orgId)).thenReturn(false);
        when(subscriptionRepository.hasRealBilling(orgId)).thenReturn(true);
        when(subscriptionRepository.hasAddon(orgId, "APPROVALS")).thenReturn(true);

        assertThat(checker.hasAddon(orgId, AddonCode.APPROVALS)).isTrue();
    }

    @Test
    @DisplayName("real billing without the add-on fails")
    void hasAddon_shouldReturnFalse_whenAddonMissing() {
        when(subscriptionRepository.isInternal(orgId)).thenReturn(false);
        when(subscriptionRepository.hasRealBilling(orgId)).thenReturn(true);
        when(subscriptionRepository.hasAddon(orgId, "APPROVALS")).thenReturn(false);

        assertThat(checker.hasAddon(orgId, AddonCode.APPROVALS)).isFalse();
    }

    @Test
    @DisplayName("a staged add-on without real billing fails")
    void hasAddon_shouldReturnFalse_whenNoRealBilling() {
        when(subscriptionRepository.isInternal(orgId)).thenReturn(false);
        when(subscriptionRepository.hasRealBilling(orgId)).thenReturn(false);

        assertThat(checker.hasAddon(orgId, AddonCode.APPROVALS)).isFalse();
        verify(subscriptionRepository, never()).hasAddon(any(), anyString());
    }
}
