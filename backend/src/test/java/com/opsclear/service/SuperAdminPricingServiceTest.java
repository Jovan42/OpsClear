package com.opsclear.service;

import com.opsclear.dto.UpdateAddonPriceRequest;
import com.opsclear.dto.UpdateTierPriceRequest;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuperAdminPricingService")
class SuperAdminPricingServiceTest {

    @Mock private SubscriptionTierRepository tierRepository;
    @Mock private SubscriptionAddonRepository addonRepository;

    private SuperAdminPricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new SuperAdminPricingService(tierRepository, addonRepository);
    }

    // --- listTiers ---

    @Test
    @DisplayName("listTiers returns all tiers from the repository")
    void listTiers_shouldReturnAllTiers() {
        List<SubscriptionTierModel> tiers = List.of(
                SubscriptionTierModel.builder().id(UUID.randomUUID()).maxMembers(5).priceMonthly(2900).build());
        when(tierRepository.findAll()).thenReturn(tiers);

        List<SubscriptionTierModel> result = pricingService.listTiers();

        assertThat(result).isEqualTo(tiers);
    }

    // --- updateTierPrice ---

    @Test
    @DisplayName("updateTierPrice updates and returns the tier")
    void updateTierPrice_shouldUpdateAndReturnTier() {
        UUID tierId = UUID.randomUUID();
        SubscriptionTierModel existing = SubscriptionTierModel.builder().id(tierId).maxMembers(5).priceMonthly(2900).build();
        SubscriptionTierModel updated = SubscriptionTierModel.builder().id(tierId).maxMembers(5).priceMonthly(3900).priceAnnual(3250).build();
        UpdateTierPriceRequest request = new UpdateTierPriceRequest();
        request.setPriceMonthly(3900);
        request.setPriceAnnual(3250);

        when(tierRepository.findById(tierId)).thenReturn(Optional.of(existing));
        when(tierRepository.updatePrice(tierId, 3900, 3250)).thenReturn(updated);

        SubscriptionTierModel result = pricingService.updateTierPrice(tierId, request);

        assertThat(result.getPriceMonthly()).isEqualTo(3900);
        assertThat(result.getPriceAnnual()).isEqualTo(3250);
        verify(tierRepository).updatePrice(tierId, 3900, 3250);
    }

    @Test
    @DisplayName("updateTierPrice throws NotFoundException when tier does not exist")
    void updateTierPrice_shouldThrow_whenTierNotFound() {
        UUID tierId = UUID.randomUUID();
        UpdateTierPriceRequest request = new UpdateTierPriceRequest();
        request.setPriceMonthly(3900);
        request.setPriceAnnual(3250);

        when(tierRepository.findById(tierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.updateTierPrice(tierId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Subscription tier not found");
    }

    // --- listAddons ---

    @Test
    @DisplayName("listAddons returns all addons from the repository")
    void listAddons_shouldReturnAllAddons() {
        List<SubscriptionAddonModel> addons = List.of(
                SubscriptionAddonModel.builder().id(UUID.randomUUID()).key("DASHBOARD").name("Dashboard").build());
        when(addonRepository.findAll()).thenReturn(addons);

        List<SubscriptionAddonModel> result = pricingService.listAddons();

        assertThat(result).isEqualTo(addons);
    }

    // --- updateAddonPrice ---

    @Test
    @DisplayName("updateAddonPrice updates and returns the addon")
    void updateAddonPrice_shouldUpdateAndReturnAddon() {
        SubscriptionAddonModel existing = SubscriptionAddonModel.builder()
                .id(UUID.randomUUID()).key("DASHBOARD").name("Dashboard").priceMonthly(990).build();
        SubscriptionAddonModel updated = SubscriptionAddonModel.builder()
                .id(UUID.randomUUID()).key("DASHBOARD").name("Dashboard").priceMonthly(1490).priceAnnual(1242).build();
        UpdateAddonPriceRequest request = new UpdateAddonPriceRequest();
        request.setPriceMonthly(1490);
        request.setPriceAnnual(1242);

        when(addonRepository.findByKey("DASHBOARD")).thenReturn(Optional.of(existing));
        when(addonRepository.updatePrice("DASHBOARD", 1490, 1242)).thenReturn(updated);

        SubscriptionAddonModel result = pricingService.updateAddonPrice("DASHBOARD", request);

        assertThat(result.getPriceMonthly()).isEqualTo(1490);
        assertThat(result.getPriceAnnual()).isEqualTo(1242);
        verify(addonRepository).updatePrice("DASHBOARD", 1490, 1242);
    }

    @Test
    @DisplayName("updateAddonPrice throws NotFoundException when addon does not exist")
    void updateAddonPrice_shouldThrow_whenAddonNotFound() {
        UpdateAddonPriceRequest request = new UpdateAddonPriceRequest();
        request.setPriceMonthly(1490);
        request.setPriceAnnual(1242);

        when(addonRepository.findByKey("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.updateAddonPrice("UNKNOWN", request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Subscription addon not found");
    }
}
