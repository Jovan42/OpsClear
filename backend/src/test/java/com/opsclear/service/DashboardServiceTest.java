package com.opsclear.service;

import com.opsclear.dto.DashboardResponse;
import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.ProjectMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService")
class DashboardServiceTest {

    @Mock private JobService jobService;
    @Mock private ApprovalService approvalService;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private DashboardService dashboardService;

    private UUID projectId;
    private UUID ownerId;
    private UUID memberId;

    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel memberMembership;

    private JobModel newJob;
    private JobModel inProgressJob;
    private JobModel blockedJobOlder;
    private JobModel blockedJobNewer;
    private JobModel overdueJobEarlier;
    private JobModel overdueJobLater;
    private JobModel completedJob;
    private JobModel overdueButCompletedJob;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(jobService, approvalService, projectMemberRepository);

        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();

        ownerMembership = ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build();

        memberMembership = ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build();

        Instant fiveDaysAgo = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);

        newJob = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("New Job")
                .status(JobStatus.NEW).build();

        inProgressJob = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("In Progress Job")
                .status(JobStatus.IN_PROGRESS).build();

        blockedJobOlder = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("Blocked Older")
                .status(JobStatus.BLOCKED).blockedReason("Waiting for parts")
                .blockedBy(ownerId).blockedAt(fiveDaysAgo).build();

        blockedJobNewer = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("Blocked Newer")
                .status(JobStatus.BLOCKED).blockedReason("Client not responding")
                .blockedBy(ownerId).blockedAt(twoDaysAgo).build();

        overdueJobEarlier = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("Overdue Earlier")
                .status(JobStatus.IN_PROGRESS)
                .deadline(Instant.now().minus(5, ChronoUnit.DAYS)).build();

        overdueJobLater = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("Overdue Later")
                .status(JobStatus.IN_PROGRESS)
                .deadline(Instant.now().minus(1, ChronoUnit.DAYS)).build();

        completedJob = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("Completed Job")
                .status(JobStatus.COMPLETED).build();

        overdueButCompletedJob = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("Overdue But Completed")
                .status(JobStatus.COMPLETED)
                .deadline(Instant.now().minus(1, ChronoUnit.DAYS)).build();
    }

    @Test
    @DisplayName("get — returns correct summary counts for all statuses")
    void get_shouldReturnCorrectSummaryCounts_whenProjectHasJobsInVariousStatuses() {
        List<JobModel> jobs = List.of(newJob, inProgressJob, blockedJobOlder, completedJob);
        when(jobService.list(projectId, ownerId, null, null)).thenReturn(jobs);
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of());

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getSummary().getTotal()).isEqualTo(4);
        assertThat(result.getSummary().getNewCount()).isEqualTo(1);
        assertThat(result.getSummary().getInProgressCount()).isEqualTo(1);
        assertThat(result.getSummary().getBlockedCount()).isEqualTo(1);
        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("get — returns blocked jobs sorted by blockedAt ascending (oldest first)")
    void get_shouldReturnBlockedJobs_sortedByBlockedAtAsc() {
        when(jobService.list(projectId, ownerId, null, null)).thenReturn(List.of(blockedJobNewer, blockedJobOlder));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of());

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getBlockedJobs()).hasSize(2);
        assertThat(result.getBlockedJobs().getFirst().getTitle()).isEqualTo("Blocked Older");
        assertThat(result.getBlockedJobs().get(1).getTitle()).isEqualTo("Blocked Newer");
    }

    @Test
    @DisplayName("get — returns overdue jobs sorted by deadline ascending (earliest deadline first)")
    void get_shouldReturnOverdueJobs_sortedByDeadlineAsc() {
        when(jobService.list(projectId, ownerId, null, null)).thenReturn(List.of(overdueJobLater, overdueJobEarlier));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of());

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getOverdueJobs()).hasSize(2);
        assertThat(result.getOverdueJobs().getFirst().getTitle()).isEqualTo("Overdue Earlier");
        assertThat(result.getOverdueJobs().get(1).getTitle()).isEqualTo("Overdue Later");
    }

    @Test
    @DisplayName("get — does not include completed jobs in overdue list even if past deadline")
    void get_shouldNotIncludeCompletedJobs_inOverdueList() {
        when(jobService.list(projectId, ownerId, null, null)).thenReturn(List.of(overdueButCompletedJob, overdueJobEarlier));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of());

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getOverdueJobs()).hasSize(1);
        assertThat(result.getOverdueJobs().getFirst().getTitle()).isEqualTo("Overdue Earlier");
    }

    @Test
    @DisplayName("get — overdue count in summary includes blocked jobs with past deadline")
    void get_shouldIncludeBlockedJobs_inOverdueCount_whenPastDeadline() {
        JobModel blockedAndOverdue = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("Blocked And Overdue")
                .status(JobStatus.BLOCKED).blockedReason("Stuck")
                .blockedBy(ownerId).blockedAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .deadline(Instant.now().minus(1, ChronoUnit.DAYS)).build();

        when(jobService.list(projectId, ownerId, null, null)).thenReturn(List.of(blockedAndOverdue));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of());

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getSummary().getBlockedCount()).isEqualTo(1);
        assertThat(result.getSummary().getOverdueCount()).isEqualTo(1);
        assertThat(result.getBlockedJobs()).hasSize(1);
        assertThat(result.getOverdueJobs()).hasSize(1);
    }

    @Test
    @DisplayName("get — returns pending approvals when caller is OWNER")
    void get_shouldReturnPendingApprovals_whenCallerIsOwner() {
        ApprovalModel approval = ApprovalModel.builder()
                .id(UUID.randomUUID()).jobId(UUID.randomUUID()).jobTitle("Some Job")
                .requesterId(memberId).description("Need budget approval")
                .status(ApprovalStatus.PENDING).requestedAt(Instant.now()).build();

        when(jobService.list(projectId, ownerId, null, null)).thenReturn(List.of());
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of(approval));

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getPendingApprovals()).hasSize(1);
        assertThat(result.getPendingApprovals().getFirst().getDescription()).isEqualTo("Need budget approval");
        assertThat(result.getSummary().getPendingApprovalsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("get — returns empty pending approvals for MEMBER without calling ApprovalService")
    void get_shouldReturnEmptyPendingApprovals_whenCallerIsMember() {
        when(jobService.list(projectId, memberId, null, null)).thenReturn(List.of(newJob));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId))
                .thenReturn(Optional.of(memberMembership));

        DashboardResponse result = dashboardService.get(projectId, memberId);

        assertThat(result.getPendingApprovals()).isEmpty();
        assertThat(result.getSummary().getPendingApprovalsCount()).isZero();
        verify(approvalService, never()).listPendingByProject(projectId, memberId);
    }

    @Test
    @DisplayName("get — does not include jobs with null deadline in overdue list")
    void get_shouldNotIncludeJobsWithNullDeadline_inOverdueList() {
        JobModel noDeadline = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("No Deadline")
                .status(JobStatus.IN_PROGRESS).deadline(null).build();

        when(jobService.list(projectId, ownerId, null, null)).thenReturn(List.of(noDeadline));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of());

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getOverdueJobs()).isEmpty();
        assertThat(result.getSummary().getOverdueCount()).isZero();
    }

    @Test
    @DisplayName("get — does not include jobs with a future deadline in overdue list")
    void get_shouldNotIncludeJobsWithFutureDeadline_inOverdueList() {
        JobModel futureDeadline = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("Future Deadline")
                .status(JobStatus.IN_PROGRESS)
                .deadline(Instant.now().plus(5, ChronoUnit.DAYS)).build();

        when(jobService.list(projectId, ownerId, null, null)).thenReturn(List.of(futureDeadline));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of());

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getOverdueJobs()).isEmpty();
        assertThat(result.getSummary().getOverdueCount()).isZero();
    }

    @Test
    @DisplayName("get — includes NEW status jobs with past deadline in overdue list")
    void get_shouldIncludeNewJob_inOverdueList_whenPastDeadline() {
        JobModel overdueNew = JobModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).title("Overdue New Job")
                .status(JobStatus.NEW)
                .deadline(Instant.now().minus(3, ChronoUnit.DAYS)).build();

        when(jobService.list(projectId, ownerId, null, null)).thenReturn(List.of(overdueNew));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of());

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getOverdueJobs()).hasSize(1);
        assertThat(result.getOverdueJobs().getFirst().getTitle()).isEqualTo("Overdue New Job");
        assertThat(result.getSummary().getOverdueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("get — returns empty lists when project has no jobs")
    void get_shouldReturnEmptyDashboard_whenProjectHasNoJobs() {
        when(jobService.list(projectId, ownerId, null, null)).thenReturn(List.of());
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId))
                .thenReturn(Optional.of(ownerMembership));
        when(approvalService.listPendingByProject(projectId, ownerId)).thenReturn(List.of());

        DashboardResponse result = dashboardService.get(projectId, ownerId);

        assertThat(result.getSummary().getTotal()).isZero();
        assertThat(result.getBlockedJobs()).isEmpty();
        assertThat(result.getOverdueJobs()).isEmpty();
        assertThat(result.getPendingApprovals()).isEmpty();
    }
}
