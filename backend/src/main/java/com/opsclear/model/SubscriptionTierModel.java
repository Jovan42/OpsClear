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
public class SubscriptionTierModel {

    private UUID id;
    private int maxMembers;
    private Integer maxProjects;
    private int priceMonthly;
    private int priceAnnual;
    private String currency;
    private int displayOrder;
    private String paddleProductId;
    private String paddlePriceIdMonthly;
    private String paddlePriceIdAnnual;
}
