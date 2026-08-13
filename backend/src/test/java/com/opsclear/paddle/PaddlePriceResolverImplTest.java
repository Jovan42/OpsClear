package com.opsclear.paddle;

import com.opsclear.exception.ConflictException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaddlePriceResolverImpl")
class PaddlePriceResolverImplTest {

    @Mock private SubscriptionTierRepository tierRepository;
    @Mock private SubscriptionAddonRepository addonRepository;

    private PaddlePriceResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new PaddlePriceResolverImpl(tierRepository, addonRepository);
    }

    // --- resolveTierPriceId ---

    @Test
    @DisplayName("resolveTierPriceId returns the monthly price id for MONTHLY billing cycle")
    void resolveTierPriceId_shouldReturnMonthlyPriceId_forMonthlyCycle() {
        UUID tierId = UUID.randomUUID();
        SubscriptionTierModel tier = SubscriptionTierModel.builder()
                .id(tierId).paddlePriceIdMonthly("pri_m").paddlePriceIdAnnual("pri_a").build();
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        String result = resolver.resolveTierPriceId(tierId, "MONTHLY");

        assertThat(result).isEqualTo("pri_m");
    }

    @Test
    @DisplayName("resolveTierPriceId returns the annual price id for ANNUAL billing cycle")
    void resolveTierPriceId_shouldReturnAnnualPriceId_forAnnualCycle() {
        UUID tierId = UUID.randomUUID();
        SubscriptionTierModel tier = SubscriptionTierModel.builder()
                .id(tierId).paddlePriceIdMonthly("pri_m").paddlePriceIdAnnual("pri_a").build();
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        String result = resolver.resolveTierPriceId(tierId, "ANNUAL");

        assertThat(result).isEqualTo("pri_a");
    }

    @Test
    @DisplayName("resolveTierPriceId throws NotFoundException when the tier does not exist")
    void resolveTierPriceId_shouldThrowNotFound_whenTierMissing() {
        UUID tierId = UUID.randomUUID();
        when(tierRepository.findById(tierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveTierPriceId(tierId, "MONTHLY"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("resolveTierPriceId throws ConflictException when the tier has not been synced to Paddle yet")
    void resolveTierPriceId_shouldThrowConflict_whenNotSyncedYet() {
        UUID tierId = UUID.randomUUID();
        SubscriptionTierModel tier = SubscriptionTierModel.builder().id(tierId).build();
        when(tierRepository.findById(tierId)).thenReturn(Optional.of(tier));

        assertThatThrownBy(() -> resolver.resolveTierPriceId(tierId, "MONTHLY"))
                .isInstanceOf(ConflictException.class);
    }

    // --- resolveAddonPriceId ---

    @Test
    @DisplayName("resolveAddonPriceId returns the monthly price id for MONTHLY billing cycle")
    void resolveAddonPriceId_shouldReturnMonthlyPriceId_forMonthlyCycle() {
        UUID addonId = UUID.randomUUID();
        SubscriptionAddonModel addon = SubscriptionAddonModel.builder()
                .id(addonId).paddlePriceIdMonthly("pri_m").paddlePriceIdAnnual("pri_a").build();
        when(addonRepository.findById(addonId)).thenReturn(Optional.of(addon));

        String result = resolver.resolveAddonPriceId(addonId, "MONTHLY");

        assertThat(result).isEqualTo("pri_m");
    }

    @Test
    @DisplayName("resolveAddonPriceId returns the annual price id for ANNUAL billing cycle")
    void resolveAddonPriceId_shouldReturnAnnualPriceId_forAnnualCycle() {
        UUID addonId = UUID.randomUUID();
        SubscriptionAddonModel addon = SubscriptionAddonModel.builder()
                .id(addonId).paddlePriceIdMonthly("pri_m").paddlePriceIdAnnual("pri_a").build();
        when(addonRepository.findById(addonId)).thenReturn(Optional.of(addon));

        String result = resolver.resolveAddonPriceId(addonId, "ANNUAL");

        assertThat(result).isEqualTo("pri_a");
    }

    @Test
    @DisplayName("resolveAddonPriceId throws NotFoundException when the addon does not exist")
    void resolveAddonPriceId_shouldThrowNotFound_whenAddonMissing() {
        UUID addonId = UUID.randomUUID();
        when(addonRepository.findById(addonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveAddonPriceId(addonId, "MONTHLY"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("resolveAddonPriceId throws ConflictException when the addon has not been synced to Paddle yet")
    void resolveAddonPriceId_shouldThrowConflict_whenNotSyncedYet() {
        UUID addonId = UUID.randomUUID();
        SubscriptionAddonModel addon = SubscriptionAddonModel.builder().id(addonId).build();
        when(addonRepository.findById(addonId)).thenReturn(Optional.of(addon));

        assertThatThrownBy(() -> resolver.resolveAddonPriceId(addonId, "MONTHLY"))
                .isInstanceOf(ConflictException.class);
    }
}
