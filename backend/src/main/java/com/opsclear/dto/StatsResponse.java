package com.opsclear.dto;

import com.opsclear.model.StatsResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response of {@code GET /api/projects/{projectId}/stats} (ADR-0050 §API). {@code buckets} has exactly
 * {@code weeks} entries, oldest first. {@code members} is null for MEMBERs and for filtered requests.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsResponse {

    private int weeks;
    private String timezone;
    private UUID memberId;
    private StatsSummaryResponse summary;
    private List<StatsBucketResponse> buckets;
    private List<StatsMemberResponse> members;

    public static StatsResponse from(StatsResult r) {
        return StatsResponse.builder()
                .weeks(r.getWeeks())
                .timezone(r.getTimezone())
                .memberId(r.getMemberId())
                .summary(StatsSummaryResponse.from(r.getSummary()))
                .buckets(r.getBuckets().stream().map(StatsBucketResponse::from).toList())
                .members(r.getMembers() != null
                        ? r.getMembers().stream().map(StatsMemberResponse::from).toList()
                        : null)
                .build();
    }
}
