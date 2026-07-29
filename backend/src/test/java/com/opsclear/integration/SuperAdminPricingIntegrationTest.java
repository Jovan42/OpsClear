package com.opsclear.integration;

import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Super Admin Pricing API")
class SuperAdminPricingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DSLContext dsl;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;

    private UUID superUserId;
    private UUID regularUserId;
    private UUID tierId;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        superUserId = UUID.randomUUID();
        regularUserId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(superUserId).email("super@example.com").name("Super").build());
        userRepository.save(UserModel.builder().id(regularUserId).email("regular@example.com").name("Regular").build());
        dsl.update(USERS).set(USERS.SUPER_USER, true).where(USERS.ID.eq(superUserId)).execute();

        tierId = tierRepository.findAll().getFirst().getId();
    }

    // --- GET /api/super-admin/pricing/tiers ---

    @Test
    @DisplayName("listTiers — super user receives 200 with all tiers")
    void listTiers_shouldReturn200_forSuperUser() throws Exception {
        mockMvc.perform(get(ApiPaths.SUPER_ADMIN_PRICING_TIERS)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(35));
    }

    @Test
    @DisplayName("listTiers — regular user receives 403")
    void listTiers_shouldReturn403_forRegularUser() throws Exception {
        mockMvc.perform(get(ApiPaths.SUPER_ADMIN_PRICING_TIERS)
                        .with(jwt().jwt(j -> j.subject(regularUserId.toString()).claim("email", "regular@example.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Super admin access required"));
    }

    @Test
    @DisplayName("listTiers — unauthenticated request receives 401")
    void listTiers_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get(ApiPaths.SUPER_ADMIN_PRICING_TIERS))
                .andExpect(status().isUnauthorized());
    }

    // --- PUT /api/super-admin/pricing/tiers/{tierId} ---

    @Test
    @DisplayName("updateTierPrice — super user can update a tier's price")
    void updateTierPrice_shouldReturn200_forSuperUser() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingTier(tierId))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 3900, "priceAnnual": 3250}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceMonthly").value(3900))
                .andExpect(jsonPath("$.priceAnnual").value(3250));

        SubscriptionTierModel updated = tierRepository.findById(tierId).orElseThrow();
        assertThat(updated.getPriceMonthly()).isEqualTo(3900);
    }

    @Test
    @DisplayName("updateTierPrice — regular user receives 403")
    void updateTierPrice_shouldReturn403_forRegularUser() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingTier(tierId))
                        .with(jwt().jwt(j -> j.subject(regularUserId.toString()).claim("email", "regular@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 3900, "priceAnnual": 3250}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateTierPrice — unknown tier returns 404")
    void updateTierPrice_shouldReturn404_whenTierNotFound() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingTier(UUID.randomUUID()))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 3900, "priceAnnual": 3250}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updateTierPrice — negative price returns 400")
    void updateTierPrice_shouldReturn400_whenPriceNegative() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingTier(tierId))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": -1, "priceAnnual": 3250}
                                """))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/super-admin/pricing/addons ---

    @Test
    @DisplayName("listAddons — super user receives 200 with all addons")
    void listAddons_shouldReturn200_forSuperUser() throws Exception {
        mockMvc.perform(get(ApiPaths.SUPER_ADMIN_PRICING_ADDONS)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(11));
    }

    @Test
    @DisplayName("listAddons — regular user receives 403")
    void listAddons_shouldReturn403_forRegularUser() throws Exception {
        mockMvc.perform(get(ApiPaths.SUPER_ADMIN_PRICING_ADDONS)
                        .with(jwt().jwt(j -> j.subject(regularUserId.toString()).claim("email", "regular@example.com"))))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/super-admin/pricing/addons/{addonKey} ---

    @Test
    @DisplayName("updateAddonPrice — super user can update an addon's price")
    void updateAddonPrice_shouldReturn200_forSuperUser() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingAddon("DASHBOARD"))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 1490, "priceAnnual": 1242}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("DASHBOARD"))
                .andExpect(jsonPath("$.priceMonthly").value(1490))
                .andExpect(jsonPath("$.priceAnnual").value(1242));

        SubscriptionAddonModel updated = addonRepository.findByKey("DASHBOARD").orElseThrow();
        assertThat(updated.getPriceMonthly()).isEqualTo(1490);
    }

    @Test
    @DisplayName("updateAddonPrice — regular user receives 403")
    void updateAddonPrice_shouldReturn403_forRegularUser() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingAddon("DASHBOARD"))
                        .with(jwt().jwt(j -> j.subject(regularUserId.toString()).claim("email", "regular@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 1490, "priceAnnual": 1242}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updateAddonPrice — unknown addon key returns 404")
    void updateAddonPrice_shouldReturn404_whenAddonNotFound() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingAddon("NONEXISTENT"))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 1490, "priceAnnual": 1242}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("updateAddonPrice — negative price returns 400")
    void updateAddonPrice_shouldReturn400_whenPriceNegative() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingAddon("DASHBOARD"))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 1490, "priceAnnual": -1}
                                """))
                .andExpect(status().isBadRequest());
    }
}
