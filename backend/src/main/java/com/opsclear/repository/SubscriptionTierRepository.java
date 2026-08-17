package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.SubscriptionTiersRecord;
import com.opsclear.model.SubscriptionTierModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.SUBSCRIPTION_TIERS;

@Repository
@RequiredArgsConstructor
public class SubscriptionTierRepository {

    private final DSLContext dsl;

    public List<SubscriptionTierModel> findAll() {
        return dsl.selectFrom(SUBSCRIPTION_TIERS)
                .orderBy(SUBSCRIPTION_TIERS.DISPLAY_ORDER.asc())
                .fetch()
                .map(this::toModel);
    }

    public Optional<SubscriptionTierModel> findById(UUID id) {
        return dsl.selectFrom(SUBSCRIPTION_TIERS)
                .where(SUBSCRIPTION_TIERS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    // Reverse lookup for the webhook (JOB-200) — resolving a real Paddle
    // subscription's item price ids back to our own catalog, since the first-ever
    // org_subscriptions row is created from what Paddle actually confirms was
    // paid for, never trusted from the client. Matches either the monthly or
    // annual price id; the caller determines which cycle matched.
    public Optional<SubscriptionTierModel> findByPaddlePriceId(String priceId) {
        return dsl.selectFrom(SUBSCRIPTION_TIERS)
                .where(SUBSCRIPTION_TIERS.PADDLE_PRICE_ID_MONTHLY.eq(priceId)
                        .or(SUBSCRIPTION_TIERS.PADDLE_PRICE_ID_ANNUAL.eq(priceId)))
                .fetchOptional()
                .map(this::toModel);
    }

    public SubscriptionTierModel updatePrice(UUID id, int priceMonthly, int priceAnnual) {
        dsl.update(SUBSCRIPTION_TIERS)
                .set(SUBSCRIPTION_TIERS.PRICE_MONTHLY, priceMonthly)
                .set(SUBSCRIPTION_TIERS.PRICE_ANNUAL, priceAnnual)
                .where(SUBSCRIPTION_TIERS.ID.eq(id))
                .execute();
        return findById(id).orElseThrow();
    }

    public SubscriptionTierModel updatePaddleIds(
            UUID id, String paddleProductId, String paddlePriceIdMonthly, String paddlePriceIdAnnual) {
        dsl.update(SUBSCRIPTION_TIERS)
                .set(SUBSCRIPTION_TIERS.PADDLE_PRODUCT_ID, paddleProductId)
                .set(SUBSCRIPTION_TIERS.PADDLE_PRICE_ID_MONTHLY, paddlePriceIdMonthly)
                .set(SUBSCRIPTION_TIERS.PADDLE_PRICE_ID_ANNUAL, paddlePriceIdAnnual)
                .where(SUBSCRIPTION_TIERS.ID.eq(id))
                .execute();
        return findById(id).orElseThrow();
    }

    private SubscriptionTierModel toModel(SubscriptionTiersRecord r) {
        return SubscriptionTierModel.builder()
                .id(r.getId())
                .maxMembers(r.getMaxMembers())
                .maxProjects(r.getMaxProjects())
                .priceMonthly(r.getPriceMonthly())
                .priceAnnual(r.getPriceAnnual())
                .currency(r.getCurrency())
                .displayOrder(r.getDisplayOrder())
                .paddleProductId(r.getPaddleProductId())
                .paddlePriceIdMonthly(r.getPaddlePriceIdMonthly())
                .paddlePriceIdAnnual(r.getPaddlePriceIdAnnual())
                .build();
    }
}
