package com.opsclear.dto;

import com.opsclear.model.StatsBucket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Velocity metrics for one ISO week (ADR-0050). {@code weekStart} is the Monday, serialized as an ISO date.
 * Empty weeks have zero counts and null averages.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsBucketResponse {

    private LocalDate weekStart;
    private int completedCount;
    private Long avgLeadTimeSeconds;
    private Long avgCycleTimeSeconds;
    private Long avgBlockedSeconds;
    private int completedWithDeadline;
    private int completedLate;

    public static StatsBucketResponse from(StatsBucket b) {
        return StatsBucketResponse.builder()
                .weekStart(b.getWeekStart())
                .completedCount(b.getCompletedCount())
                .avgLeadTimeSeconds(b.getAvgLeadTimeSeconds())
                .avgCycleTimeSeconds(b.getAvgCycleTimeSeconds())
                .avgBlockedSeconds(b.getAvgBlockedSeconds())
                .completedWithDeadline(b.getCompletedWithDeadline())
                .completedLate(b.getCompletedLate())
                .build();
    }
}
