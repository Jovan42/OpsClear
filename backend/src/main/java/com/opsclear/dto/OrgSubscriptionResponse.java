package com.opsclear.dto;

import com.opsclear.model.OrgSubscriptionModel;
import com.opsclear.model.SubscriptionAddonModel;
import com.opsclear.model.SubscriptionTierModel;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class OrgSubscriptionResponse {

    private UUID id;
    private String billingCycle;
    private boolean isInternal;
    private Instant updatedAt;
    private SubscriptionTierResponse tier;
    private List<SubscriptionAddonResponse> addons;
    private int totalMonthly;
    private String paddleCustomerId;
    private String paddleSubscriptionId;
    private String subscriptionStatus;

    public static OrgSubscriptionResponse from(
            OrgSubscriptionModel model,
            SubscriptionTierModel tier,
            List<SubscriptionAddonModel> addons
    ) {
        boolean annual = "ANNUAL".equals(model.getBillingCycle());
        int basePrice = annual ? tier.getPriceAnnual() : tier.getPriceMonthly();
        int addonTotal = addons.stream()
                .mapToInt(a -> annual ? a.getPriceAnnual() : a.getPriceMonthly())
                .sum();

        return OrgSubscriptionResponse.builder()
                .id(model.getId())
                .billingCycle(model.getBillingCycle())
                .isInternal(model.isInternal())
                .updatedAt(model.getUpdatedAt())
                .tier(SubscriptionTierResponse.from(tier))
                .addons(addons.stream().map(SubscriptionAddonResponse::from).toList())
                .totalMonthly(basePrice + addonTotal)
                .paddleCustomerId(model.getPaddleCustomerId())
                .paddleSubscriptionId(model.getPaddleSubscriptionId())
                .subscriptionStatus(model.getSubscriptionStatus())
                .build();
    }
}
