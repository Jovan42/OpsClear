package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Velocity metrics for one member over the whole stats range (ADR-0050), attributed to the current assignee.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsMember {

    private UUID userId;
    private String name;
    private int completedCount;
    private Long avgLeadTimeSeconds;
    private Long avgCycleTimeSeconds;
    private Long avgBlockedSeconds;
    private int completedWithDeadline;
    private int completedLate;
    private Double lateRate;
}
