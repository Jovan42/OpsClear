package com.opsclear.dto;

import com.opsclear.model.StatsMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Velocity metrics for one member over the whole stats range (ADR-0050), attributed to the current assignee.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsMemberResponse {

    private UUID userId;
    private String name;
    private int completedCount;
    private Long avgLeadTimeSeconds;
    private Long avgCycleTimeSeconds;
    private Long avgBlockedSeconds;
    private int completedWithDeadline;
    private int completedLate;
    private Double lateRate;

    public static StatsMemberResponse from(StatsMember m) {
        return StatsMemberResponse.builder()
                .userId(m.getUserId())
                .name(m.getName())
                .completedCount(m.getCompletedCount())
                .avgLeadTimeSeconds(m.getAvgLeadTimeSeconds())
                .avgCycleTimeSeconds(m.getAvgCycleTimeSeconds())
                .avgBlockedSeconds(m.getAvgBlockedSeconds())
                .completedWithDeadline(m.getCompletedWithDeadline())
                .completedLate(m.getCompletedLate())
                .lateRate(m.getLateRate())
                .build();
    }
}
