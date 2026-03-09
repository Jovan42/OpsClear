package com.opsclear.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummary {

    private int total;
    private int newCount;
    private int inProgressCount;
    private int blockedCount;
    private int completedCount;
    private int overdueCount;
    private int pendingApprovalsCount;
}
