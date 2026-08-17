package com.opsclear.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.repository.OrgSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaddleWebhookService")
class PaddleWebhookServiceTest {

    private static final String SECRET = "test-webhook-secret-fixture";
    private static final String TIMESTAMP = "1700000000";

    @Mock private OrgSubscriptionRepository orgSubscriptionRepository;

    private PaddleWebhookService service;

    @BeforeEach
    void setUp() {
        // A bare `new ObjectMapper()` doesn't match Spring Boot's autoconfigured bean
        // in two ways this test needs: no jsr310 module (Instant deserialization would
        // silently fail) and FAIL_ON_UNKNOWN_PROPERTIES left at Jackson's own default of
        // true (Spring Boot turns it off) — a real payload's resume_at field, which this
        // codebase deliberately doesn't map, would otherwise throw.
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        service = new PaddleWebhookService(objectMapper, orgSubscriptionRepository, SECRET);
    }

    // --- signature verification ---

    @Test
    @DisplayName("handle rejects a request with no Paddle-Signature header")
    void handle_shouldReject_whenSignatureHeaderMissing() {
        String body = subscriptionEventBody("subscription.created", "active");

        assertThatThrownBy(() -> service.handle(null, body))
                .isInstanceOf(ForbiddenException.class);
        verify(orgSubscriptionRepository, never()).updateFromPaddleWebhook(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle rejects a request whose signature doesn't match the body")
    void handle_shouldReject_whenSignatureDoesNotMatchBody() {
        String body = subscriptionEventBody("subscription.created", "active");
        String wrongHash = hmacSha256Hex(TIMESTAMP + ":" + "{\"different\":\"payload\"}", SECRET);
        String header = "ts=" + TIMESTAMP + ";h1=" + wrongHash;

        assertThatThrownBy(() -> service.handle(header, body))
                .isInstanceOf(ForbiddenException.class);
        verify(orgSubscriptionRepository, never()).updateFromPaddleWebhook(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle rejects a request signed with the wrong secret")
    void handle_shouldReject_whenSignedWithWrongSecret() {
        String body = subscriptionEventBody("subscription.created", "active");
        String header = signatureHeader(body, "some-other-secret");

        assertThatThrownBy(() -> service.handle(header, body))
                .isInstanceOf(ForbiddenException.class);
        verify(orgSubscriptionRepository, never()).updateFromPaddleWebhook(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle rejects a header missing the ts component")
    void handle_shouldReject_whenHeaderMissingTimestamp() {
        String body = subscriptionEventBody("subscription.created", "active");
        String hash = hmacSha256Hex(TIMESTAMP + ":" + body, SECRET);

        assertThatThrownBy(() -> service.handle("h1=" + hash, body))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("handle rejects a header missing the h1 component")
    void handle_shouldReject_whenHeaderMissingHash() {
        String body = subscriptionEventBody("subscription.created", "active");

        assertThatThrownBy(() -> service.handle("ts=" + TIMESTAMP, body))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("handle accepts a signature header containing a malformed extra segment")
    void handle_shouldAccept_whenHeaderHasMalformedSegment() {
        String body = subscriptionEventBody("subscription.created", "active");
        String header = "not-a-key-value-pair;" + signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, null)).thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository).updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, null);
    }

    @Test
    @DisplayName("handle accepts a signature header containing an unrecognized but well-formed key=value segment")
    void handle_shouldAccept_whenHeaderHasUnrecognizedKeyValueSegment() {
        String body = subscriptionEventBody("subscription.created", "active");
        String header = "foo=bar;" + signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, null)).thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository).updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, null);
    }

    @Test
    @DisplayName("handle rejects a body that isn't valid JSON, even with a valid signature")
    void handle_shouldReject_whenBodyNotValidJson() {
        String body = "not-json";
        String header = signatureHeader(body, SECRET);

        assertThatThrownBy(() -> service.handle(header, body))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- subscription status sync ---

    @ParameterizedTest(name = "Paddle status \"{0}\" maps to local status \"{1}\"")
    @CsvSource({
        "active,ACTIVE",
        "trialing,ACTIVE",
        "past_due,PAST_DUE",
        "canceled,CANCELED",
        "paused,CANCELED"
    })
    @DisplayName("handle maps each known Paddle subscription status to the correct local status")
    void handle_shouldMapPaddleStatus_toLocalStatus(String paddleStatus, String localStatus) {
        String body = subscriptionEventBody("subscription.updated", paddleStatus);
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", localStatus, null, null)).thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository).updateFromPaddleWebhook("ctm_123", "sub_123", localStatus, null, null);
    }

    @Test
    @DisplayName("handle ignores an unrecognized Paddle subscription status without throwing")
    void handle_shouldIgnore_whenSubscriptionStatusUnrecognized() {
        String body = subscriptionEventBody("subscription.updated", "some_future_status");
        String header = signatureHeader(body, SECRET);

        service.handle(header, body);

        verify(orgSubscriptionRepository, never()).updateFromPaddleWebhook(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle logs and no-ops when no org matches the event's customer id")
    void handle_shouldNoOp_whenNoMatchingOrg() {
        String body = subscriptionEventBody("subscription.created", "active");
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, null)).thenReturn(0);

        service.handle(header, body);

        verify(orgSubscriptionRepository).updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, null);
    }

