package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionAddonModel {

    private UUID id;
    private String key;
    private String name;
    private int priceMonthly;
    private int priceAnnual;
    private boolean available;
    private int displayOrder;
    private String paddleProductId;
    private String paddlePriceIdMonthly;
    private String paddlePriceIdAnnual;
}
