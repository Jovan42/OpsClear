package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.SubscriptionAddonsRecord;
import com.opsclear.model.SubscriptionAddonModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.SUBSCRIPTION_ADDONS;

@Repository
@RequiredArgsConstructor
public class SubscriptionAddonRepository {

    private final DSLContext dsl;

    public List<SubscriptionAddonModel> findAll() {
        return dsl.selectFrom(SUBSCRIPTION_ADDONS)
                .orderBy(SUBSCRIPTION_ADDONS.DISPLAY_ORDER.asc())
                .fetch()
                .map(this::toModel);
    }

    public List<SubscriptionAddonModel> findByIds(Set<UUID> ids) {
        return dsl.selectFrom(SUBSCRIPTION_ADDONS)
                .where(SUBSCRIPTION_ADDONS.ID.in(ids))
                .orderBy(SUBSCRIPTION_ADDONS.DISPLAY_ORDER.asc())
                .fetch()
                .map(this::toModel);
    }

    public Optional<SubscriptionAddonModel> findByKey(String key) {
        return dsl.selectFrom(SUBSCRIPTION_ADDONS)
                .where(SUBSCRIPTION_ADDONS.KEY.eq(key))
                .fetchOptional()
                .map(this::toModel);
    }

    public Optional<SubscriptionAddonModel> findById(UUID id) {
        return dsl.selectFrom(SUBSCRIPTION_ADDONS)
                .where(SUBSCRIPTION_ADDONS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    public SubscriptionAddonModel updatePrice(String key, int priceMonthly, int priceAnnual) {
        dsl.update(SUBSCRIPTION_ADDONS)
                .set(SUBSCRIPTION_ADDONS.PRICE_MONTHLY, priceMonthly)
                .set(SUBSCRIPTION_ADDONS.PRICE_ANNUAL, priceAnnual)
                .where(SUBSCRIPTION_ADDONS.KEY.eq(key))
                .execute();
        return findByKey(key).orElseThrow();
    }

    public SubscriptionAddonModel updatePaddleIds(
            UUID id, String paddleProductId, String paddlePriceIdMonthly, String paddlePriceIdAnnual) {
        dsl.update(SUBSCRIPTION_ADDONS)
                .set(SUBSCRIPTION_ADDONS.PADDLE_PRODUCT_ID, paddleProductId)
                .set(SUBSCRIPTION_ADDONS.PADDLE_PRICE_ID_MONTHLY, paddlePriceIdMonthly)
                .set(SUBSCRIPTION_ADDONS.PADDLE_PRICE_ID_ANNUAL, paddlePriceIdAnnual)
                .where(SUBSCRIPTION_ADDONS.ID.eq(id))
                .execute();
        return findById(id).orElseThrow();
    }

    private SubscriptionAddonModel toModel(SubscriptionAddonsRecord r) {
        return SubscriptionAddonModel.builder()
                .id(r.getId())
                .key(r.getKey())
                .name(r.getName())
                .priceMonthly(r.getPriceMonthly())
                .priceAnnual(r.getPriceAnnual())
                .available(r.getAvailable())
                .displayOrder(r.getDisplayOrder())
                .paddleProductId(r.getPaddleProductId())
                .paddlePriceIdMonthly(r.getPaddlePriceIdMonthly())
                .paddlePriceIdAnnual(r.getPaddlePriceIdAnnual())
                .build();
    }
}