    @Test
    @DisplayName("handle is idempotent across redelivery of the same event")
    void handle_shouldBeIdempotent_onRedelivery() {
        String body = subscriptionEventBody("subscription.created", "active");
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, null)).thenReturn(1);

        service.handle(header, body);
        service.handle(header, body);

        verify(orgSubscriptionRepository, times(2))
                .updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, null);
    }

    // --- scheduled cancellation (JOB-197) ---

    @Test
    @DisplayName("handle persists the scheduled cancellation date when scheduled_change.action is cancel")
    void handle_shouldPersistScheduledCancellation_whenActionIsCancel() {
        Instant effectiveAt = Instant.parse("2024-10-12T07:20:50.52Z");
        String body = subscriptionEventBodyWithScheduledChange("subscription.updated", "active", "cancel", effectiveAt);
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.updateFromPaddleWebhook(eq("ctm_123"), eq("sub_123"), eq("ACTIVE"), eq(effectiveAt), any()))
                .thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository).updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", effectiveAt, null);
    }

    @Test
    @DisplayName("handle clears the scheduled cancellation date when scheduled_change is null")
    void handle_shouldClearScheduledCancellation_whenScheduledChangeIsNull() {
        String body = subscriptionEventBody("subscription.updated", "active");
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, null)).thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository).updateFromPaddleWebhook(eq("ctm_123"), eq("sub_123"), eq("ACTIVE"), isNull(), any());
    }

    @Test
    @DisplayName("handle ignores a scheduled_change whose action isn't cancel")
    void handle_shouldIgnoreScheduledChange_whenActionIsNotCancel() {
        Instant effectiveAt = Instant.parse("2024-10-12T07:20:50.52Z");
        String body = subscriptionEventBodyWithScheduledChange("subscription.updated", "paused", "pause", effectiveAt);
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "CANCELED", null, null)).thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository).updateFromPaddleWebhook(eq("ctm_123"), eq("sub_123"), eq("CANCELED"), isNull(), any());
    }

    // --- current billing period / pending downgrade rollover (JOB-198) ---

    @Test
    @DisplayName("handle passes current_billing_period.starts_at through to updateFromPaddleWebhook")
    void handle_shouldPersistCurrentPeriodStartsAt_whenPresent() {
        Instant periodStart = Instant.parse("2026-08-01T00:00:00Z");
        String body = subscriptionEventBodyWithPeriod("subscription.updated", "active", periodStart);
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.findByPaddleCustomerId("ctm_123")).thenReturn(Optional.empty());
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, periodStart))
                .thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository).updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, periodStart);
        verify(orgSubscriptionRepository, never()).applyPendingDowngrade(any());
    }

    @Test
    @DisplayName("handle does not look up a pending downgrade when the event carries no current_billing_period "
            + "(paused/canceled events, per Paddle's docs)")
    void handle_shouldNotLookUpPendingDowngrade_whenCurrentPeriodAbsent() {
        String body = subscriptionEventBody("subscription.canceled", "canceled");
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "CANCELED", null, null))
                .thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository, never()).findByPaddleCustomerId(any());
        verify(orgSubscriptionRepository, never()).applyPendingDowngrade(any());
    }

    @Test
    @DisplayName("handle applies a pending downgrade once the webhook reports the period has actually rolled over")
    void handle_shouldApplyPendingDowngrade_whenPeriodRolledOver() {
        UUID subscriptionId = UUID.randomUUID();
        Instant previousPeriodStart = Instant.parse("2026-07-01T00:00:00Z");
        Instant newPeriodStart = Instant.parse("2026-08-01T00:00:00Z");
        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(UUID.randomUUID())
                .paddleCurrentPeriodStartsAt(previousPeriodStart).pendingTierId(UUID.randomUUID()).build();

        String body = subscriptionEventBodyWithPeriod("subscription.updated", "active", newPeriodStart);
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.findByPaddleCustomerId("ctm_123")).thenReturn(Optional.of(subscription));
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, newPeriodStart))
                .thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository).applyPendingDowngrade(subscriptionId);
    }

    @Test
    @DisplayName("handle applies a pending downgrade when the org has no previously-known period start at all "
            + "(e.g. its first webhook since JOB-198 shipped)")
    void handle_shouldApplyPendingDowngrade_whenNoPreviousPeriodKnown() {
        UUID subscriptionId = UUID.randomUUID();
        Instant newPeriodStart = Instant.parse("2026-08-01T00:00:00Z");
        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(UUID.randomUUID())
                .paddleCurrentPeriodStartsAt(null).pendingTierId(UUID.randomUUID()).build();

        String body = subscriptionEventBodyWithPeriod("subscription.updated", "active", newPeriodStart);
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.findByPaddleCustomerId("ctm_123")).thenReturn(Optional.of(subscription));
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, newPeriodStart))
                .thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository).applyPendingDowngrade(subscriptionId);
    }

    @Test
    @DisplayName("handle does not apply a pending downgrade when the period has not actually rolled over")
    void handle_shouldNotApplyPendingDowngrade_whenPeriodUnchanged() {
        UUID subscriptionId = UUID.randomUUID();
        Instant periodStart = Instant.parse("2026-08-01T00:00:00Z");
        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(UUID.randomUUID())
                .paddleCurrentPeriodStartsAt(periodStart).pendingTierId(UUID.randomUUID()).build();

        String body = subscriptionEventBodyWithPeriod("subscription.updated", "active", periodStart);
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.findByPaddleCustomerId("ctm_123")).thenReturn(Optional.of(subscription));
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, periodStart))
                .thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository, never()).applyPendingDowngrade(any());
    }

    @Test
    @DisplayName("handle does not apply a pending downgrade when the period rolled over but nothing is pending")
    void handle_shouldNotApplyPendingDowngrade_whenNothingPending() {
        UUID subscriptionId = UUID.randomUUID();
        Instant previousPeriodStart = Instant.parse("2026-07-01T00:00:00Z");
        Instant newPeriodStart = Instant.parse("2026-08-01T00:00:00Z");
        OrgSubscriptionModel subscription = OrgSubscriptionModel.builder()
                .id(subscriptionId).orgId(UUID.randomUUID())
                .paddleCurrentPeriodStartsAt(previousPeriodStart).pendingTierId(null).build();

        String body = subscriptionEventBodyWithPeriod("subscription.updated", "active", newPeriodStart);
        String header = signatureHeader(body, SECRET);
        when(orgSubscriptionRepository.findByPaddleCustomerId("ctm_123")).thenReturn(Optional.of(subscription));
        when(orgSubscriptionRepository.updateFromPaddleWebhook("ctm_123", "sub_123", "ACTIVE", null, newPeriodStart))
                .thenReturn(1);

        service.handle(header, body);

        verify(orgSubscriptionRepository, never()).applyPendingDowngrade(any());
    }

    // --- non-subscription events ---

    @Test
    @DisplayName("handle ignores transaction.payment_failed without mutating subscription status")
    void handle_shouldIgnore_transactionPaymentFailed() {
        String body = "{\"event_id\":\"evt_1\",\"event_type\":\"transaction.payment_failed\","
                + "\"data\":{\"id\":\"txn_123\"}}";
        String header = signatureHeader(body, SECRET);

        service.handle(header, body);

        verify(orgSubscriptionRepository, never()).updateFromPaddleWebhook(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle ignores transaction.completed without mutating subscription status")
    void handle_shouldIgnore_transactionCompleted() {
        String body = "{\"event_id\":\"evt_2\",\"event_type\":\"transaction.completed\","
                + "\"data\":{\"id\":\"txn_456\"}}";
        String header = signatureHeader(body, SECRET);

        service.handle(header, body);

        verify(orgSubscriptionRepository, never()).updateFromPaddleWebhook(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("handle ignores a completely unrecognized event type")
    void handle_shouldIgnore_unrecognizedEventType() {
        String body = "{\"event_id\":\"evt_3\",\"event_type\":\"some.future.event\",\"data\":{}}";
        String header = signatureHeader(body, SECRET);

        service.handle(header, body);

        verify(orgSubscriptionRepository, never()).updateFromPaddleWebhook(any(), any(), any(), any(), any());
    }

    // --- helpers ---

    private static String subscriptionEventBody(String eventType, String status) {
        return "{\"event_id\":\"evt_1\",\"event_type\":\"" + eventType + "\","
                + "\"data\":{\"id\":\"sub_123\",\"customer_id\":\"ctm_123\",\"status\":\"" + status
                + "\",\"scheduled_change\":null}}";
    }

    private static String subscriptionEventBodyWithPeriod(String eventType, String status, Instant periodStartsAt) {
        return "{\"event_id\":\"evt_1\",\"event_type\":\"" + eventType + "\","
                + "\"data\":{\"id\":\"sub_123\",\"customer_id\":\"ctm_123\",\"status\":\"" + status
                + "\",\"scheduled_change\":null,\"current_billing_period\":{\"starts_at\":\""
                + periodStartsAt + "\"}}}";
    }

    private static String subscriptionEventBodyWithScheduledChange(
            String eventType, String status, String scheduledAction, Instant effectiveAt) {
        return "{\"event_id\":\"evt_1\",\"event_type\":\"" + eventType + "\","
                + "\"data\":{\"id\":\"sub_123\",\"customer_id\":\"ctm_123\",\"status\":\"" + status + "\","
                + "\"scheduled_change\":{\"action\":\"" + scheduledAction + "\",\"effective_at\":\""
                + effectiveAt + "\",\"resume_at\":null}}}";
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
