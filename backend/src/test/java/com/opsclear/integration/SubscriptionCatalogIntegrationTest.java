package com.opsclear.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("GET /api/subscriptions/catalog")
class SubscriptionCatalogIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("getCatalog_shouldReturn200WithoutAuthentication")
    void getCatalog_shouldReturn200WithoutAuthentication() throws Exception {
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getCatalog_shouldReturn35Tiers")
    void getCatalog_shouldReturn35Tiers() throws Exception {
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers.length()").value(35));
    }

    @Test
    @DisplayName("getCatalog_shouldReturn11Addons")
    void getCatalog_shouldReturn11Addons() throws Exception {
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addons.length()").value(11));
    }

    @Test
    @DisplayName("getCatalog_shouldReturnTiersWithExpectedFields")
    void getCatalog_shouldReturnTiersWithExpectedFields() throws Exception {
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers[0].id").isNotEmpty())
                .andExpect(jsonPath("$.tiers[0].maxMembers").value(5))
                .andExpect(jsonPath("$.tiers[0].maxProjects").value(3))
                .andExpect(jsonPath("$.tiers[0].priceMonthly").value(2900))
                .andExpect(jsonPath("$.tiers[0].priceAnnual").value(2417))
                .andExpect(jsonPath("$.tiers[0].currency").value("RSD"))
                .andExpect(jsonPath("$.tiers[0].displayOrder").value(1));
    }

    @Test
    @DisplayName("getCatalog_shouldReturnAddonsWithExpectedFields")
    void getCatalog_shouldReturnAddonsWithExpectedFields() throws Exception {
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addons[0].id").isNotEmpty())
                .andExpect(jsonPath("$.addons[0].key").value("DASHBOARD"))
                .andExpect(jsonPath("$.addons[0].name").value("Dashboard"))
                .andExpect(jsonPath("$.addons[0].priceMonthly").value(990))
                .andExpect(jsonPath("$.addons[0].priceAnnual").value(825))
                .andExpect(jsonPath("$.addons[0].available").value(true))
                .andExpect(jsonPath("$.addons[0].displayOrder").value(1));
    }

    @Test
    @DisplayName("getCatalog_shouldReturnJobTemplatesAsAvailable")
    void getCatalog_shouldReturnJobTemplatesAsAvailable() throws Exception {
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addons[7].key").value("JOB_TEMPLATES"))
                .andExpect(jsonPath("$.addons[7].available").value(true));
    }

    @Test
    @DisplayName("getCatalog_shouldReturnRecurringSchedulingAsAvailable")
    void getCatalog_shouldReturnRecurringSchedulingAsAvailable() throws Exception {
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addons[8].key").value("RECURRING_SCHEDULING"))
                .andExpect(jsonPath("$.addons[8].available").value(true));
    }

    @Test
    @DisplayName("getCatalog_shouldReturnJobLinksAsAvailable")
    void getCatalog_shouldReturnJobLinksAsAvailable() throws Exception {
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addons[9].key").value("JOB_LINKS"))
                .andExpect(jsonPath("$.addons[9].available").value(true));
    }

    @Test
    @DisplayName("getCatalog_shouldReturnJobTypesAsComingSoon")
    void getCatalog_shouldReturnJobTypesAsComingSoon() throws Exception {
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addons[10].key").value("JOB_TYPES"))
                .andExpect(jsonPath("$.addons[10].available").value(false));
    }

    @Test
    @DisplayName("getCatalog_shouldReturnNullMaxProjectsForUnlimitedTier")
    void getCatalog_shouldReturnNullMaxProjectsForUnlimitedTier() throws Exception {
        // Tier at display_order 5 is (5 members, unlimited projects)
        mockMvc.perform(get(ApiPaths.SUBSCRIPTION_CATALOG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers[4].maxMembers").value(5))
                .andExpect(jsonPath("$.tiers[4].maxProjects").doesNotExist());
    }
}
