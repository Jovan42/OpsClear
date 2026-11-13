package com.opsclear.service;

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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalService: withdrawing a completion approval (ADR-0052)")
class ApprovalServiceCancelTest {

    @Mock private ApprovalRepository approvalRepository;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private JobStatusTransitioner jobStatusTransitioner;
    @Mock private CompletionApprovalCanceller completionApprovalCanceller;

    private ApprovalService approvalService;

    private UUID projectId;
    private UUID jobId;
    private UUID approvalId;
    private UUID requesterId;
    private ProjectModel project;
    private JobModel job;
    private ApprovalModel approval;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(approvalRepository, jobRepository, projectRepository,
                projectMemberRepository, eventPublisher, jobStatusTransitioner, completionApprovalCanceller);

        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        approvalId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        project = ProjectModel.builder().id(projectId).name("Gated").requireCompletionApproval(true).build();
        job = JobModel.builder().id(jobId).projectId(projectId).title("Install panel")
                .status(JobStatus.IN_PROGRESS).assignedTo(requesterId).build();
        approval = ApprovalModel.builder().id(approvalId).jobId(jobId).requesterId(requesterId)
                .status(ApprovalStatus.PENDING).kind(ApprovalKind.COMPLETION).requestedAt(Instant.now()).build();
    }

    @Test
    @DisplayName("the requester (a Member) withdraws their own request: locks the job, then cancels the approval")
    void cancel_shouldWithdraw_whenRequester() {
        givenCaller(requesterId, ProjectMemberRole.MEMBER);
        givenJobAndApproval();
        ApprovalModel cancelled = ApprovalModel.builder().id(approvalId).status(ApprovalStatus.CANCELLED).build();
        when(completionApprovalCanceller.withdraw(projectId, approval, requesterId)).thenReturn(cancelled);

        ApprovalModel result = approvalService.cancel(projectId, jobId, approvalId, requesterId);

        assertThat(result).isSameAs(cancelled);
        InOrder order = inOrder(jobRepository, completionApprovalCanceller);
        order.verify(jobRepository).findByIdForUpdate(jobId);
        order.verify(completionApprovalCanceller).withdraw(projectId, approval, requesterId);
        verifyNoInteractions(jobStatusTransitioner, eventPublisher);
    }

    @Test
    @DisplayName("an Owner withdraws someone else's request")
    void cancel_shouldWithdraw_whenOwner() {
        UUID ownerId = UUID.randomUUID();
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenJobAndApproval();

        approvalService.cancel(projectId, jobId, approvalId, ownerId);

        verify(completionApprovalCanceller).withdraw(projectId, approval, ownerId);
    }

    @Test
    @DisplayName("an Admin withdraws someone else's request")
    void cancel_shouldWithdraw_whenAdmin() {
        UUID adminId = UUID.randomUUID();
        givenCaller(adminId, ProjectMemberRole.ADMIN);
        givenJobAndApproval();

        approvalService.cancel(projectId, jobId, approvalId, adminId);

        verify(completionApprovalCanceller).withdraw(projectId, approval, adminId);
    }

    @Test
    @DisplayName("another Member gets 403 before the job is locked")
    void cancel_shouldThrowForbidden_whenOtherMember() {
        UUID otherId = UUID.randomUUID();
        givenCaller(otherId, ProjectMemberRole.MEMBER);
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(approvalRepository.findByIdAndJobId(approvalId, jobId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> approvalService.cancel(projectId, jobId, approvalId, otherId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Approval.CANCEL_DENIED);
        verify(jobRepository, never()).findByIdForUpdate(jobId);
        verifyNoInteractions(completionApprovalCanceller);
    }

    @Test
    @DisplayName("a manual approval can't be withdrawn (409), even by its requester")
    void cancel_shouldThrowConflict_whenManual() {
        approval.setKind(ApprovalKind.MANUAL);
        givenCaller(requesterId, ProjectMemberRole.MEMBER);
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(approvalRepository.findByIdAndJobId(approvalId, jobId)).thenReturn(Optional.of(approval));

        assertThatThrownBy(() -> approvalService.cancel(projectId, jobId, approvalId, requesterId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Approval.ONLY_COMPLETION_CANCELLABLE);
        verify(jobRepository, never()).findByIdForUpdate(jobId);
        verifyNoInteractions(completionApprovalCanceller);
    }

    @Test
    @DisplayName("an already decided approval: the canceller's 409 propagates")
    void cancel_shouldPropagateConflict_whenAlreadyDecided() {
        givenCaller(requesterId, ProjectMemberRole.MEMBER);
        givenJobAndApproval();
        when(completionApprovalCanceller.withdraw(projectId, approval, requesterId))
                .thenThrow(new ConflictException(ErrorMessages.Approval.ALREADY_DECIDED));

        assertThatThrownBy(() -> approvalService.cancel(projectId, jobId, approvalId, requesterId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Approval.ALREADY_DECIDED);
    }

    @Test
    @DisplayName("a completed project rejects the withdrawal with 409 as today")
    void cancel_shouldThrowConflict_whenProjectCompleted() {
        project.setStatus(ProjectStatus.COMPLETED);
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> approvalService.cancel(projectId, jobId, approvalId, requesterId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Project.COMPLETED_NO_MUTATIONS);
        verifyNoInteractions(approvalRepository, jobRepository, completionApprovalCanceller);
    }

    @Test
    @DisplayName("a non-member gets 403")
    void cancel_shouldThrowForbidden_whenNotMember() {
        UUID strangerId = UUID.randomUUID();
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, strangerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.cancel(projectId, jobId, approvalId, strangerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Member.NOT_A_MEMBER);
    }

    @Test
    @DisplayName("an unknown approval gets 404")
    void cancel_shouldThrowNotFound_whenApprovalMissing() {
        givenCaller(requesterId, ProjectMemberRole.MEMBER);
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(approvalRepository.findByIdAndJobId(approvalId, jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> approvalService.cancel(projectId, jobId, approvalId, requesterId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.Approval.NOT_FOUND);
    }

    @Test
    @DisplayName("a job in another project gets 404")
    void cancel_shouldThrowNotFound_whenJobInOtherProject() {
        job.setProjectId(UUID.randomUUID());
        givenCaller(requesterId, ProjectMemberRole.MEMBER);
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> approvalService.cancel(projectId, jobId, approvalId, requesterId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.Job.NOT_FOUND);
        verifyNoInteractions(approvalRepository);
    }

    // --- Helpers ---

    private void givenCaller(UUID callerId, ProjectMemberRole role) {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, callerId))
                .thenReturn(Optional.of(ProjectMemberModel.builder().projectId(projectId).userId(callerId)
                        .role(role).build()));
    }

    private void givenJobAndApproval() {
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(approvalRepository.findByIdAndJobId(approvalId, jobId)).thenReturn(Optional.of(approval));
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
    }
}
