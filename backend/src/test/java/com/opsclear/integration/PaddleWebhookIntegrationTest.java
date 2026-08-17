package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORGANISATIONS;
import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises {@code POST /api/webhooks/paddle} end-to-end against the app's real
 * signature-verification and persistence code, but WITHOUT calling Paddle's real
 * API: there is no live public URL yet for Paddle's sandbox to deliver a webhook to
 * (that only exists once this branch is deployed and a notification destination is
 * configured in Paddle's dashboard — a manual step explicitly out of scope for
 * JOB-174). Instead, payloads here are self-signed in test code using the exact
 * HMAC-SHA256 scheme documented at developer.paddle.com/webhooks/signature-verification,
 * with the fixture secret wired into {@code application-test.properties}
 * ({@code paddle.webhook-secret=test-webhook-secret-fixture}). This proves the
 * verification and dispatch logic is correct; it does not prove Paddle's real
 * deliveries match this shape byte-for-byte (see PaddleWebhookService's class
 * Javadoc and the PR description for the verified-vs-assumed breakdown).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Paddle webhook endpoint")
class PaddleWebhookIntegrationTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret-fixture";
    private static final String TIMESTAMP = "1700000000";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PaddleSubscriptionService paddleSubscriptionService;

    @Value("${paddle.webhook-secret}")
    private String configuredSecret;

    private UUID ownerId;
    private UUID orgId;
    private UUID tierId;
    private String customerId;

    @BeforeEach
    void setUp() throws Exception {
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        String ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        userRepository.save(UserModel.builder().id(ownerId).email(ownerEmail).name("Owner").build());

        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", ownerEmail).claim("name", "Owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme Corp", "slug", "ACM"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        orgId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        tierId = tierRepository.findAll().getFirst().getId();

        mockMvc.perform(put(ApiPaths.orgSubscription(orgId))
                        .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", ownerEmail)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("tierId", tierId, "billingCycle", "MONTHLY", "addonIds", List.of()))))
                .andExpect(status().isOk());

        customerId = "ctm_" + UUID.randomUUID();
        dsl.update(ORGANISATIONS)
                .set(ORGANISATIONS.PADDLE_CUSTOMER_ID, customerId)
                .where(ORGANISATIONS.ID.eq(orgId))
                .execute();
    }

    @Test
    @DisplayName("configured test fixture secret matches what this test class signs with")
    void fixtureSecret_shouldMatch_configuredWebhookSecret() {
        assertThat(configuredSecret).isEqualTo(WEBHOOK_SECRET);
    }

    @Test
    @DisplayName("subscription.created with status=active sets ACTIVE and stores the Paddle subscription id")
    void webhook_shouldActivateSubscription_onSubscriptionCreated() throws Exception {
        String subscriptionId = "sub_" + UUID.randomUUID();
        String body = subscriptionEventBody("subscription.created", subscriptionId, "active");

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        assertPersistedStatus(subscriptionId, "ACTIVE");
    }

    @Test
    @DisplayName("JOB-200: a first-ever subscription.created webhook creates the org_subscriptions row by "
            + "resolving Paddle's own item price ids back to our tier/add-on catalog — nothing here is trusted "
            + "from a client")
    void webhook_shouldCreateSubscriptionRow_onFirstEverEvent_whenOrgHasNoRowYet() throws Exception {
        // setUp() already staged a row via the free picker (needed so other tests in
        // this class exercise the UPDATE path) — undo that specifically for this
        // test, which exercises the CREATE path for an org with no row at all yet.
        dsl.deleteFrom(ORG_SUBSCRIPTIONS).where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId)).execute();

        SubscriptionTierModel tier =
                paddleSubscriptionService.syncTierPriceToPaddle(tierRepository.findById(tierId).orElseThrow());
        SubscriptionAddonModel addon =
                paddleSubscriptionService.syncAddonPriceToPaddle(addonRepository.findAll().getFirst());
        String subscriptionId = "sub_" + UUID.randomUUID();
        String body = "{\"event_id\":\"evt_" + UUID.randomUUID() + "\",\"event_type\":\"subscription.created\","
                + "\"data\":{\"id\":\"" + subscriptionId + "\",\"customer_id\":\"" + customerId + "\","
                + "\"status\":\"active\",\"items\":[{\"price\":{\"id\":\"" + tier.getPaddlePriceIdMonthly()
                + "\"}},{\"price\":{\"id\":\"" + addon.getPaddlePriceIdMonthly() + "\"}}]}}";

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        var record = dsl.selectFrom(ORG_SUBSCRIPTIONS)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .fetchSingle();
        assertThat(record.getTierId()).isEqualTo(tierId);
        assertThat(record.getBillingCycle()).isEqualTo("MONTHLY");
        assertThat(record.getPaddleSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(record.getSubscriptionStatus()).isEqualTo("ACTIVE");

        var subscription = subscriptionRepository.findByOrgId(orgId).orElseThrow();
        assertThat(subscription.getAddonIds()).containsExactly(addon.getId());
    }

    @Test
    @DisplayName("JOB-200: a first-ever webhook with no items is ignored — nothing to resolve a plan from")
    void webhook_shouldIgnore_whenFirstEverEventHasNoItems() throws Exception {
        dsl.deleteFrom(ORG_SUBSCRIPTIONS).where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId)).execute();
        String body = subscriptionEventBody("subscription.created", "sub_" + UUID.randomUUID(), "active");

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        assertThat(dsl.selectFrom(ORG_SUBSCRIPTIONS).where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId)).fetchOptional())
                .isEmpty();
    }

    @Test
    @DisplayName("JOB-200: a first-ever webhook whose items don't resolve to any known tier is ignored")
    void webhook_shouldIgnore_whenFirstEverEventItemsDontResolveToATier() throws Exception {
        dsl.deleteFrom(ORG_SUBSCRIPTIONS).where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId)).execute();
        String body = "{\"event_id\":\"evt_" + UUID.randomUUID() + "\",\"event_type\":\"subscription.created\","
                + "\"data\":{\"id\":\"sub_" + UUID.randomUUID() + "\",\"customer_id\":\"" + customerId + "\","
                + "\"status\":\"active\",\"items\":[{\"price\":{\"id\":\"pri_not_in_our_catalog\"}}]}}";

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        assertThat(dsl.selectFrom(ORG_SUBSCRIPTIONS).where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId)).fetchOptional())
                .isEmpty();
    }

    @Test
    @DisplayName("subscription.updated with status=past_due sets PAST_DUE")
    void webhook_shouldSetPastDue_onSubscriptionPastDue() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String body = subscriptionEventBody("subscription.updated", subscriptionId, "past_due");

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        assertPersistedStatus(subscriptionId, "PAST_DUE");
    }

    @Test
    @DisplayName("subscription.canceled sets CANCELED")
    void webhook_shouldSetCanceled_onSubscriptionCanceled() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String body = subscriptionEventBody("subscription.canceled", subscriptionId, "canceled");

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        assertPersistedStatus(subscriptionId, "CANCELED");
    }

    @Test
    @DisplayName("subscription.updated with a scheduled cancel persists the scheduled cancellation date")
    void webhook_shouldPersistScheduledCancellation_whenScheduledChangeIsCancel() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String body = subscriptionEventBodyWithScheduledChange(
                "subscription.updated", subscriptionId, "active", "cancel", "2024-10-12T07:20:50.52Z");

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        assertPersistedScheduledCancellation(subscriptionId, Instant.parse("2024-10-12T07:20:50.52Z"));
    }

    @Test
    @DisplayName("subscription.updated with no scheduled change clears a previously scheduled cancellation")
    void webhook_shouldClearScheduledCancellation_whenScheduledChangeIsNull() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String scheduled = subscriptionEventBodyWithScheduledChange(
                "subscription.updated", subscriptionId, "active", "cancel", "2024-10-12T07:20:50.52Z");
        postWebhook(scheduled, signatureHeader(scheduled, WEBHOOK_SECRET)).andExpect(status().isOk());
        assertPersistedScheduledCancellation(subscriptionId, Instant.parse("2024-10-12T07:20:50.52Z"));

        String resumed = subscriptionEventBody("subscription.updated", subscriptionId, "active");
        postWebhook(resumed, signatureHeader(resumed, WEBHOOK_SECRET)).andExpect(status().isOk());

        assertPersistedScheduledCancellation(subscriptionId, null);
    }

    @Test
    @DisplayName("subscription.updated with a scheduled change that isn't a cancel (e.g. pause) doesn't persist "
            + "a scheduled cancellation")
    void webhook_shouldIgnoreScheduledCancellation_whenScheduledChangeIsNotCancel() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String body = subscriptionEventBodyWithScheduledChange(
                "subscription.updated", subscriptionId, "active", "pause", "2024-10-12T07:20:50.52Z");

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        assertPersistedScheduledCancellation(subscriptionId, null);
    }

    @Test
    @DisplayName("transaction.payment_failed is accepted with 200 but does not mutate subscription status")
    void webhook_shouldAcceptButIgnore_transactionPaymentFailed() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String body = "{\"event_id\":\"evt_txn_failed\",\"event_type\":\"transaction.payment_failed\","
                + "\"data\":{\"id\":\"txn_1\"}}";

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        assertPersistedStatus(subscriptionId, "ACTIVE");
    }

    @Test
    @DisplayName("a request with an invalid signature is rejected and does not mutate state")
    void webhook_shouldReject_whenSignatureInvalid() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String body = subscriptionEventBody("subscription.canceled", subscriptionId, "canceled");
        String badHeader = "ts=" + TIMESTAMP + ";h1=" + "0".repeat(64);

        postWebhook(body, badHeader)
                .andExpect(status().isForbidden());

        assertPersistedStatus(subscriptionId, "ACTIVE");
    }

    @Test
    @DisplayName("a signature header missing the ts component is rejected")
    void webhook_shouldReject_whenHeaderMissingTimestamp() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String body = subscriptionEventBody("subscription.canceled", subscriptionId, "canceled");
        String hash = hmacSha256Hex(TIMESTAMP + ":" + body, WEBHOOK_SECRET);

        postWebhook(body, "h1=" + hash)
                .andExpect(status().isForbidden());

        assertPersistedStatus(subscriptionId, "ACTIVE");
    }

    @Test
    @DisplayName("a signature header missing the h1 component is rejected")
    void webhook_shouldReject_whenHeaderMissingHash() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String body = subscriptionEventBody("subscription.canceled", subscriptionId, "canceled");

        postWebhook(body, "ts=" + TIMESTAMP)
                .andExpect(status().isForbidden());

        assertPersistedStatus(subscriptionId, "ACTIVE");
    }

    @Test
    @DisplayName("a request with a missing signature header is rejected")
    void webhook_shouldReject_whenSignatureHeaderMissing() throws Exception {
        String body = subscriptionEventBody("subscription.canceled", "sub_x", "canceled");

        mockMvc.perform(post(ApiPaths.WEBHOOKS_PADDLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an event referencing an unknown customer id is accepted but does not error")
    void webhook_shouldAcceptButNoOp_whenNoMatchingOrgForCustomerId() throws Exception {
        String body = "{\"event_id\":\"evt_" + UUID.randomUUID() + "\",\"event_type\":\"subscription.created\","
                + "\"data\":{\"id\":\"sub_orphan\",\"customer_id\":\"ctm_no_such_customer\",\"status\":\"active\"}}";

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        var record = dsl.selectFrom(ORG_SUBSCRIPTIONS)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .fetchSingle();
        assertThat(record.getPaddleSubscriptionId()).isNull();
    }

    @Test
    @DisplayName("an unrecognized subscription status is accepted but does not mutate state")
    void webhook_shouldAcceptButIgnore_whenSubscriptionStatusUnrecognized() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        String body = subscriptionEventBody("subscription.updated", subscriptionId, "some_future_status");

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        assertPersistedStatus(subscriptionId, "ACTIVE");
    }

    @Test
    @DisplayName("a signature header with a malformed extra segment is still accepted")
    void webhook_shouldAccept_whenSignatureHeaderHasMalformedSegment() throws Exception {
        String subscriptionId = "sub_" + UUID.randomUUID();
        String body = subscriptionEventBody("subscription.created", subscriptionId, "active");
        String header = "not-a-key-value-pair;" + signatureHeader(body, WEBHOOK_SECRET);

        postWebhook(body, header)
                .andExpect(status().isOk());

        assertPersistedStatus(subscriptionId, "ACTIVE");
    }

    @Test
    @DisplayName("a body that isn't valid JSON is rejected even with a validly-computed signature")
    void webhook_shouldReject_whenBodyIsNotValidJson() throws Exception {
        String body = "not-json";

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("subscription.updated with current_billing_period persists its starts_at")
    void webhook_shouldPersistCurrentPeriodStartsAt_whenPresent() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        Instant periodStart = Instant.parse("2026-08-01T00:00:00Z");
        String body = subscriptionEventBodyWithPeriod("subscription.updated", subscriptionId, "active", periodStart);

        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        var record = dsl.selectFrom(ORG_SUBSCRIPTIONS)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .fetchSingle();
        assertThat(record.getPaddleCurrentPeriodStartsAt().toInstant()).isEqualTo(periodStart);
    }

    @Test
    @DisplayName("JOB-198: a pending downgrade is applied once the webhook reports the period has rolled over")
    void webhook_shouldApplyPendingDowngrade_whenPeriodRollsOver() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        UUID pendingTierId = tierRepository.findAll().get(1).getId();
        Instant previousPeriodStart = Instant.parse("2026-07-01T00:00:00Z");
        Instant newPeriodStart = Instant.parse("2026-08-01T00:00:00Z");

        dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.PENDING_TIER_ID, pendingTierId)
                .set(ORG_SUBSCRIPTIONS.PADDLE_CURRENT_PERIOD_STARTS_AT, previousPeriodStart.atOffset(ZoneOffset.UTC))
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .execute();

        String body = subscriptionEventBodyWithPeriod("subscription.updated", subscriptionId, "active", newPeriodStart);
        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        var record = dsl.selectFrom(ORG_SUBSCRIPTIONS)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .fetchSingle();
        assertThat(record.getTierId()).isEqualTo(pendingTierId);
        assertThat(record.getPendingTierId()).isNull();
        assertThat(record.getPaddleCurrentPeriodStartsAt().toInstant()).isEqualTo(newPeriodStart);
    }

    @Test
    @DisplayName("JOB-198: a pending downgrade is left untouched when the period has not rolled over yet")
    void webhook_shouldNotApplyPendingDowngrade_whenPeriodUnchanged() throws Exception {
        String subscriptionId = givenExistingPaddleSubscription("ACTIVE");
        UUID originalTierId = tierId;
        UUID pendingTierId = tierRepository.findAll().get(1).getId();
        Instant periodStart = Instant.parse("2026-08-01T00:00:00Z");

        dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.PENDING_TIER_ID, pendingTierId)
                .set(ORG_SUBSCRIPTIONS.PADDLE_CURRENT_PERIOD_STARTS_AT, periodStart.atOffset(ZoneOffset.UTC))
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .execute();

        String body = subscriptionEventBodyWithPeriod("subscription.updated", subscriptionId, "active", periodStart);
        postWebhook(body, signatureHeader(body, WEBHOOK_SECRET))
                .andExpect(status().isOk());

        var record = dsl.selectFrom(ORG_SUBSCRIPTIONS)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .fetchSingle();
        assertThat(record.getTierId()).isEqualTo(originalTierId);
        assertThat(record.getPendingTierId()).isEqualTo(pendingTierId);
    }

    @Test
    @DisplayName("redelivering the same event is idempotent — same end state, no error")
    void webhook_shouldBeIdempotent_onRedelivery() throws Exception {
        String subscriptionId = "sub_" + UUID.randomUUID();
        String body = subscriptionEventBody("subscription.created", subscriptionId, "active");
        String header = signatureHeader(body, WEBHOOK_SECRET);

        postWebhook(body, header).andExpect(status().isOk());
        postWebhook(body, header).andExpect(status().isOk());

        assertPersistedStatus(subscriptionId, "ACTIVE");
    }

    // --- helpers ---

    private String givenExistingPaddleSubscription(String status) {
        String subscriptionId = "sub_" + UUID.randomUUID();
        dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.PADDLE_SUBSCRIPTION_ID, subscriptionId)
                .set(ORG_SUBSCRIPTIONS.SUBSCRIPTION_STATUS, status)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .execute();
        return subscriptionId;
    }

    private void assertPersistedStatus(String expectedSubscriptionId, String expectedStatus) {
        var record = dsl.selectFrom(ORG_SUBSCRIPTIONS)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .fetchSingle();
        assertThat(record.getPaddleSubscriptionId()).isEqualTo(expectedSubscriptionId);
        assertThat(record.getSubscriptionStatus()).isEqualTo(expectedStatus);
    }

    private void assertPersistedScheduledCancellation(String expectedSubscriptionId, Instant expectedEffectiveAt) {
        var record = dsl.selectFrom(ORG_SUBSCRIPTIONS)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .fetchSingle();
        assertThat(record.getPaddleSubscriptionId()).isEqualTo(expectedSubscriptionId);
        if (expectedEffectiveAt == null) {
            assertThat(record.getPaddleScheduledCancellationAt()).isNull();
        } else {
            assertThat(record.getPaddleScheduledCancellationAt().toInstant()).isEqualTo(expectedEffectiveAt);
        }
    }

    private org.springframework.test.web.servlet.ResultActions postWebhook(String body, String signatureHeader)
            throws Exception {
        return mockMvc.perform(post(ApiPaths.WEBHOOKS_PADDLE)
                .header("Paddle-Signature", signatureHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String subscriptionEventBody(String eventType, String subscriptionId, String status) {
        return "{\"event_id\":\"evt_" + UUID.randomUUID() + "\",\"event_type\":\"" + eventType + "\","
                + "\"data\":{\"id\":\"" + subscriptionId + "\",\"customer_id\":\"" + customerId + "\","
                + "\"status\":\"" + status + "\"}}";
    }

    private String subscriptionEventBodyWithPeriod(
            String eventType, String subscriptionId, String status, Instant periodStartsAt) {
        return "{\"event_id\":\"evt_" + UUID.randomUUID() + "\",\"event_type\":\"" + eventType + "\","
                + "\"data\":{\"id\":\"" + subscriptionId + "\",\"customer_id\":\"" + customerId + "\","
                + "\"status\":\"" + status + "\",\"current_billing_period\":{\"starts_at\":\""
                + periodStartsAt + "\"}}}";
    }

    private String subscriptionEventBodyWithScheduledChange(
            String eventType, String subscriptionId, String status, String scheduledAction, String effectiveAt) {
        return "{\"event_id\":\"evt_" + UUID.randomUUID() + "\",\"event_type\":\"" + eventType + "\","
                + "\"data\":{\"id\":\"" + subscriptionId + "\",\"customer_id\":\"" + customerId + "\","
                + "\"status\":\"" + status + "\",\"scheduled_change\":{\"action\":\"" + scheduledAction
                + "\",\"effective_at\":\"" + effectiveAt + "\",\"resume_at\":null}}}";
    }

    private static String signatureHeader(String body, String secret) {
        String hash = hmacSha256Hex(TIMESTAMP + ":" + body, secret);
        return "ts=" + TIMESTAMP + ";h1=" + hash;
    }

    private static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
