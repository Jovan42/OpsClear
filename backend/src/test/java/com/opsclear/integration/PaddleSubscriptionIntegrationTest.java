package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.UserModel;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleCustomer;
import com.opsclear.paddle.PaddlePreviewImmediateTransaction;
import com.opsclear.paddle.PaddlePreviewTransactionDetails;
import com.opsclear.paddle.PaddlePreviewTotals;
import com.opsclear.paddle.PaddlePrice;
import com.opsclear.paddle.PaddleProduct;
import com.opsclear.paddle.PaddleSubscriptionPreview;
import com.opsclear.paddle.PaddleTransaction;
import com.opsclear.paddle.PaddleTransactionDetails;
import com.opsclear.paddle.PaddleTransactionTotals;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.PaddleSubscriptionService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTIONS;
import static com.opsclear.generated.jooq.Tables.SUBSCRIPTION_TIERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mocks {@link PaddleClient} (JOB-180) — previously ran against Paddle's real sandbox
 * API, which became a major source of Cloudflare rate-limit failures (this file alone
 * makes several Paddle calls per test across ~20 of its ~39 tests) when run repeatedly.
 *
 * <p>The update-items endpoint's actual successful round-trip to Paddle's
 * {@code PATCH /subscriptions/{id}} still can't be exercised end-to-end here even with
 * a real API: Paddle does not support creating a Subscription via API at all (only
 * real checkout completion creates one, JOB-178), so there was never a real
 * subscription id sandbox-side to PATCH against — the "reaches Paddle instead of
 * failing at our own resolver/guard" tests below always relied on Paddle rejecting a
 * fake placeholder id, which a mocked {@code thenThrow} now simulates directly instead.
 * That successful-response path is covered at the unit level in
 * {@code PaddleSubscriptionServiceTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Paddle subscription endpoints")
class PaddleSubscriptionIntegrationTest {

    private static final OffsetDateTime SCHEDULED_CANCELLATION_FIXTURE = OffsetDateTime.parse("2024-10-12T07:20:50.52Z");
    private static final String SIMULATED_PADDLE_REJECTION =
            "simulated - Paddle would reject a fake subscription id";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaddleSubscriptionService paddleSubscriptionService;
    @MockitoBean private PaddleClient paddleClient;

    private UUID ownerId;
    private UUID memberId;
    private UUID orgId;
    private UUID tierId;
    private String ownerEmail;

    @BeforeEach
    void setUp() throws Exception {
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";

        userRepository.save(UserModel.builder().id(ownerId).email(ownerEmail).name("Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Member").build());

        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", ownerEmail).claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        orgId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(post(ApiPaths.orgMembers(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", memberId, "role", "MEMBER"))))
                .andExpect(status().isCreated());

        tierId = tierRepository.findAll().getFirst().getId();

        when(paddleClient.createCustomer(any(), any()))
                .thenAnswer(inv -> new PaddleCustomer("ctm_" + UUID.randomUUID(), inv.getArgument(0)));
        when(paddleClient.createProduct(any()))
                .thenAnswer(inv -> new PaddleProduct("pro_" + UUID.randomUUID(), inv.getArgument(0)));
        when(paddleClient.createPrice(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new PaddlePrice("pri_" + UUID.randomUUID(), "active"));
        // sub_test_placeholder (see givenOrgHasFakePaddleSubscriptionId) was never a
        // real Paddle subscription — these all simulate Paddle rejecting it, matching
        // real observed sandbox behavior for the "reachesPaddleApi" tests below.
        when(paddleClient.updateSubscriptionItems(any(), any(), any()))
                .thenThrow(new RestClientException(SIMULATED_PADDLE_REJECTION));
        when(paddleClient.previewUpdateSubscriptionItems(any(), any(), any()))
                .thenThrow(new RestClientException(SIMULATED_PADDLE_REJECTION));
        when(paddleClient.cancelSubscription(any()))
                .thenThrow(new RestClientException(SIMULATED_PADDLE_REJECTION));
        when(paddleClient.removeScheduledCancellation(any()))
                .thenThrow(new RestClientException(SIMULATED_PADDLE_REJECTION));
        when(paddleClient.getUpdatePaymentMethodTransaction(any()))
                .thenThrow(new RestClientException(SIMULATED_PADDLE_REJECTION));
    }

    private void givenOrgHasSubscriptionRecord() throws Exception {
        givenOrgHasSubscriptionRecord("MONTHLY");
    }

    private void givenOrgHasSubscriptionRecord(String billingCycle) throws Exception {
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("tierId", tierId, "billingCycle", billingCycle, "addonIds", List.of()))))
                .andExpect(status().isOk());
    }

    private void givenOrgHasFakePaddleSubscriptionId() {
        dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.PADDLE_SUBSCRIPTION_ID, "sub_test_placeholder")
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .execute();
    }

    private void givenOrgIsInternal() {
        dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.IS_INTERNAL, true)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .execute();
    }

    // Paddle can't be driven to a real scheduled-cancellation state here without a
    // real subscription (checkout, JOB-178) — seeded directly, same pragmatic pattern
    // as givenOrgHasFakePaddleSubscriptionId.
    private void givenOrgHasScheduledCancellation() {
        dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.PADDLE_SCHEDULED_CANCELLATION_AT, SCHEDULED_CANCELLATION_FIXTURE)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .execute();
    }

    // Same pragmatic seeding pattern as givenOrgHasScheduledCancellation — a pending
    // downgrade is normally set by updateSubscriptionItems' own downgrade branch,
    // seeded here directly for tests that only care about the guard/undo behavior.
    private void givenOrgHasPendingDowngrade() {
        UUID pendingTierId = tierRepository.findAll().get(1).getId();
        subscriptionRepository.schedulePendingDowngrade(
                subscriptionRepository.findByOrgId(orgId).orElseThrow().getId(), orgId, pendingTierId, Set.of(),
                Instant.parse("2026-09-01T00:00:00Z"));
    }

    // subscription_tiers is global seed data shared across the whole test run (not
    // reset per test/class), so other suites (e.g. SuperAdminPricingIntegrationTest)
    // may have already synced this exact tier to Paddle by the time this class runs —
    // reset it explicitly rather than assume a fresh-unsynced starting state.
    private void givenTierIsNotSyncedToPaddle() {
        dsl.update(SUBSCRIPTION_TIERS)
                .setNull(SUBSCRIPTION_TIERS.PADDLE_PRODUCT_ID)
                .setNull(SUBSCRIPTION_TIERS.PADDLE_PRICE_ID_MONTHLY)
                .setNull(SUBSCRIPTION_TIERS.PADDLE_PRICE_ID_ANNUAL)
                .where(SUBSCRIPTION_TIERS.ID.eq(tierId))
                .execute();
    }

    // ─── POST /api/organisations/{orgId}/subscription/paddle ──────────────────

    @Test
    @DisplayName("initiate_shouldReturn201_andCreateRealPaddleCustomer_forOwner")
    void initiate_shouldReturn201_andCreateRealPaddleCustomer_forOwner() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(post(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orgId").value(orgId.toString()))
                .andExpect(jsonPath("$.paddleCustomerId").value(org.hamcrest.Matchers.startsWith("ctm_")));
    }

    @Test
    @DisplayName("initiate_shouldBeIdempotent_returningTheSameCustomerId_onASecondCall")
    void initiate_shouldBeIdempotent_returningTheSameCustomerId_onASecondCall() throws Exception {
        givenOrgHasSubscriptionRecord();

        String first = mockMvc.perform(post(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstCustomerId = objectMapper.readTree(first).get("paddleCustomerId").asText();

        mockMvc.perform(post(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paddleCustomerId").value(firstCustomerId));
    }

    @Test
    @DisplayName("initiate_shouldReturn400_forInternalOrg")
    void initiate_shouldReturn400_forInternalOrg() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgIsInternal();

        mockMvc.perform(post(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("initiate_shouldReturn403_forNonOwner")
    void initiate_shouldReturn403_forNonOwner() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(post(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("initiate_shouldReturn201_andCreateRealPaddleCustomer_evenWithNoSubscriptionRecordYet")
    void initiate_shouldReturn201_andCreateRealPaddleCustomer_evenWithNoSubscriptionRecordYet() throws Exception {
        // JOB-200: paddle_customer_id lives on organisations, not org_subscriptions —
        // every tier/add-on has a real price, so the org_subscriptions row is only
        // ever created once a real payment is webhook-confirmed. initiate() must
        // still work before that, while the org is still just picking a plan.
        mockMvc.perform(post(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orgId").value(orgId.toString()))
                .andExpect(jsonPath("$.paddleCustomerId").value(org.hamcrest.Matchers.startsWith("ctm_")));
    }

    // ─── PUT /api/organisations/{orgId}/subscription/paddle ────────────────────

    @Test
    @DisplayName("update_shouldReturn409_whenNoPaddleSubscriptionYet")
    void update_shouldReturn409_whenNoPaddleSubscriptionYet() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("update_shouldReturn409_whenTierHasNotBeenSyncedToPaddleYet")
    void update_shouldReturn409_whenTierHasNotBeenSyncedToPaddleYet() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        givenTierIsNotSyncedToPaddle();

        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This tier/add-on has not been synced to Paddle yet — update its price via "
                                + "the super admin console, or run the catalog sync, before it can be "
                                + "selected on a Paddle subscription"));
    }

    @Test
    @DisplayName("update_shouldReachPaddleApi_insteadOfFailingAtResolver_onceTierSyncedToPaddle")
    void update_shouldReachPaddleApi_insteadOfFailingAtResolver_onceTierSyncedToPaddle() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        paddleSubscriptionService.syncTierPriceToPaddle(tierRepository.findById(tierId).orElseThrow());

        // Resolving the tier's synced Paddle Price id succeeds, so the request passes
        // our own resolver and genuinely reaches PaddleClient.updateSubscriptionItems
        // — mocked to throw (setUp), surfaced as a generic 500 via
        // GlobalExceptionHandler's catch-all, simulating what Paddle's real sandbox
        // does for sub_test_placeholder (never a real subscription — Paddle
        // subscriptions can only be created via real checkout, JOB-178, never faked).
        // The important assertion is that this is no longer our own 409 conflict.
        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("update_shouldReachPaddleApi_insteadOfFailingAtResolver_onceAddonSyncedToPaddle")
    void update_shouldReachPaddleApi_insteadOfFailingAtResolver_onceAddonSyncedToPaddle() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        paddleSubscriptionService.syncTierPriceToPaddle(tierRepository.findById(tierId).orElseThrow());
        SubscriptionAddonModel addon = addonRepository.findAll().getFirst();
        paddleSubscriptionService.syncAddonPriceToPaddle(addon);

        // Same reasoning as the tier-only test above: resolving the addon's synced
        // Paddle Price id succeeds, so the request passes our own resolver and reaches
        // the mocked-to-throw PaddleClient.updateSubscriptionItems instead of failing
        // at our own resolver.
        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("tierId", tierId, "addonIds", List.of(addon.getId())))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("update_shouldReturn404_whenRequestedAddonDoesNotExist")
    void update_shouldReturn404_whenRequestedAddonDoesNotExist() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        paddleSubscriptionService.syncTierPriceToPaddle(tierRepository.findById(tierId).orElseThrow());

        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("tierId", tierId, "addonIds", List.of(UUID.randomUUID())))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("update_shouldReachPaddleApi_insteadOfFailingAtResolver_forAnAnnualSubscription")
    void update_shouldReachPaddleApi_insteadOfFailingAtResolver_forAnAnnualSubscription() throws Exception {
        givenOrgHasSubscriptionRecord("ANNUAL");
        givenOrgHasFakePaddleSubscriptionId();
        paddleSubscriptionService.syncTierPriceToPaddle(tierRepository.findById(tierId).orElseThrow());

        // Proves the resolver picks the tier's ANNUAL Paddle Price id (not just
        // MONTHLY, already covered above) — the request reaches the mocked-to-throw
        // PaddleClient.updateSubscriptionItems instead of failing at our own resolver.
        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("update_shouldReturn409_insteadOfPaddlesRaw400_whenADowngradeIsAttemptedWithACancellationAlreadyScheduled")
    void update_shouldReturn409_insteadOfPaddlesRaw400_whenADowngradeIsAttemptedWithACancellationAlreadyScheduled()
            throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        givenOrgHasScheduledCancellation();
        paddleSubscriptionService.syncTierPriceToPaddle(tierRepository.findById(tierId).orElseThrow());

        // Requesting the same tier (no price change) classifies as a downgrade —
        // real bug found via live manual testing: Paddle rejects a second
        // full_next_billing_period change with a raw 400
        // (subscription_invalid_billing_mode_for_scheduled_change) when the
        // subscription already has a scheduled cancellation pending. This must be
        // caught by our own guard before ever reaching Paddle.
        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("update_shouldReturn409_whenChangeIsMixed_addingAndRemovingAddonsInTheSameRequest")
    void update_shouldReturn409_whenChangeIsMixed_addingAndRemovingAddonsInTheSameRequest() throws Exception {
        List<SubscriptionAddonModel> addons = addonRepository.findAll();
        UUID existingAddonId = addons.get(0).getId();
        UUID newAddonId = addons.get(1).getId();

        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tierId", tierId, "billingCycle", "MONTHLY", "addonIds", List.of(existingAddonId)))))
                .andExpect(status().isOk());

        // Real bug found via live manual testing: adding one addon while removing
        // another in the same request nets out to an "upgrade" by total price, which
        // would apply immediately and yank the removed addon away right now even
        // though it's already paid for this period. Must be caught by our own guard
        // before ever reaching Paddle's price resolver.
        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("tierId", tierId, "addonIds", List.of(newAddonId)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This change both adds something more expensive and removes something cheaper — "
                                + "please save these as two separate changes"));
    }

    @Test
    @DisplayName("update_shouldReturn400_forInternalOrg")
    void update_shouldReturn400_forInternalOrg() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgIsInternal();

        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("update_shouldReturn403_forNonOwner")
    void update_shouldReturn403_forNonOwner() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("update_shouldReturn404_whenTierDoesNotExist")
    void update_shouldReturn404_whenTierDoesNotExist() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("update_shouldReturn400_whenTierIdMissing")
    void update_shouldReturn400_whenTierIdMissing() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ─── POST /api/organisations/{orgId}/subscription/paddle/preview ──────────

    @Test
    @DisplayName("preview_shouldReachPaddleApi_insteadOfFailingAtResolver_onceTierSyncedToPaddle")
    void preview_shouldReachPaddleApi_insteadOfFailingAtResolver_onceTierSyncedToPaddle() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        paddleSubscriptionService.syncTierPriceToPaddle(tierRepository.findById(tierId).orElseThrow());

        // Same reasoning as the update-items tests above: resolving the tier's synced
        // Paddle Price id succeeds, so the request passes our own resolver and reaches
        // the mocked-to-throw PaddleClient.previewUpdateSubscriptionItems. The
        // important assertion is that this is no longer our own 409 conflict.
        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("preview_shouldSurfaceCreditApplied_whenPaddlePreviewIncludesADiscount")
    void preview_shouldSurfaceCreditApplied_whenPaddlePreviewIncludesADiscount() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        paddleSubscriptionService.syncTierPriceToPaddle(tierRepository.findById(tierId).orElseThrow());

        // doReturn, not when(...).thenReturn(...): the default setUp() stub for this
        // same method throws, and when(mock.method()) evaluates that stubbed call
        // before attaching the new one — doReturn skips invoking the mock entirely.
        Mockito.doReturn(new PaddleSubscriptionPreview(
                        null,
                        new PaddlePreviewImmediateTransaction(
                                new PaddlePreviewTransactionDetails(
                                        new PaddlePreviewTotals("1250", "10000", "EUR")))))
                .when(paddleClient).previewUpdateSubscriptionItems(any(), any(), any());

        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.immediateChargeAmount").value(12))
                .andExpect(jsonPath("$.creditApplied").value(100))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    @DisplayName("preview_shouldReturn409_whenNoPaddleSubscriptionYet")
    void preview_shouldReturn409_whenNoPaddleSubscriptionYet() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("preview_shouldReturn409_insteadOfPaddlesRaw400_whenADowngradeIsAttemptedWithACancellationAlreadyScheduled")
    void preview_shouldReturn409_insteadOfPaddlesRaw400_whenADowngradeIsAttemptedWithACancellationAlreadyScheduled()
            throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        givenOrgHasScheduledCancellation();
        paddleSubscriptionService.syncTierPriceToPaddle(tierRepository.findById(tierId).orElseThrow());

        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("preview_shouldReturn409_whenChangeIsMixed_addingAndRemovingAddonsInTheSameRequest")
    void preview_shouldReturn409_whenChangeIsMixed_addingAndRemovingAddonsInTheSameRequest() throws Exception {
        List<SubscriptionAddonModel> addons = addonRepository.findAll();
        UUID existingAddonId = addons.get(0).getId();
        UUID newAddonId = addons.get(1).getId();

        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tierId", tierId, "billingCycle", "MONTHLY", "addonIds", List.of(existingAddonId)))))
                .andExpect(status().isOk());

        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("tierId", tierId, "addonIds", List.of(newAddonId)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This change both adds something more expensive and removes something cheaper — "
                                + "please save these as two separate changes"));
    }

    @Test
    @DisplayName("preview_shouldReturn400_forInternalOrg")
    void preview_shouldReturn400_forInternalOrg() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgIsInternal();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("preview_shouldReturn403_forNonOwner")
    void preview_shouldReturn403_forNonOwner() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("preview_shouldReturn404_whenTierDoesNotExist")
    void preview_shouldReturn404_whenTierDoesNotExist() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    // ─── POST /api/organisations/{orgId}/subscription/paddle/cancel ───────────

    @Test
    @DisplayName("cancel_shouldReachPaddleApi_insteadOfFailingAtGuard_onceSubscriptionExists")
    void cancel_shouldReachPaddleApi_insteadOfFailingAtGuard_onceSubscriptionExists() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        // Same reasoning as the update-items tests above: the request passes our own
        // guard and reaches the mocked-to-throw PaddleClient.cancelSubscription
        // instead of failing at our own guard.
        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancel(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("cancel_shouldReturn409_whenNoPaddleSubscriptionYet")
    void cancel_shouldReturn409_whenNoPaddleSubscriptionYet() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancel(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("cancel_shouldReturn409_whenCancellationAlreadyScheduled")
    void cancel_shouldReturn409_whenCancellationAlreadyScheduled() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        givenOrgHasScheduledCancellation();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancel(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "A cancellation is already scheduled for this subscription"));
    }

    @Test
    @DisplayName("cancel_shouldReturn400_forInternalOrg")
    void cancel_shouldReturn400_forInternalOrg() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgIsInternal();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancel(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cancel_shouldReturn403_forNonOwner")
    void cancel_shouldReturn403_forNonOwner() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancel(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cancel_shouldReturn404_whenOrgHasNoSubscriptionRecordYet")
    void cancel_shouldReturn404_whenOrgHasNoSubscriptionRecordYet() throws Exception {
        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancel(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isNotFound());
    }

    // ─── POST /api/organisations/{orgId}/subscription/paddle/resume ───────────

    @Test
    @DisplayName("resume_shouldReachPaddleApi_insteadOfFailingAtGuard_onceCancellationScheduled")
    void resume_shouldReachPaddleApi_insteadOfFailingAtGuard_onceCancellationScheduled() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        givenOrgHasScheduledCancellation();

        // Same reasoning as cancel above: the request passes our own guard and reaches
        // the mocked-to-throw PaddleClient.removeScheduledCancellation instead of
        // failing at our own guard.
        mockMvc.perform(post(ApiPaths.paddleSubscriptionResume(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("resume_shouldReturn409_whenNoCancellationScheduled")
    void resume_shouldReturn409_whenNoCancellationScheduled() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionResume(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "There's no scheduled cancellation on this subscription to resume"));
    }

    @Test
    @DisplayName("resume_shouldReturn409_whenNoPaddleSubscriptionYet")
    void resume_shouldReturn409_whenNoPaddleSubscriptionYet() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionResume(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("resume_shouldReturn400_forInternalOrg")
    void resume_shouldReturn400_forInternalOrg() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgIsInternal();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionResume(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("resume_shouldReturn403_forNonOwner")
    void resume_shouldReturn403_forNonOwner() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        givenOrgHasScheduledCancellation();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionResume(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("resume_shouldReturn404_whenOrgHasNoSubscriptionRecordYet")
    void resume_shouldReturn404_whenOrgHasNoSubscriptionRecordYet() throws Exception {
        mockMvc.perform(post(ApiPaths.paddleSubscriptionResume(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isNotFound());
    }

    // ─── POST .../subscription/paddle/cancel-pending-downgrade ────────────────

    @Test
    @DisplayName("cancelPendingDowngrade_shouldReachPaddleApi_insteadOfFailingAtGuard_oncePendingDowngradeExists")
    void cancelPendingDowngrade_shouldReachPaddleApi_insteadOfFailingAtGuard_oncePendingDowngradeExists()
            throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        givenOrgHasPendingDowngrade();

        // Same reasoning as cancel/resume above: the request passes our own guard and
        // reaches the mocked-to-throw PaddleClient.updateSubscriptionItems (reverting
        // to the active tier) instead of failing at our own guard.
        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancelPendingDowngrade(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("cancelPendingDowngrade_shouldReturn409_whenNoPendingDowngrade")
    void cancelPendingDowngrade_shouldReturn409_whenNoPendingDowngrade() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancelPendingDowngrade(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "There's no pending downgrade on this subscription to cancel"));
    }

    @Test
    @DisplayName("cancelPendingDowngrade_shouldReturn409_whenNoPaddleSubscriptionYet")
    void cancelPendingDowngrade_shouldReturn409_whenNoPaddleSubscriptionYet() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancelPendingDowngrade(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("cancelPendingDowngrade_shouldReturn400_forInternalOrg")
    void cancelPendingDowngrade_shouldReturn400_forInternalOrg() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgIsInternal();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancelPendingDowngrade(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("cancelPendingDowngrade_shouldReturn403_forNonOwner")
    void cancelPendingDowngrade_shouldReturn403_forNonOwner() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();
        givenOrgHasPendingDowngrade();

        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancelPendingDowngrade(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cancelPendingDowngrade_shouldReturn404_whenOrgHasNoSubscriptionRecordYet")
    void cancelPendingDowngrade_shouldReturn404_whenOrgHasNoSubscriptionRecordYet() throws Exception {
        mockMvc.perform(post(ApiPaths.paddleSubscriptionCancelPendingDowngrade(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isNotFound());
    }

    // ─── GET .../subscription/paddle/transactions ──────────────────────────────

    @Test
    @DisplayName("getBillingHistory_shouldReturn200WithMappedTransaction_forOwner")
    void getBillingHistory_shouldReturn200WithMappedTransaction_forOwner() throws Exception {
        givenOrgHasSubscriptionRecord();
        String initiateResponse = mockMvc.perform(post(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String customerId = objectMapper.readTree(initiateResponse).get("paddleCustomerId").asText();

        var tier = tierRepository.findById(tierId).orElseThrow();
        String transactionId = "txn_" + UUID.randomUUID();
        String amountMinorUnits = String.valueOf(tier.getPriceMonthly() * 100);
        when(paddleClient.listBillingHistory(customerId)).thenReturn(List.of(new PaddleTransaction(
                transactionId, "draft", List.of(), null, "EUR",
                new PaddleTransactionDetails(new PaddleTransactionTotals(amountMinorUnits)))));

        // Exercises the actual response mapping (PaddleTransaction/.../
        // PaddleBillingTransactionResponse) against a hand-built but realistically
        // shaped transaction, not just an empty array.
        mockMvc.perform(get(ApiPaths.paddleSubscriptionTransactions(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(transactionId))
                .andExpect(jsonPath("$[0].status").value("draft"))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[0].totalAmount").value(tier.getPriceMonthly()));
    }

    @Test
    @DisplayName("getBillingHistory_shouldReturn200WithEmptyList_whenNoPaddleCustomerYet")
    void getBillingHistory_shouldReturn200WithEmptyList_whenNoPaddleCustomerYet() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(get(ApiPaths.paddleSubscriptionTransactions(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("getBillingHistory_shouldReturn400_forInternalOrg")
    void getBillingHistory_shouldReturn400_forInternalOrg() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgIsInternal();

        mockMvc.perform(get(ApiPaths.paddleSubscriptionTransactions(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("getBillingHistory_shouldReturn403_forNonOwner")
    void getBillingHistory_shouldReturn403_forNonOwner() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(get(ApiPaths.paddleSubscriptionTransactions(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getBillingHistory_shouldReturn200WithEmptyList_whenNoSubscriptionRecordAtAll")
    void getBillingHistory_shouldReturn200WithEmptyList_whenNoSubscriptionRecordAtAll() throws Exception {
        // JOB-200: an org_subscriptions row may not exist at all yet (never staged
        // via the free picker) — that's not an error, just "no billing history yet",
        // same as having a row with no Paddle customer id.
        mockMvc.perform(get(ApiPaths.paddleSubscriptionTransactions(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── GET .../subscription/paddle/update-payment-method-transaction ────────

    @Test
    @DisplayName("getUpdatePaymentMethodTransaction_shouldReachPaddleApi_insteadOfFailingAtGuard_onceSubscriptionExists")
    void getUpdatePaymentMethodTransaction_shouldReachPaddleApi_insteadOfFailingAtGuard_onceSubscriptionExists()
            throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        // Same reasoning as cancel/update above: the request passes our own guard and
        // reaches the mocked-to-throw PaddleClient.getUpdatePaymentMethodTransaction
        // instead of failing at our own guard.
        mockMvc.perform(get(ApiPaths.paddleSubscriptionUpdatePaymentMethodTransaction(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransaction_shouldReturn409_whenNoPaddleSubscriptionYet")
    void getUpdatePaymentMethodTransaction_shouldReturn409_whenNoPaddleSubscriptionYet() throws Exception {
        givenOrgHasSubscriptionRecord();

        mockMvc.perform(get(ApiPaths.paddleSubscriptionUpdatePaymentMethodTransaction(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransaction_shouldReturn400_forInternalOrg")
    void getUpdatePaymentMethodTransaction_shouldReturn400_forInternalOrg() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgIsInternal();

        mockMvc.perform(get(ApiPaths.paddleSubscriptionUpdatePaymentMethodTransaction(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransaction_shouldReturn403_forNonOwner")
    void getUpdatePaymentMethodTransaction_shouldReturn403_forNonOwner() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        mockMvc.perform(get(ApiPaths.paddleSubscriptionUpdatePaymentMethodTransaction(orgId))
                        .with(jwt().jwt(j -> j.subject(memberId.toString()).claim("email", "member@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransaction_shouldReturn404_whenOrgHasNoSubscriptionRecordYet")
    void getUpdatePaymentMethodTransaction_shouldReturn404_whenOrgHasNoSubscriptionRecordYet() throws Exception {
        mockMvc.perform(get(ApiPaths.paddleSubscriptionUpdatePaymentMethodTransaction(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isNotFound());
    }

    // ─── OrgSubscriptionRepository — pending-downgrade / resume persistence ────
    //
    // These write pure DB state, no Paddle call involved, so — unlike the endpoint
    // tests above — they don't need a real Paddle subscription id to exercise for
    // real. Called directly against the real Testcontainers Postgres (JOB-198).

    @Test
    @DisplayName("clearScheduledCancellation clears the column and returns the updated model")
    void clearScheduledCancellation_shouldClearColumn() throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasScheduledCancellation();
        var subscription = subscriptionRepository.findByOrgId(orgId).orElseThrow();

        var result = subscriptionRepository.clearScheduledCancellation(subscription.getId(), orgId);

        assertThat(result.getPaddleScheduledCancellationAt()).isNull();
        assertThat(subscriptionRepository.findByOrgId(orgId).orElseThrow().getPaddleScheduledCancellationAt())
                .isNull();
    }

    @Test
    @DisplayName("schedulePendingDowngrade persists a pending tier and add-ons without touching the active ones")
    void schedulePendingDowngrade_shouldPersistPendingSelection() throws Exception {
        givenOrgHasSubscriptionRecord();
        var subscription = subscriptionRepository.findByOrgId(orgId).orElseThrow();
        UUID pendingTierId = tierRepository.findAll().get(1).getId();
        UUID addonId = addonRepository.findAll().getFirst().getId();
        Instant effectiveAt = Instant.parse("2026-09-01T00:00:00Z");

        var result = subscriptionRepository.schedulePendingDowngrade(
                subscription.getId(), orgId, pendingTierId, Set.of(addonId), effectiveAt);

        assertThat(result.getPendingTierId()).isEqualTo(pendingTierId);
        assertThat(result.getPendingAddonIds()).containsExactly(addonId);
        assertThat(result.getTierId()).isEqualTo(tierId);
        assertThat(result.getAddonIds()).isEmpty();
        assertThat(result.getPaddlePendingDowngradeEffectiveAt()).isEqualTo(effectiveAt);
    }

    @Test
    @DisplayName("applyPendingDowngrade promotes the pending tier/add-ons to active and clears the pending fields")
    void applyPendingDowngrade_shouldPromotePendingToActive() throws Exception {
        givenOrgHasSubscriptionRecord();
        var subscription = subscriptionRepository.findByOrgId(orgId).orElseThrow();
        UUID pendingTierId = tierRepository.findAll().get(1).getId();
        UUID addonId = addonRepository.findAll().getFirst().getId();
        subscriptionRepository.schedulePendingDowngrade(
                subscription.getId(), orgId, pendingTierId, Set.of(addonId), Instant.parse("2026-09-01T00:00:00Z"));

        subscriptionRepository.applyPendingDowngrade(subscription.getId());

        var result = subscriptionRepository.findByOrgId(orgId).orElseThrow();
        assertThat(result.getTierId()).isEqualTo(pendingTierId);
        assertThat(result.getAddonIds()).containsExactly(addonId);
        assertThat(result.getPendingTierId()).isNull();
        assertThat(result.getPendingAddonIds()).isEmpty();
        assertThat(result.getPaddlePendingDowngradeEffectiveAt()).isNull();
    }

    @Test
    @DisplayName("clearPendingDowngrade reverts to keeping the currently active tier/add-ons and clears "
            + "the pending fields")
    void clearPendingDowngrade_shouldClearPendingFields_withoutTouchingActivePlan() throws Exception {
        givenOrgHasSubscriptionRecord();
        var subscription = subscriptionRepository.findByOrgId(orgId).orElseThrow();
        UUID pendingTierId = tierRepository.findAll().get(1).getId();
        UUID addonId = addonRepository.findAll().getFirst().getId();
        subscriptionRepository.schedulePendingDowngrade(
                subscription.getId(), orgId, pendingTierId, Set.of(addonId), Instant.parse("2026-09-01T00:00:00Z"));

        var result = subscriptionRepository.clearPendingDowngrade(subscription.getId(), orgId);

        assertThat(result.getTierId()).isEqualTo(tierId);
        assertThat(result.getAddonIds()).isEmpty();
        assertThat(result.getPendingTierId()).isNull();
        assertThat(result.getPendingAddonIds()).isEmpty();
        assertThat(result.getPaddlePendingDowngradeEffectiveAt()).isNull();
        assertThat(subscriptionRepository.findByOrgId(orgId).orElseThrow().getPendingTierId()).isNull();
    }

    @Test
    @DisplayName("applyPendingDowngrade is a no-op when nothing is pending")
    void applyPendingDowngrade_shouldNoOp_whenNothingPending() throws Exception {
        givenOrgHasSubscriptionRecord();
        var subscription = subscriptionRepository.findByOrgId(orgId).orElseThrow();

        subscriptionRepository.applyPendingDowngrade(subscription.getId());

        var result = subscriptionRepository.findByOrgId(orgId).orElseThrow();
        assertThat(result.getTierId()).isEqualTo(tierId);
        assertThat(result.getAddonIds()).isEmpty();
    }
}
