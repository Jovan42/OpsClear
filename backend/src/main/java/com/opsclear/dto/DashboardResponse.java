package com.opsclear.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private DashboardSummary summary;
    private List<JobSummary> blockedJobs;
    private List<JobSummary> overdueJobs;
    private List<ApprovalResponse> pendingApprovals;
    private List<JobTypeBreakdown> typeBreakdown;
}
