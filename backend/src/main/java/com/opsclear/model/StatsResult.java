package com.opsclear.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Project velocity stats (ADR-0050), built by StatsService. Field names follow the ADR's response JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsResult {

    private int weeks;
    private String timezone;
    private UUID memberId;             // effective filter: forced to the caller for MEMBERs
    private StatsSummary summary;
    @Builder.Default
    private List<StatsBucket> buckets = new ArrayList<>(); // exactly `weeks` entries, oldest first
    private List<StatsMember> members; // OWNER/ADMIN without memberId only, null otherwise
}
