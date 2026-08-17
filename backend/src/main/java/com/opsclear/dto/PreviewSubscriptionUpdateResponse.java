package com.opsclear.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreviewSubscriptionUpdateResponse {

    private boolean upgrade;
    private Integer immediateChargeAmount;
    private String currency;
    private Instant effectiveAt;
}
