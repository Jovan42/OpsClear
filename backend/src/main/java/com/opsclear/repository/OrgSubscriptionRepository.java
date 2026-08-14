package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.OrgSubscriptionsRecord;
import com.opsclear.model.OrgSubscriptionModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTION_ADDONS;
import static com.opsclear.generated.jooq.Tables.ORG_SUBSCRIPTIONS;
import static com.opsclear.generated.jooq.Tables.SUBSCRIPTION_ADDONS;

@Repository
@RequiredArgsConstructor
public class OrgSubscriptionRepository {

    private final DSLContext dsl;

    public Optional<OrgSubscriptionModel> findByOrgId(UUID orgId) {
        return dsl.selectFrom(ORG_SUBSCRIPTIONS)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .fetchOptional()
                .map(r -> toModel(r, fetchAddonIds(r.getId())));
    }

    public OrgSubscriptionModel create(UUID orgId, UUID tierId, String billingCycle, Set<UUID> addonIds) {
        UUID subscriptionId = dsl.insertInto(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.ORG_ID, orgId)
                .set(ORG_SUBSCRIPTIONS.TIER_ID, tierId)
                .set(ORG_SUBSCRIPTIONS.BILLING_CYCLE, billingCycle)
                .set(ORG_SUBSCRIPTIONS.CREATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .set(ORG_SUBSCRIPTIONS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .returning(ORG_SUBSCRIPTIONS.ID)
                .fetchSingle()
                .getId();

        replaceAddons(subscriptionId, addonIds);
        return findByOrgId(orgId).orElseThrow();
    }

    public OrgSubscriptionModel update(
            UUID subscriptionId, UUID orgId, UUID tierId, String billingCycle, Set<UUID> addonIds) {
        dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.TIER_ID, tierId)
                .set(ORG_SUBSCRIPTIONS.BILLING_CYCLE, billingCycle)
                .set(ORG_SUBSCRIPTIONS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(ORG_SUBSCRIPTIONS.ID.eq(subscriptionId))
                .execute();

        replaceAddons(subscriptionId, addonIds);
        return findByOrgId(orgId).orElseThrow();
    }

    public OrgSubscriptionModel updatePaddleCustomerId(UUID subscriptionId, UUID orgId, String paddleCustomerId) {
        dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.PADDLE_CUSTOMER_ID, paddleCustomerId)
                .set(ORG_SUBSCRIPTIONS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(ORG_SUBSCRIPTIONS.ID.eq(subscriptionId))
                .execute();
        return findByOrgId(orgId).orElseThrow();
    }

    // Written immediately rather than waiting on a webhook round-trip — same
    // reasoning as PaddleSubscriptionService.updateSubscriptionItems persisting
    // tier/addon selection immediately: this is the resume endpoint's own
    // authoritative action, not something to infer from Paddle's async event stream.
    public OrgSubscriptionModel clearScheduledCancellation(UUID subscriptionId, UUID orgId) {
        dsl.update(ORG_SUBSCRIPTIONS)
                .setNull(ORG_SUBSCRIPTIONS.PADDLE_SCHEDULED_CANCELLATION_AT)
                .set(ORG_SUBSCRIPTIONS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(ORG_SUBSCRIPTIONS.ID.eq(subscriptionId))
                .execute();
        return findByOrgId(orgId).orElseThrow();
    }

    // Keyed by paddle_customer_id (stable for the org's whole lifetime, set once by
    // JOB-173's initiate) rather than paddle_subscription_id — the latter doesn't
    // exist yet on the very first subscription.created event, which is exactly the
    // event that sets it for the first time. Returns rows-affected so the caller can
    // detect "no matching org" (0) without an exception — that's a legitimate,
    // non-retriable outcome for a webhook (e.g. sandbox noise), not an error.
    //
    // scheduledCancellationAt is always overwritten, including to null — a webhook
    // with no scheduled_change means any previous one no longer applies (e.g. Paddle
    // resumed the subscription via its own customer portal), so this must clear a
    // stale value, not just skip setting a new one.
    public int updateFromPaddleWebhook(
            String paddleCustomerId, String paddleSubscriptionId, String subscriptionStatus,
            Instant scheduledCancellationAt) {
        return dsl.update(ORG_SUBSCRIPTIONS)
                .set(ORG_SUBSCRIPTIONS.PADDLE_SUBSCRIPTION_ID, paddleSubscriptionId)
                .set(ORG_SUBSCRIPTIONS.SUBSCRIPTION_STATUS, subscriptionStatus)
                .set(ORG_SUBSCRIPTIONS.PADDLE_SCHEDULED_CANCELLATION_AT,
                        scheduledCancellationAt != null ? scheduledCancellationAt.atOffset(ZoneOffset.UTC) : null)
                .set(ORG_SUBSCRIPTIONS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(ORG_SUBSCRIPTIONS.PADDLE_CUSTOMER_ID.eq(paddleCustomerId))
                .execute();
    }

    // Empty when there's no subscription row at all, or when subscription_status is
    // itself null (org hasn't completed its first Paddle checkout yet) — both cases
    // are treated identically by callers as "no restriction applies".
    public Optional<String> findSubscriptionStatus(UUID orgId) {
        return dsl.select(ORG_SUBSCRIPTIONS.SUBSCRIPTION_STATUS)
                .from(ORG_SUBSCRIPTIONS)
                .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                .fetchOptional(ORG_SUBSCRIPTIONS.SUBSCRIPTION_STATUS);
    }

    public boolean isInternal(UUID orgId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(ORG_SUBSCRIPTIONS)
                        .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                        .and(ORG_SUBSCRIPTIONS.IS_INTERNAL.isTrue()));
    }

    public boolean hasAddon(UUID orgId, String addonKey) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(ORG_SUBSCRIPTIONS)
                        .join(ORG_SUBSCRIPTION_ADDONS)
                        .on(ORG_SUBSCRIPTION_ADDONS.ORG_SUBSCRIPTION_ID.eq(ORG_SUBSCRIPTIONS.ID))
                        .join(SUBSCRIPTION_ADDONS)
                        .on(SUBSCRIPTION_ADDONS.ID.eq(ORG_SUBSCRIPTION_ADDONS.ADDON_ID))
                        .where(ORG_SUBSCRIPTIONS.ORG_ID.eq(orgId))
                        .and(SUBSCRIPTION_ADDONS.KEY.eq(addonKey)));
    }

    public void deleteAll() {
        dsl.deleteFrom(ORG_SUBSCRIPTION_ADDONS).execute();
        dsl.deleteFrom(ORG_SUBSCRIPTIONS).execute();
    }

    private void replaceAddons(UUID subscriptionId, Set<UUID> addonIds) {
        dsl.deleteFrom(ORG_SUBSCRIPTION_ADDONS)
                .where(ORG_SUBSCRIPTION_ADDONS.ORG_SUBSCRIPTION_ID.eq(subscriptionId))
                .execute();
        for (UUID addonId : addonIds) {
            dsl.insertInto(ORG_SUBSCRIPTION_ADDONS)
                    .set(ORG_SUBSCRIPTION_ADDONS.ORG_SUBSCRIPTION_ID, subscriptionId)
                    .set(ORG_SUBSCRIPTION_ADDONS.ADDON_ID, addonId)
                    .execute();
        }

    }

    private List<UUID> fetchAddonIds(UUID subscriptionId) {
        return dsl.select(ORG_SUBSCRIPTION_ADDONS.ADDON_ID)
                .from(ORG_SUBSCRIPTION_ADDONS)
                .where(ORG_SUBSCRIPTION_ADDONS.ORG_SUBSCRIPTION_ID.eq(subscriptionId))
                .fetch(ORG_SUBSCRIPTION_ADDONS.ADDON_ID);
    }

    private OrgSubscriptionModel toModel(OrgSubscriptionsRecord r, List<UUID> addonIds) {
        return OrgSubscriptionModel.builder()
                .id(r.getId())
                .orgId(r.getOrgId())
                .tierId(r.getTierId())
                .billingCycle(r.getBillingCycle())
                .isInternal(r.getIsInternal())
                .createdAt(toInstant(r.getCreatedAt()))
                .updatedAt(toInstant(r.getUpdatedAt()))
                .addonIds(addonIds)
                .paddleCustomerId(r.getPaddleCustomerId())
                .paddleSubscriptionId(r.getPaddleSubscriptionId())
                .subscriptionStatus(r.getSubscriptionStatus())
                .paddleScheduledCancellationAt(r.getPaddleScheduledCancellationAt() == null
                        ? null : r.getPaddleScheduledCancellationAt().toInstant())
                .build();
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt.toInstant(ZoneOffset.UTC);
    }
}
