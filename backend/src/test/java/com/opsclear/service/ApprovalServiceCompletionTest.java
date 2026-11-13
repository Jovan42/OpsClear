package com.opsclear.service;

import com.opsclear.event.ApprovalDecidedEvent;
import com.opsclear.event.ApprovalRequestedEvent;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.ApprovalKind;
import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalService: completion approvals (ADR-0052)")
class ApprovalServiceCompletionTest {

    @Mock private ApprovalRepository approvalRepository;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private JobStatusTransitioner jobStatusTransitioner;

    private ApprovalService approvalService;

    private UUID projectId;
    private UUID jobId;
    private UUID memberId;
    private UUID ownerId;
    private UUID approvalId;
    private ProjectModel project;
    private JobModel job;
    private ProjectMemberModel member;
    private ProjectMemberModel owner;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(approvalRepository, jobRepository, projectRepository,
                projectMemberRepository, eventPublisher, jobStatusTransitioner);

        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        approvalId = UUID.randomUUID();
        project = ProjectModel.builder().id(projectId).name("Gated").ownerId(ownerId)
                .requireCompletionApproval(true).build();
        job = JobModel.builder().id(jobId).projectId(projectId).title("Install panel")
                .status(JobStatus.IN_PROGRESS).assignedTo(memberId).build();
        member = ProjectMemberModel.builder().projectId(projectId).userId(memberId)
                .role(ProjectMemberRole.MEMBER).build();
        owner = ProjectMemberModel.builder().projectId(projectId).userId(ownerId)
                .role(ProjectMemberRole.OWNER).build();
    }

    // --- createCompletionApproval ---

    @Test
    @DisplayName("creates a COMPLETION approval with the stripped message and publishes APPROVAL_REQUESTED with kind")
    void createCompletionApproval_shouldInsertWithMessage() {
        ApprovalModel created = completionApproval(ApprovalStatus.PENDING, "Done, please check");
        when(approvalRepository.insert(jobId, memberId, "Done, please check", ApprovalKind.COMPLETION))
                .thenReturn(created);

        ApprovalModel result = approvalService.createCompletionApproval(project, job, member, "  Done, please check ");

        assertThat(result).isSameAs(created);
        verify(eventPublisher).publishEvent(new ApprovalRequestedEvent(approvalId, projectId, jobId, memberId,
                "Done, please check", ApprovalKind.COMPLETION));
    }

    @Test
    @DisplayName("uses the default description when there is no message")
    void createCompletionApproval_shouldUseDefault_whenMessageNull() {
        when(approvalRepository.insert(jobId, memberId, ApprovalService.DEFAULT_COMPLETION_DESCRIPTION,
                ApprovalKind.COMPLETION))
                .thenReturn(completionApproval(ApprovalStatus.PENDING, ApprovalService.DEFAULT_COMPLETION_DESCRIPTION));

        approvalService.createCompletionApproval(project, job, member, null);

        verify(approvalRepository).insert(jobId, memberId, "Requesting to complete this job", ApprovalKind.COMPLETION);
    }

    @Test
    @DisplayName("uses the default description when the message is blank")
    void createCompletionApproval_shouldUseDefault_whenMessageBlank() {
        when(approvalRepository.insert(jobId, memberId, ApprovalService.DEFAULT_COMPLETION_DESCRIPTION,
                ApprovalKind.COMPLETION))
                .thenReturn(completionApproval(ApprovalStatus.PENDING, ApprovalService.DEFAULT_COMPLETION_DESCRIPTION));

        approvalService.createCompletionApproval(project, job, member, "   ");

        verify(approvalRepository).insert(jobId, memberId, ApprovalService.DEFAULT_COMPLETION_DESCRIPTION,
                ApprovalKind.COMPLETION);
    }

    @Test
    @DisplayName("unique-index violation (already pending or a concurrent request) becomes 409 and publishes nothing")
    void createCompletionApproval_shouldThrowConflict_whenAlreadyPending() {
        when(approvalRepository.insert(any(), any(), any(), eq(ApprovalKind.COMPLETION)))
                .thenThrow(new DuplicateKeyException("uq_approvals_one_pending_completion"));

        assertThatThrownBy(() -> approvalService.createCompletionApproval(project, job, member, null))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Approval.COMPLETION_ALREADY_PENDING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("a completed project rejects the request")
    void createCompletionApproval_shouldThrowConflict_whenProjectCompleted() {
        project.setStatus(ProjectStatus.COMPLETED);

        assertThatThrownBy(() -> approvalService.createCompletionApproval(project, job, member, null))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Project.COMPLETED_NO_MUTATIONS);
        verifyNoInteractions(approvalRepository);
    }

    @Test
    @DisplayName("a Member not assigned to the job can't request completion")
    void createCompletionApproval_shouldThrowForbidden_whenMemberNotAssigned() {
        job.setAssignedTo(UUID.randomUUID());

        assertThatThrownBy(() -> approvalService.createCompletionApproval(project, job, member, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Approval.ACCESS_DENIED_NOT_ASSIGNED);
        verifyNoInteractions(approvalRepository);
    }

    // --- decide: completion approvals ---

    @Test
    @DisplayName("approving while the job is In Progress completes it as the approver and marks it applied")
    void decide_shouldCompleteJob_whenApprovedAndInProgress() {
        givenDecision(ApprovalStatus.APPROVED);
        ApprovalModel decided = completionApproval(ApprovalStatus.APPROVED, "Done");
        decided.setCompletionApplied(true);
        whenReReadReturn(decided);

        ApprovalModel result = approvalService.decide(projectId, jobId, approvalId, ApprovalStatus.APPROVED, null,
                ownerId);

        assertThat(result.getCompletionApplied()).isTrue();
        verify(jobStatusTransitioner).transition(job, JobStatus.COMPLETED, null, owner);
        verify(approvalRepository).updateCompletionOutcome(approvalId, true, null);
        verify(eventPublisher).publishEvent(new ApprovalDecidedEvent(approvalId, projectId, jobId, ownerId,
                memberId, ApprovalStatus.APPROVED, null, ApprovalKind.COMPLETION, true, null));
    }

    @Test
    @DisplayName("approving while the job is Blocked records 'not applied' with reason BLOCKED and leaves the job")
    void decide_shouldNotApply_whenApprovedAndBlocked() {
        job.setStatus(JobStatus.BLOCKED);
        givenDecision(ApprovalStatus.APPROVED);
        ApprovalModel decided = completionApproval(ApprovalStatus.APPROVED, "Done");
        decided.setCompletionApplied(false);
        decided.setCompletionNotAppliedReason("BLOCKED");
        whenReReadReturn(decided);

        ApprovalModel result = approvalService.decide(projectId, jobId, approvalId, ApprovalStatus.APPROVED, null,
                ownerId);

        assertThat(result.getCompletionApplied()).isFalse();
        assertThat(result.getCompletionNotAppliedReason()).isEqualTo("BLOCKED");
        verifyNoInteractions(jobStatusTransitioner);
        verify(approvalRepository).updateCompletionOutcome(approvalId, false, "BLOCKED");
        verify(eventPublisher).publishEvent(new ApprovalDecidedEvent(approvalId, projectId, jobId, ownerId,
                memberId, ApprovalStatus.APPROVED, null, ApprovalKind.COMPLETION, false, "BLOCKED"));
    }

    @Test
    @DisplayName("approving when the job is already Completed records 'not applied' with reason COMPLETED")
    void decide_shouldNotApply_whenApprovedAndCompleted() {
        job.setStatus(JobStatus.COMPLETED);
        givenDecision(ApprovalStatus.APPROVED);
        whenReReadReturn(completionApproval(ApprovalStatus.APPROVED, "Done"));

        approvalService.decide(projectId, jobId, approvalId, ApprovalStatus.APPROVED, null, ownerId);

        verifyNoInteractions(jobStatusTransitioner);
        verify(approvalRepository).updateCompletionOutcome(approvalId, false, "COMPLETED");
    }

    @Test
    @DisplayName("rejecting a completion approval leaves the job unchanged and records no outcome")
    void decide_shouldNotTouchJob_whenRejected() {
        givenDecision(ApprovalStatus.REJECTED);
        ApprovalModel decided = completionApproval(ApprovalStatus.REJECTED, "Done");
        decided.setComment("Not yet");
        whenReReadReturn(decided);

        approvalService.decide(projectId, jobId, approvalId, ApprovalStatus.REJECTED, "Not yet", ownerId);

        verifyNoInteractions(jobStatusTransitioner);
        verify(approvalRepository, never()).updateCompletionOutcome(any(), anyBoolean(), any());
        verify(eventPublisher).publishEvent(new ApprovalDecidedEvent(approvalId, projectId, jobId, ownerId,
                memberId, ApprovalStatus.REJECTED, "Not yet", ApprovalKind.COMPLETION, null, null));
    }

    @Test
    @DisplayName("decide reads the job with a row lock and returns 404 when it's gone")
    void decide_shouldThrowNotFound_whenJobMissing() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.decide(projectId, jobId, approvalId, ApprovalStatus.APPROVED, null,
                ownerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.Job.NOT_FOUND);
        verifyNoInteractions(approvalRepository);
    }

    // --- Helpers ---

    private void givenDecision(ApprovalStatus status) {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.of(owner));
        when(approvalRepository.updateDecision(eq(approvalId), eq(ownerId), eq(status), any(), any(Instant.class)))
                .thenReturn(1);
    }

    private void whenReReadReturn(ApprovalModel decided) {
        when(approvalRepository.findByIdAndJobId(approvalId, jobId))
                .thenReturn(Optional.of(completionApproval(ApprovalStatus.PENDING, "Done")))
                .thenReturn(Optional.of(decided));
    }

    private ApprovalModel completionApproval(ApprovalStatus status, String description) {
        return ApprovalModel.builder()
                .id(approvalId)
                .jobId(jobId)
                .requesterId(memberId)
                .description(description)
                .status(status)
                .kind(ApprovalKind.COMPLETION)
                .requestedAt(Instant.now())
                .build();
    }
}
