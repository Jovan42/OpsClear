package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.UserModel;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTIONS;
import static com.opsclear.generated.jooq.Tables.SUBSCRIPTION_TIERS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against Paddle's real sandbox API (not mocked) per this project's explicit
 * choice for JOB-173 — sandbox, not live, so there's no real-money consequence.
 * Requires PADDLE_API_KEY to be set in the environment running these tests; if it
 * isn't, every test here fails with a connection/auth error against Paddle, not a
 * silent skip — that's intentional, matching this project's "confirmed against a
 * real API call" testing philosophy rather than a mocked stand-in.
 *
 * <p>The update-items endpoint's actual successful round-trip to Paddle's
 * {@code PATCH /subscriptions/{id}} still can't be exercised end-to-end here: Paddle
 * does not support creating a Subscription via API at all (only real checkout
 * completion creates one, JOB-178), so there is no real subscription id sandbox-side
 * to PATCH against. What JOB-176 does unblock is the resolver step — once a tier/
 * addon has been synced to Paddle (see {@code SuperAdminPricingIntegrationTest}),
 * resolving its Price id succeeds for real, and the request genuinely reaches
 * Paddle's API instead of failing at our own resolver; it then fails there instead,
 * because {@code sub_test_placeholder} isn't a real subscription id — see
 * {@code update_shouldReachPaddleApi_insteadOfFailingAtResolver_onceTierSyncedToPaddle}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Paddle subscription endpoints")
class PaddleSubscriptionIntegrationTest {

    private static final OffsetDateTime SCHEDULED_CANCELLATION_FIXTURE = OffsetDateTime.parse("2024-10-12T07:20:50.52Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaddleSubscriptionService paddleSubscriptionService;

    private UUID ownerId;
    private UUID memberId;
    private UUID orgId;
    private UUID tierId;
    // Unique per run: Paddle's sandbox customer data is NOT reset the way the
    // local DB is (subscriptionRepository.deleteAll() etc. below), so a literal
    // hardcoded email collides with a customer left over from a previous run
    // (Paddle rejects a second POST /customers for the same email with 409
    // customer_already_exists). Randomizing keeps this suite re-runnable.
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
    @DisplayName("initiate_shouldReturn404_whenOrgHasNoSubscriptionRecordYet")
    void initiate_shouldReturn404_whenOrgHasNoSubscriptionRecordYet() throws Exception {
        mockMvc.perform(post(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail))))
                .andExpect(status().isNotFound());
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

        // Resolving the tier's real Paddle Price id now succeeds, so the request
        // genuinely reaches Paddle's PATCH /subscriptions/{id} — it then fails there
        // (surfaced as a generic 500 via GlobalExceptionHandler's catch-all) because
        // sub_test_placeholder isn't a real Paddle subscription; Paddle subscriptions
        // can only be created via real checkout (JOB-178), never faked in sandbox.
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

        // Same reasoning as the tier-only test above: resolving the addon's real
        // Paddle Price id now succeeds, so the request reaches Paddle's real API and
        // fails there (sub_test_placeholder isn't a real subscription) instead of
        // failing at our own resolver.
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
        // MONTHLY, already covered above) — the request reaches Paddle's real API
        // instead of failing at our own resolver.
        mockMvc.perform(put(ApiPaths.paddleSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isInternalServerError());
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

        // Same reasoning as the update-items tests above: resolving the tier's real
        // Paddle Price id now succeeds, so the request genuinely reaches Paddle's real
        // PATCH /subscriptions/{id}/preview — it then fails there because
        // sub_test_placeholder isn't a real Paddle subscription. The important
        // assertion is that this is no longer our own 409 conflict.
        mockMvc.perform(post(ApiPaths.paddleSubscriptionPreview(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tierId", tierId))))
                .andExpect(status().isInternalServerError());
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

        // Same reasoning as the update-items tests above: sub_test_placeholder isn't
        // a real Paddle subscription, so the request reaches Paddle's real API and
        // fails there instead of failing at our own guard.
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

        // Same reasoning as cancel above: sub_test_placeholder isn't a real Paddle
        // subscription, so the request reaches Paddle's real API and fails there
        // instead of failing at our own guard.
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

    // ─── GET .../subscription/paddle/update-payment-method-transaction ────────

    @Test
    @DisplayName("getUpdatePaymentMethodTransaction_shouldReachPaddleApi_insteadOfFailingAtGuard_onceSubscriptionExists")
    void getUpdatePaymentMethodTransaction_shouldReachPaddleApi_insteadOfFailingAtGuard_onceSubscriptionExists()
            throws Exception {
        givenOrgHasSubscriptionRecord();
        givenOrgHasFakePaddleSubscriptionId();

        // Same reasoning as cancel/update above: sub_test_placeholder isn't a real
        // Paddle subscription, so the request reaches Paddle's real API and fails
        // there instead of failing at our own guard.
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
}
