package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.aop.RequiresAddon;
import com.opsclear.dto.ApprovalResponse;
import com.opsclear.dto.DashboardResponse;
import com.opsclear.dto.DashboardSummary;
import com.opsclear.dto.JobSummary;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.ProjectMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Aggregates data from JobService and ApprovalService to build the project dashboard.o
 * NOTE: This service intentionally calls other services (JobService, ApprovalService)
 * rather than repositories directly. This is a deliberate, documented exception to the
 * controller → service → repository pattern — see ADR-0017 §4. DashboardService is the
 * only service permitted to call other services. This avoids duplicating MEMBER visibility
 * scoping and soft-delete filtering that already live in those services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final JobService jobService;
    private final ApprovalService approvalService;
    private final ProjectMemberRepository projectMemberRepository;

    @RequiresAddon(AddonCode.DASHBOARD)
    @Transactional(readOnly = true)
    public DashboardResponse get(UUID projectId, UUID callerId) {
        // jobService.list() validates project membership and applies MEMBER scoping
        List<JobModel> jobs = jobService.list(projectId, callerId, null, null, null);

        boolean isOwnerOrAdmin = projectMemberRepository
                .findByProjectIdAndUserId(projectId, callerId)
                .map(m -> m.getRole() != ProjectMemberRole.MEMBER)
                .orElse(false);

        List<ApprovalResponse> pendingApprovals = isOwnerOrAdmin
                ? approvalService.listPendingByProject(projectId, callerId)
                        .stream().map(ApprovalResponse::from).toList()
                : List.of();

        Instant now = Instant.now();

        List<JobSummary> blockedJobs = jobs.stream()
                .filter(j -> j.getStatus() == JobStatus.BLOCKED)
                .sorted(Comparator.comparing(JobModel::getBlockedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(JobSummary::from)
                .toList();

        List<JobSummary> overdueJobs = jobs.stream()
                .filter(j -> j.getDeadline() != null
                        && j.getDeadline().isBefore(now)
                        && j.getStatus() != JobStatus.COMPLETED)
                .sorted(Comparator.comparing(JobModel::getDeadline))
                .map(JobSummary::from)
                .toList();

        DashboardSummary summary = DashboardSummary.builder()
                .total(jobs.size())
                .newCount((int) jobs.stream().filter(j -> j.getStatus() == JobStatus.NEW).count())
                .inProgressCount((int) jobs.stream().filter(j -> j.getStatus() == JobStatus.IN_PROGRESS).count())
                .blockedCount(blockedJobs.size())
                .completedCount((int) jobs.stream().filter(j -> j.getStatus() == JobStatus.COMPLETED).count())
                .overdueCount(overdueJobs.size())
                .pendingApprovalsCount(pendingApprovals.size())
                .build();

        log.info("Dashboard loaded for project {} by user {}: {} jobs, {} blocked, {} overdue, {} pending approvals",
                projectId, callerId, jobs.size(), blockedJobs.size(), overdueJobs.size(), pendingApprovals.size());

        return DashboardResponse.builder()
                .summary(summary)
                .blockedJobs(blockedJobs)
                .overdueJobs(overdueJobs)
                .pendingApprovals(pendingApprovals)
                .build();
    }
}
