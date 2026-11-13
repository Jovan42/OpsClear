package com.opsclear.service;

import com.opsclear.event.ApprovalDecidedEvent;
import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.model.ApprovalKind;
import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.ApprovalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompletionApprovalCanceller: withdrawal and auto-cancel of completion approvals (ADR-0052)")
class CompletionApprovalCancellerTest {

    @Mock private ApprovalRepository approvalRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private CompletionApprovalCanceller canceller;

    private UUID projectId;
    private UUID jobId;
    private UUID approvalId;
    private UUID requesterId;
    private UUID ownerId;
    private JobModel job;
    private ApprovalModel pending;
    private ProjectMemberModel owner;
    private ProjectMemberModel requester;

    @BeforeEach
    void setUp() {
        canceller = new CompletionApprovalCanceller(approvalRepository, eventPublisher);

        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        approvalId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        job = JobModel.builder().id(jobId).projectId(projectId).status(JobStatus.COMPLETED).build();
        pending = approval(ApprovalStatus.PENDING, null, null);
        owner = ProjectMemberModel.builder().projectId(projectId).userId(ownerId).userName("Olga Owner")
                .role(ProjectMemberRole.OWNER).build();
        requester = ProjectMemberModel.builder().projectId(projectId).userId(requesterId).userName("Max Member")
                .role(ProjectMemberRole.MEMBER).build();
    }

    // --- withdraw ---

    @Test
    @DisplayName("withdraw by the requester cancels it with no comment and notifies nobody")
    void withdraw_shouldCancelWithoutNotification_whenRequester() {
        ApprovalModel cancelled = approval(ApprovalStatus.CANCELLED, requesterId, null);
        when(approvalRepository.cancelPendingCompletion(eq(approvalId), eq(requesterId), isNull(),
                any(Instant.class))).thenReturn(1);
        when(approvalRepository.findByIdAndJobId(approvalId, jobId)).thenReturn(Optional.of(cancelled));

        ApprovalModel result = canceller.withdraw(projectId, pending, requesterId);

        assertThat(result).isSameAs(cancelled);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("withdraw by someone else publishes APPROVAL_DECIDED with decision CANCELLED and kind COMPLETION")
    void withdraw_shouldPublishCancelled_whenNotRequester() {
        ApprovalModel cancelled = approval(ApprovalStatus.CANCELLED, ownerId, null);
        when(approvalRepository.cancelPendingCompletion(eq(approvalId), eq(ownerId), isNull(), any(Instant.class)))
                .thenReturn(1);
        when(approvalRepository.findByIdAndJobId(approvalId, jobId)).thenReturn(Optional.of(cancelled));

        canceller.withdraw(projectId, pending, ownerId);

        verify(eventPublisher).publishEvent(new ApprovalDecidedEvent(approvalId, projectId, jobId, ownerId,
                requesterId, ApprovalStatus.CANCELLED, null, ApprovalKind.COMPLETION, null, null));
    }

    @Test
    @DisplayName("withdraw of an approval that's no longer pending is a 409 and publishes nothing")
    void withdraw_shouldThrowConflict_whenNoLongerPending() {
        when(approvalRepository.cancelPendingCompletion(eq(approvalId), eq(ownerId), isNull(), any(Instant.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> canceller.withdraw(projectId, pending, ownerId))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Approval.ALREADY_DECIDED);
        verify(approvalRepository, never()).findByIdAndJobId(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    // --- Auto-cancel ---

    @Test
    @DisplayName("direct completion by an Owner cancels the pending request with 'Completed directly by <name>'")
    void cancelOnDirectCompletion_shouldCancelAndNotify_whenOwner() {
        String comment = "Completed directly by Olga Owner";
        when(approvalRepository.cancelPendingCompletionByJobId(eq(jobId), eq(ownerId), eq(comment),
                any(Instant.class))).thenReturn(Optional.of(approval(ApprovalStatus.CANCELLED, ownerId, comment)));

        canceller.cancelOnDirectCompletion(job, owner);

        verify(eventPublisher).publishEvent(new ApprovalDecidedEvent(approvalId, projectId, jobId, ownerId,
                requesterId, ApprovalStatus.CANCELLED, comment, ApprovalKind.COMPLETION, null, null));
    }

    @Test
    @DisplayName("direct completion by the requester (add-on lapsed) cancels their own request without notifying")
    void cancelOnDirectCompletion_shouldCancelSilently_whenRequester() {
        String comment = "Completed directly by Max Member";
        when(approvalRepository.cancelPendingCompletionByJobId(eq(jobId), eq(requesterId), eq(comment),
                any(Instant.class))).thenReturn(Optional.of(approval(ApprovalStatus.CANCELLED, requesterId, comment)));

        canceller.cancelOnDirectCompletion(job, requester);

        verify(approvalRepository).cancelPendingCompletionByJobId(eq(jobId), eq(requesterId), eq(comment),
                any(Instant.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("direct completion with no pending request does nothing else")
    void cancelOnDirectCompletion_shouldDoNothing_whenNonePending() {
        when(approvalRepository.cancelPendingCompletionByJobId(eq(jobId), eq(ownerId), any(), any(Instant.class)))
                .thenReturn(Optional.empty());

        canceller.cancelOnDirectCompletion(job, owner);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("deleting the job cancels the pending request with 'Job deleted by <name>'")
    void cancelOnDeletion_shouldCancelWithComment() {
        String comment = "Job deleted by Olga Owner";
        when(approvalRepository.cancelPendingCompletionByJobId(eq(jobId), eq(ownerId), eq(comment),
                any(Instant.class))).thenReturn(Optional.of(approval(ApprovalStatus.CANCELLED, ownerId, comment)));

        canceller.cancelOnDeletion(job, owner);

        verify(eventPublisher).publishEvent(new ApprovalDecidedEvent(approvalId, projectId, jobId, ownerId,
                requesterId, ApprovalStatus.CANCELLED, comment, ApprovalKind.COMPLETION, null, null));
    }

    private ApprovalModel approval(ApprovalStatus status, UUID approverId, String comment) {
        return ApprovalModel.builder()
                .id(approvalId)
                .jobId(jobId)
                .requesterId(requesterId)
                .approverId(approverId)
                .status(status)
                .comment(comment)
                .kind(ApprovalKind.COMPLETION)
                .requestedAt(Instant.now())
                .build();
    }
}
