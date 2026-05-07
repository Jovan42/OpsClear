package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.SubscriptionTiersRecord;
import com.opsclear.model.SubscriptionTierModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    private SubscriptionTierModel toModel(SubscriptionTiersRecord r) {
        return SubscriptionTierModel.builder()
                .id(r.getId())
                .maxMembers(r.getMaxMembers())
                .maxProjects(r.getMaxProjects())
                .priceMonthly(r.getPriceMonthly())
                .priceAnnual(r.getPriceAnnual())
                .currency(r.getCurrency())
                .displayOrder(r.getDisplayOrder())
                .build();
    }
}
