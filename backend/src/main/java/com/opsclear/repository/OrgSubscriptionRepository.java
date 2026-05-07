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
                .build();
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt.toInstant(ZoneOffset.UTC);
    }
}
