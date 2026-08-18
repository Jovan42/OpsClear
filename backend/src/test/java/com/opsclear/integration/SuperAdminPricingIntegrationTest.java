package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import com.opsclear.model.UserModel;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddlePrice;
import com.opsclear.paddle.PaddleProduct;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mocks {@link PaddleClient} (JOB-180) — this class used to hit Paddle's real sandbox
 * on every price update and especially on {@code syncCatalog} (up to ~46 products +
 * ~92 prices in one test run, since it syncs every unsynced tier/addon), which was a
 * major source of Cloudflare rate-limit failures when the suite ran repeatedly.
 * {@code createProduct}/{@code createPrice} return a fresh unique id per call so the
 * "second update replaces the price but reuses the product" assertions still hold
 * (the service itself only calls {@code createProduct} once a tier already has one).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Super Admin Pricing API")
class SuperAdminPricingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private UserRepository userRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @MockitoBean private PaddleClient paddleClient;

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

        when(paddleClient.createProduct(any()))
                .thenAnswer(inv -> new PaddleProduct("pro_" + UUID.randomUUID(), inv.getArgument(0)));
        when(paddleClient.createPrice(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new PaddlePrice("pri_" + UUID.randomUUID(), "active"));
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

    // --- Paddle price sync (JOB-176) ---

    @Test
    @DisplayName("updateTierPrice — syncs a real Paddle Product and Prices to the sandbox on first update")
    void updateTierPrice_shouldSyncRealPaddlePrices_onFirstUpdate() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingTier(tierId))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 44, "priceAnnual": 37}
                                """))
                .andExpect(status().isOk());

        SubscriptionTierModel synced = tierRepository.findById(tierId).orElseThrow();
        assertThat(synced.getPaddleProductId()).startsWith("pro_");
        assertThat(synced.getPaddlePriceIdMonthly()).startsWith("pri_");
        assertThat(synced.getPaddlePriceIdAnnual()).startsWith("pri_");
    }

    @Test
    @DisplayName("updateTierPrice — a second update reuses the Product and replaces the Prices (archive-and-recreate)")
    void updateTierPrice_shouldReplacePrices_onSecondUpdate() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingTier(tierId))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 44, "priceAnnual": 37}
                                """))
                .andExpect(status().isOk());
        SubscriptionTierModel firstSync = tierRepository.findById(tierId).orElseThrow();

        mockMvc.perform(put(ApiPaths.superAdminPricingTier(tierId))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 64, "priceAnnual": 53}
                                """))
                .andExpect(status().isOk());
        SubscriptionTierModel secondSync = tierRepository.findById(tierId).orElseThrow();

        assertThat(secondSync.getPaddleProductId()).isEqualTo(firstSync.getPaddleProductId());
        assertThat(secondSync.getPaddlePriceIdMonthly()).isNotEqualTo(firstSync.getPaddlePriceIdMonthly());
        assertThat(secondSync.getPaddlePriceIdAnnual()).isNotEqualTo(firstSync.getPaddlePriceIdAnnual());
        // The old monthly + annual prices from the first sync must be archived, not left dangling.
        verify(paddleClient).archivePrice(firstSync.getPaddlePriceIdMonthly());
        verify(paddleClient).archivePrice(firstSync.getPaddlePriceIdAnnual());
    }

    @Test
    @DisplayName("updateAddonPrice — syncs a real Paddle Product and Prices to the sandbox on first update")
    void updateAddonPrice_shouldSyncRealPaddlePrices_onFirstUpdate() throws Exception {
        mockMvc.perform(put(ApiPaths.superAdminPricingAddon("NOTES"))
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priceMonthly": 9, "priceAnnual": 8}
                                """))
                .andExpect(status().isOk());

        SubscriptionAddonModel synced = addonRepository.findByKey("NOTES").orElseThrow();
        assertThat(synced.getPaddleProductId()).startsWith("pro_");
        assertThat(synced.getPaddlePriceIdMonthly()).startsWith("pri_");
        assertThat(synced.getPaddlePriceIdAnnual()).startsWith("pri_");
    }

    // --- POST /api/super-admin/pricing/sync ---

    @Test
    @DisplayName("syncCatalog — regular user receives 403")
    void syncCatalog_shouldReturn403_forRegularUser() throws Exception {
        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_PRICING_SYNC)
                        .with(jwt().jwt(j -> j.subject(regularUserId.toString()).claim("email", "regular@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("syncCatalog — second call is a no-op: nothing left unsynced, and a fresh row's Paddle ids don't change")
    void syncCatalog_shouldBeIdempotent_onSecondCall() throws Exception {
        UUID untouchedTierId = tierRepository.findAll().stream()
                .filter(t -> t.getMaxMembers() == 50 && t.getMaxProjects() == null)
                .findFirst().orElseThrow().getId();

        mockMvc.perform(post(ApiPaths.SUPER_ADMIN_PRICING_SYNC)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com"))))
                .andExpect(status().isOk());
        SubscriptionTierModel afterFirstSync = tierRepository.findById(untouchedTierId).orElseThrow();
        assertThat(afterFirstSync.getPaddleProductId()).isNotNull();

        String response = mockMvc.perform(post(ApiPaths.SUPER_ADMIN_PRICING_SYNC)
                        .with(jwt().jwt(j -> j.subject(superUserId.toString()).claim("email", "super@example.com"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(response).get("tiersSynced").asInt()).isZero();
        assertThat(objectMapper.readTree(response).get("addonsSynced").asInt()).isZero();

        SubscriptionTierModel afterSecondSync = tierRepository.findById(untouchedTierId).orElseThrow();
        assertThat(afterSecondSync.getPaddleProductId()).isEqualTo(afterFirstSync.getPaddleProductId());
        assertThat(afterSecondSync.getPaddlePriceIdMonthly()).isEqualTo(afterFirstSync.getPaddlePriceIdMonthly());
    }
}
