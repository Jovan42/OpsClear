package com.opsclear.dto;

import com.opsclear.model.SubscriptionAddonModel;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SubscriptionAddonResponse {

    private UUID id;
    private String key;
    private String name;
    private int priceMonthly;
    private int priceAnnual;
    private boolean available;
    private int displayOrder;
    private String paddlePriceIdMonthly;
    private String paddlePriceIdAnnual;

    public static SubscriptionAddonResponse from(SubscriptionAddonModel model) {
        return SubscriptionAddonResponse.builder()
                .id(model.getId())
                .key(model.getKey())
                .name(model.getName())
                .priceMonthly(model.getPriceMonthly())
                .priceAnnual(model.getPriceAnnual())
                .available(model.isAvailable())
                .displayOrder(model.getDisplayOrder())
                .paddlePriceIdMonthly(model.getPaddlePriceIdMonthly())
                .paddlePriceIdAnnual(model.getPaddlePriceIdAnnual())
                .build();
    }
}
