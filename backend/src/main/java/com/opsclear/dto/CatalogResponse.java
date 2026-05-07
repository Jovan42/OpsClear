package com.opsclear.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class CatalogResponse {

    private List<SubscriptionTierResponse> tiers;
    private List<SubscriptionAddonResponse> addons;
}
