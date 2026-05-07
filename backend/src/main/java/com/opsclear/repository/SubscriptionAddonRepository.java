package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.SubscriptionAddonsRecord;
import com.opsclear.model.SubscriptionAddonModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    private SubscriptionAddonModel toModel(SubscriptionAddonsRecord r) {
        return SubscriptionAddonModel.builder()
                .id(r.getId())
                .key(r.getKey())
                .name(r.getName())
                .priceMonthly(r.getPriceMonthly())
                .priceAnnual(r.getPriceAnnual())
                .available(r.getAvailable())
                .displayOrder(r.getDisplayOrder())
                .build();
    }
}
