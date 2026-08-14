package com.opsclear.dto;

import com.opsclear.model.SubscriptionTierModel;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SubscriptionTierResponse {

    private UUID id;
    private int maxMembers;
    private Integer maxProjects;
    private int priceMonthly;
    private int priceAnnual;
    private String currency;
    private int displayOrder;
    private String paddlePriceIdMonthly;
    private String paddlePriceIdAnnual;

    public static SubscriptionTierResponse from(SubscriptionTierModel model) {
        return SubscriptionTierResponse.builder()
                .id(model.getId())
                .maxMembers(model.getMaxMembers())
                .maxProjects(model.getMaxProjects())
                .priceMonthly(model.getPriceMonthly())
                .priceAnnual(model.getPriceAnnual())
                .currency(model.getCurrency())
                .displayOrder(model.getDisplayOrder())
                .paddlePriceIdMonthly(model.getPaddlePriceIdMonthly())
                .paddlePriceIdAnnual(model.getPaddlePriceIdAnnual())
                .build();
    }
}
