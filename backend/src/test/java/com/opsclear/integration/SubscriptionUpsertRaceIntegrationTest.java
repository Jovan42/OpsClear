package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JOB-397: right after a Paddle checkout the frontend's PUT and Paddle's first
 * subscription webhook both try to create the org's org_subscriptions row. The PUT
 * reads "no row", the webhook commits its row, and the PUT must still succeed
 * without clobbering the webhook-synced Paddle fields.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Subscription upsert racing the first Paddle webhook")
class SubscriptionUpsertRaceIntegrationTest {

    private static final String PADDLE_SUBSCRIPTION_ID = "sub_race_01";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID orgId;
    private UUID webhookTierId;
    private UUID checkoutTierId;
    private UUID addonId;

    @BeforeEach
    void setUp() throws Exception {
        subscriptionRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Owner").build());

        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", "owner@example.com").claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Brightline", "slug", "BRL"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        orgId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        var tiers = tierRepository.findAll();
        webhookTierId = tiers.get(0).getId();
        checkoutTierId = tiers.get(1).getId();
        addonId = addonRepository.findAll().stream()
                .filter(SubscriptionAddonModel::isAvailable)
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    @DisplayName("upsertSubscription_shouldReturn200_andKeepWebhookFields_whenWebhookCreatesRowAfterTheRead")
    void upsertSubscription_shouldReturn200_andKeepWebhookFields_whenWebhookCreatesRowAfterTheRead()
            throws Exception {
        // The PUT's own findByOrgId sees no row; before it returns, the webhook
        // creates and commits the row in another thread (its own transaction).
        AtomicBoolean webhookFired = new AtomicBoolean(false);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (webhookFired.compareAndSet(false, true)) {
                CompletableFuture.runAsync(() -> subscriptionRepository.createFromPaddleWebhook(
                        orgId, webhookTierId, "MONTHLY", Set.of(), PADDLE_SUBSCRIPTION_ID, "ACTIVE",
                        null, Instant.parse("2026-11-19T18:52:39Z"))).join();
            }
            return result;
        }).when(subscriptionRepository).findByOrgId(eq(orgId));

        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", "owner@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tierId", checkoutTierId,
                                "billingCycle", "ANNUAL",
                                "addonIds", List.of(addonId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tier.id").value(checkoutTierId.toString()))
                .andExpect(jsonPath("$.billingCycle").value("ANNUAL"))
                .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"));

        assertThat(webhookFired).isTrue();
        OrgSubscriptionModel saved = subscriptionRepository.findByOrgId(orgId).orElseThrow();
        assertThat(saved.getTierId()).isEqualTo(checkoutTierId);
        assertThat(saved.getBillingCycle()).isEqualTo("ANNUAL");
        assertThat(saved.getAddonIds()).containsExactly(addonId);
        assertThat(saved.getPaddleSubscriptionId()).isEqualTo(PADDLE_SUBSCRIPTION_ID);
        assertThat(saved.getSubscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(saved.getPaddleCurrentPeriodStartsAt()).isEqualTo(Instant.parse("2026-11-19T18:52:39Z"));
    }
}
