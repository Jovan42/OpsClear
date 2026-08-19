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
    // Only set when Paddle's preview actually applied a discount to this charge (e.g.
    // an unconsumed credit) — null, not zero, when there's nothing to show (JOB-180).
    private Integer creditApplied;
    private String currency;
    private Instant effectiveAt;
}
