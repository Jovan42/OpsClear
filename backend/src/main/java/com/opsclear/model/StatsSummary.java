package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Velocity metrics over the whole stats range (ADR-0050). Averages and lateRate are null when their
 * denominator is zero.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsSummary {

    private int completedCount;
    private Long avgLeadTimeSeconds;
    private Long avgCycleTimeSeconds;
    private Long avgBlockedSeconds;
    private int completedWithDeadline;
    private int completedLate;
    private Double lateRate;
}
