package com.opsclear.service;

import com.opsclear.aop.AddonCode;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.ApprovalModel;
import com.opsclear.model.BlockReasonModel;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.JobStatusUpdateResult;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.PendingCompletionApprovalModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.JobLinkRepository;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.JobTypeRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobService: completion approval gate and pending completion approvals (ADR-0052)")
class JobServiceCompletionGateTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobRelationshipRepository jobRelationshipRepository;
    @Mock private JobLinkRepository jobLinkRepository;
    @Mock private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private JobTypeRepository jobTypeRepository;
    @Mock private BlockReasonService blockReasonService;
    @Mock private OrganisationRepository organisationRepository;
    @Mock private FriendlyIdService friendlyIdService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ApprovalRepository approvalRepository;
    @Mock private ApprovalService approvalService;
    @Mock private AddonAccessChecker addonAccessChecker;
    @Mock private CompletionApprovalCanceller completionApprovalCanceller;

    private JobService jobService;

    private UUID orgId;
    private UUID projectId;
    private UUID jobId;
    private UUID memberId;
    private ProjectModel project;
    private JobModel job;
    private PendingCompletionApprovalModel pending;

    @BeforeEach
    void setUp() {
        // Real transitioner, so the gated path's validate() and the direct path's transition() both really run
        JobStatusTransitioner transitioner = new JobStatusTransitioner(jobRepository, jobStatusHistoryRepository,
                jobRelationshipRepository, blockReasonService, eventPublisher);
        jobService = new JobService(jobRepository, jobRelationshipRepository, jobLinkRepository,
                jobStatusHistoryRepository, projectRepository, projectMemberRepository, userRepository,
                milestoneRepository, jobTypeRepository, organisationRepository, friendlyIdService, eventPublisher,
                transitioner, approvalRepository, approvalService, addonAccessChecker, completionApprovalCanceller);

        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        project = ProjectModel.builder().id(projectId).name("Gated").organisationId(orgId)
                .requireCompletionApproval(true).build();
        job = JobModel.builder().id(jobId).projectId(projectId).title("Install panel")
                .status(JobStatus.IN_PROGRESS).assignedTo(memberId).build();
        pending = PendingCompletionApprovalModel.builder().id(UUID.randomUUID()).jobId(jobId)
                .requesterId(memberId).requesterName("Max Member").requestedAt(Instant.now()).build();
    }

    // --- updateStatus: gated path ---

    @Test
    @DisplayName("gated Member completion creates a completion approval instead and returns the job unchanged")
    void updateStatus_shouldCreateCompletionApproval_whenGatedMember() {
        ProjectMemberModel caller = givenStatusChange(memberId, ProjectMemberRole.MEMBER);
        when(addonAccessChecker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).thenReturn(true);
        when(approvalService.createCompletionApproval(project, job, caller, "Done"))
                .thenReturn(ApprovalModel.builder().id(pending.getId()).build());
        when(approvalRepository.findPendingCompletionByJobId(jobId)).thenReturn(Optional.of(pending));

        JobStatusUpdateResult result = jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, "Done",
                memberId);

        assertThat(result.isCompletionRequested()).isTrue();
        assertThat(result.getJob().getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(result.getJob().getPendingCompletionApproval()).isSameAs(pending);
        verify(jobRepository, never()).save(any());
        verifyNoInteractions(jobStatusHistoryRepository, eventPublisher, completionApprovalCanceller);
    }

    @Test
    @DisplayName("gated Member: an illegal transition is rejected before any approval is created")
    void updateStatus_shouldValidateFirst_whenGatedMember() {
        job.setStatus(JobStatus.NEW);
        givenStatusChange(memberId, ProjectMemberRole.MEMBER);
        when(addonAccessChecker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).thenReturn(true);

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, null, memberId))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(approvalService);
    }

    @Test
    @DisplayName("gated Member: an already pending completion approval propagates as 409")
    void updateStatus_shouldPropagateConflict_whenAlreadyPending() {
        ProjectMemberModel caller = givenStatusChange(memberId, ProjectMemberRole.MEMBER);
        when(addonAccessChecker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).thenReturn(true);
        when(approvalService.createCompletionApproval(project, job, caller, null))
                .thenThrow(new ConflictException(ErrorMessages.Approval.COMPLETION_ALREADY_PENDING));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, null, memberId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Approval.COMPLETION_ALREADY_PENDING);
        verify(jobRepository, never()).save(any());
    }

    // --- updateStatus: gate not enforced ---

    @Test
    @DisplayName("gate on but APPROVALS not usable for writes (missing add-on or PAST_DUE): Member completes directly")
    void updateStatus_shouldCompleteDirectly_whenAddonNotUsable() {
        ProjectMemberModel caller = givenStatusChange(memberId, ProjectMemberRole.MEMBER);
        when(addonAccessChecker.isAddonUsableForWrites(orgId, AddonCode.APPROVALS)).thenReturn(false);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobStatusUpdateResult result = jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, "Done",
                memberId);

        assertThat(result.isCompletionRequested()).isFalse();
        assertThat(result.getJob().getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(result.getJob().getPendingCompletionApproval()).isNull();
        verifyNoInteractions(approvalService);
        // ADR-0052 add-on lapsed: the Member's own pending request is cancelled
        verify(completionApprovalCanceller).cancelOnDirectCompletion(job, caller);
    }

    @Test
    @DisplayName("ungated project: Member completes directly and the add-on isn't checked")
    void updateStatus_shouldCompleteDirectly_whenUngated() {
        project.setRequireCompletionApproval(false);
        givenStatusChange(memberId, ProjectMemberRole.MEMBER);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobStatusUpdateResult result = jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, null,
                memberId);

        assertThat(result.isCompletionRequested()).isFalse();
        assertThat(result.getJob().getStatus()).isEqualTo(JobStatus.COMPLETED);
        verifyNoInteractions(addonAccessChecker, approvalService);
    }

    @Test
    @DisplayName("gated project: an Owner completes directly")
    void updateStatus_shouldCompleteDirectly_whenOwner() {
        UUID ownerId = UUID.randomUUID();
        ProjectMemberModel caller = givenStatusChange(ownerId, ProjectMemberRole.OWNER);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobStatusUpdateResult result = jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, null,
                ownerId);

        assertThat(result.isCompletionRequested()).isFalse();
        assertThat(result.getJob().getStatus()).isEqualTo(JobStatus.COMPLETED);
        verifyNoInteractions(addonAccessChecker, approvalService);
        // ADR-0052: completing directly cancels a pending completion request
        verify(completionApprovalCanceller).cancelOnDirectCompletion(job, caller);
    }

    @Test
    @DisplayName("gated project: an Admin completes directly")
    void updateStatus_shouldCompleteDirectly_whenAdmin() {
        UUID adminId = UUID.randomUUID();
        givenStatusChange(adminId, ProjectMemberRole.ADMIN);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JobStatusUpdateResult result = jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, null,
                adminId);

        assertThat(result.isCompletionRequested()).isFalse();
        verifyNoInteractions(addonAccessChecker, approvalService);
    }

    @Test
    @DisplayName("gated project: a Member's other transitions (e.g. Blocked) aren't gated and keep the pending request")
    void updateStatus_shouldNotGate_whenNotCompleting() {
        givenStatusChange(memberId, ProjectMemberRole.MEMBER);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(blockReasonService.findOrCreate(projectId, "Waiting"))
                .thenReturn(BlockReasonModel.builder().id(UUID.randomUUID()).reason("Waiting")
                        .build());
        when(approvalRepository.findPendingCompletionByJobId(jobId)).thenReturn(Optional.of(pending));

        JobStatusUpdateResult result = jobService.updateStatus(projectId, jobId, JobStatus.BLOCKED, "Waiting", null,
                memberId);

        assertThat(result.isCompletionRequested()).isFalse();
        assertThat(result.getJob().getStatus()).isEqualTo(JobStatus.BLOCKED);
        assertThat(result.getJob().getPendingCompletionApproval()).isSameAs(pending);
        verifyNoInteractions(addonAccessChecker, approvalService, completionApprovalCanceller);
    }

    // --- Reads: pendingCompletionApproval ---

    @Test
    @DisplayName("list attaches pending completion approvals with one batched query")
    void list_shouldAttachPendingCompletionApprovals() {
        UUID ownerId = UUID.randomUUID();
        JobModel other = JobModel.builder().id(UUID.randomUUID()).projectId(projectId).status(JobStatus.NEW).build();
        givenOrgAndProject(ownerId, ProjectMemberRole.OWNER);
        when(jobRepository.findByFilters(projectId, null, null, null, null, null)).thenReturn(List.of(job, other));
        when(approvalRepository.findPendingCompletionByJobIds(List.of(jobId, other.getId())))
                .thenReturn(List.of(pending));

        List<JobModel> jobs = jobService.list(projectId, ownerId, null, null, null, null);

        assertThat(jobs.get(0).getPendingCompletionApproval()).isSameAs(pending);
        assertThat(jobs.get(1).getPendingCompletionApproval()).isNull();
        verify(approvalRepository, never()).findPendingCompletionByJobId(any());
    }

    @Test
    @DisplayName("getById attaches the job's pending completion approval")
    void getById_shouldAttachPendingCompletionApproval() {
        givenOrgAndProject(memberId, ProjectMemberRole.MEMBER);
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(approvalRepository.findPendingCompletionByJobId(jobId)).thenReturn(Optional.of(pending));

        JobModel result = jobService.getById(projectId, jobId, memberId);

        assertThat(result.getPendingCompletionApproval()).isSameAs(pending);
    }

    // --- Helpers ---

    private ProjectMemberModel givenStatusChange(UUID callerId, ProjectMemberRole role) {
        ProjectMemberModel caller = givenOrgAndProject(callerId, role);
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        return caller;
    }

    private ProjectMemberModel givenOrgAndProject(UUID callerId, ProjectMemberRole role) {
        ProjectMemberModel caller = ProjectMemberModel.builder().projectId(projectId).userId(callerId).role(role)
                .build();
        when(organisationRepository.findByMember(callerId))
                .thenReturn(Optional.of(OrganisationModel.builder().id(orgId).name("Org").slug("ORG").build()));
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId)).thenReturn(Optional.of(caller));
        return caller;
    }
}
