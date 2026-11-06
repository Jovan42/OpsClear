package com.opsclear.dto;

import com.opsclear.model.StatsSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Velocity metrics over the whole stats range (ADR-0050). Durations in seconds; averages and lateRate are null
 * when their denominator is zero.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsSummaryResponse {

    private int completedCount;
    private Long avgLeadTimeSeconds;
    private Long avgCycleTimeSeconds;
    private Long avgBlockedSeconds;
    private int completedWithDeadline;
    private int completedLate;
    private Double lateRate;

    public static StatsSummaryResponse from(StatsSummary s) {
        return StatsSummaryResponse.builder()
                .completedCount(s.getCompletedCount())
                .avgLeadTimeSeconds(s.getAvgLeadTimeSeconds())
                .avgCycleTimeSeconds(s.getAvgCycleTimeSeconds())
                .avgBlockedSeconds(s.getAvgBlockedSeconds())
                .completedWithDeadline(s.getCompletedWithDeadline())
                .completedLate(s.getCompletedLate())
                .lateRate(s.getLateRate())
                .build();
    }
}
