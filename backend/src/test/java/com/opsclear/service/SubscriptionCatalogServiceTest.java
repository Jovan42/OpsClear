package com.opsclear.service;

import com.opsclear.dto.CatalogResponse;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionCatalogService")
class SubscriptionCatalogServiceTest {

    @Mock private SubscriptionTierRepository tierRepository;
    @Mock private SubscriptionAddonRepository addonRepository;

    private SubscriptionCatalogService service;

    private SubscriptionTierModel tier;
    private SubscriptionAddonModel availableAddon;
    private SubscriptionAddonModel comingSoonAddon;

    @BeforeEach
    void setUp() {
        service = new SubscriptionCatalogService(tierRepository, addonRepository);

        tier = SubscriptionTierModel.builder()
                .id(UUID.randomUUID())
                .maxMembers(5)
                .maxProjects(3)
                .priceMonthly(2900)
                .priceAnnual(2417)
                .currency("RSD")
                .displayOrder(1)
                .build();

        availableAddon = SubscriptionAddonModel.builder()
                .id(UUID.randomUUID())
                .key("DASHBOARD")
                .name("Dashboard")
                .priceMonthly(990)
                .priceAnnual(825)
                .available(true)
                .displayOrder(1)
                .build();

        comingSoonAddon = SubscriptionAddonModel.builder()
                .id(UUID.randomUUID())
                .key("JOB_TEMPLATES")
                .name("Job templates")
                .priceMonthly(990)
                .priceAnnual(825)
                .available(false)
                .displayOrder(8)
                .build();
    }

    @Test
    @DisplayName("getCatalog_shouldReturnAllTiersAndAddons")
    void getCatalog_shouldReturnAllTiersAndAddons() {
        when(tierRepository.findAll()).thenReturn(List.of(tier));
        when(addonRepository.findAll()).thenReturn(List.of(availableAddon, comingSoonAddon));

        CatalogResponse response = service.getCatalog();

        assertThat(response.getTiers()).hasSize(1);
        assertThat(response.getAddons()).hasSize(2);
    }

    @Test
    @DisplayName("getCatalog_shouldMapTierFieldsCorrectly")
    void getCatalog_shouldMapTierFieldsCorrectly() {
        when(tierRepository.findAll()).thenReturn(List.of(tier));
        when(addonRepository.findAll()).thenReturn(List.of());

        CatalogResponse response = service.getCatalog();

        var mapped = response.getTiers().get(0);
        assertThat(mapped.getId()).isEqualTo(tier.getId());
        assertThat(mapped.getMaxMembers()).isEqualTo(5);
        assertThat(mapped.getMaxProjects()).isEqualTo(3);
        assertThat(mapped.getPriceMonthly()).isEqualTo(2900);
        assertThat(mapped.getPriceAnnual()).isEqualTo(2417);
        assertThat(mapped.getCurrency()).isEqualTo("RSD");
        assertThat(mapped.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("getCatalog_shouldMapAddonFieldsCorrectly")
    void getCatalog_shouldMapAddonFieldsCorrectly() {
        when(tierRepository.findAll()).thenReturn(List.of());
        when(addonRepository.findAll()).thenReturn(List.of(availableAddon));

        CatalogResponse response = service.getCatalog();

        var mapped = response.getAddons().get(0);
        assertThat(mapped.getId()).isEqualTo(availableAddon.getId());
        assertThat(mapped.getKey()).isEqualTo("DASHBOARD");
        assertThat(mapped.getName()).isEqualTo("Dashboard");
        assertThat(mapped.getPriceMonthly()).isEqualTo(990);
        assertThat(mapped.getPriceAnnual()).isEqualTo(825);
        assertThat(mapped.isAvailable()).isTrue();
        assertThat(mapped.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("getCatalog_shouldIncludeComingSoonAddons")
    void getCatalog_shouldIncludeComingSoonAddons() {
        when(tierRepository.findAll()).thenReturn(List.of());
        when(addonRepository.findAll()).thenReturn(List.of(availableAddon, comingSoonAddon));

        CatalogResponse response = service.getCatalog();

        assertThat(response.getAddons())
                .extracting("available")
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("getCatalog_shouldReturnEmptyListsWhenNoData")
    void getCatalog_shouldReturnEmptyListsWhenNoData() {
        when(tierRepository.findAll()).thenReturn(List.of());
        when(addonRepository.findAll()).thenReturn(List.of());

        CatalogResponse response = service.getCatalog();

        assertThat(response.getTiers()).isEmpty();
        assertThat(response.getAddons()).isEmpty();
    }

    @Test
    @DisplayName("getCatalog_shouldHandleUnlimitedProjects_whenMaxProjectsIsNull")
    void getCatalog_shouldHandleUnlimitedProjects_whenMaxProjectsIsNull() {
        SubscriptionTierModel unlimitedTier = SubscriptionTierModel.builder()
                .id(UUID.randomUUID())
                .maxMembers(50)
                .maxProjects(null)
                .priceMonthly(27900)
                .priceAnnual(23250)
                .currency("RSD")
                .displayOrder(35)
                .build();
        when(tierRepository.findAll()).thenReturn(List.of(unlimitedTier));
        when(addonRepository.findAll()).thenReturn(List.of());

        CatalogResponse response = service.getCatalog();

        assertThat(response.getTiers().get(0).getMaxProjects()).isNull();
    }
}
