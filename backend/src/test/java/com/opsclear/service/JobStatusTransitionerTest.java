package com.opsclear.service;

import com.opsclear.event.BlockerClearedEvent;
import com.opsclear.event.JobBlockedEvent;
import com.opsclear.event.JobStatusChangedEvent;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.BlockReasonModel;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobRelationshipType;
import com.opsclear.model.JobStatus;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobStatusTransitioner (ADR-0052, JOB-285)")
class JobStatusTransitionerTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Mock private JobRelationshipRepository jobRelationshipRepository;
    @Mock private BlockReasonService blockReasonService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private JobStatusTransitioner transitioner;

    private UUID projectId;
    private UUID jobId;
    private UUID ownerId;
    private UUID assigneeId;
    private ProjectMemberModel owner;
    private ProjectMemberModel assignee;

    @BeforeEach
    void setUp() {
        transitioner = new JobStatusTransitioner(jobRepository, jobStatusHistoryRepository,
                jobRelationshipRepository, blockReasonService, eventPublisher);
        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        owner = member(ownerId, ProjectMemberRole.OWNER);
        assignee = member(assigneeId, ProjectMemberRole.MEMBER);
    }

    // --- Helpers ---

    private ProjectMemberModel member(UUID userId, ProjectMemberRole role) {
        return ProjectMemberModel.builder().projectId(projectId).userId(userId).role(role).build();
    }

    private JobModel job(JobStatus status) {
        return JobModel.builder().id(jobId).projectId(projectId).title("Fix login").status(status)
                .assignedTo(assigneeId).build();
    }

    private JobModel blockedJob() {
        JobModel job = job(JobStatus.BLOCKED);
        job.setBlockedBy(ownerId);
        job.setBlockedReasonId(UUID.randomUUID());
        job.setBlockedAt(Instant.parse("2026-11-01T00:00:00Z"));
        return job;
    }

    private void givenSaveReturnsArgument() {
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenBlockReason(String reason) {
        when(blockReasonService.findOrCreate(projectId, reason))
                .thenReturn(BlockReasonModel.builder().id(UUID.randomUUID()).projectId(projectId)
                        .reason(reason.strip()).build());
    }

    private List<Object> publishedEvents(int count) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(count)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    static Stream<Arguments> illegalTransitions() {
        List<String> legal = List.of("NEW>IN_PROGRESS", "IN_PROGRESS>COMPLETED", "IN_PROGRESS>BLOCKED",
                "BLOCKED>IN_PROGRESS", "COMPLETED>IN_PROGRESS");
        return Stream.of(JobStatus.values())
                .flatMap(from -> Stream.of(JobStatus.values()).map(to -> Arguments.of(from, to)))
                .filter(a -> !legal.contains(a.get()[0] + ">" + a.get()[1]));
    }

    // --- validate: legal and illegal transitions ---

    @ParameterizedTest(name = "{0} → {1} is legal for an Owner")
    @CsvSource({"NEW, IN_PROGRESS", "IN_PROGRESS, COMPLETED", "IN_PROGRESS, BLOCKED", "BLOCKED, IN_PROGRESS",
        "COMPLETED, IN_PROGRESS"})
    @DisplayName("validate accepts every legal transition for an Owner")
    void validate_shouldAccept_legalTransitions(JobStatus from, JobStatus to) {
        assertThatCode(() -> transitioner.validate(job(from), to, owner)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} → {1} is rejected")
    @MethodSource("illegalTransitions")
    @DisplayName("validate rejects every illegal transition with 400, even for an Owner")
    void validate_shouldReject_illegalTransitions(JobStatus from, JobStatus to) {
        assertThatThrownBy(() -> transitioner.validate(job(from), to, owner))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorMessages.Job.INVALID_TRANSITION + from + " → " + to);
    }

    @ParameterizedTest(name = "assignee may move {0} → {1}")
    @CsvSource({"NEW, IN_PROGRESS", "IN_PROGRESS, COMPLETED", "IN_PROGRESS, BLOCKED", "BLOCKED, IN_PROGRESS"})
    @DisplayName("validate lets the assigned Member make every non-reopen transition")
    void validate_shouldAccept_assigneeTransitions(JobStatus from, JobStatus to) {
        assertThatCode(() -> transitioner.validate(job(from), to, assignee)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0} may reopen")
    @EnumSource(value = ProjectMemberRole.class, names = {"OWNER", "ADMIN"})
    @DisplayName("validate lets an Owner or Admin reopen a completed job they aren't assigned to")
    void validate_shouldAllowReopen_forOwnerOrAdmin(ProjectMemberRole role) {
        ProjectMemberModel caller = member(UUID.randomUUID(), role);

        assertThatCode(() -> transitioner.validate(job(JobStatus.COMPLETED), JobStatus.IN_PROGRESS, caller))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validate forbids a Member, even the assignee, from reopening a completed job")
    void validate_shouldForbidReopen_forMember() {
        assertThatThrownBy(() -> transitioner.validate(job(JobStatus.COMPLETED), JobStatus.IN_PROGRESS, assignee))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Job.ONLY_OWNER_ADMIN_CAN_REOPEN);
    }

    @Test
    @DisplayName("validate forbids a Member who isn't the assignee from changing status")
    void validate_shouldForbid_unassignedMember() {
        ProjectMemberModel other = member(UUID.randomUUID(), ProjectMemberRole.MEMBER);

        assertThatThrownBy(() -> transitioner.validate(job(JobStatus.IN_PROGRESS), JobStatus.COMPLETED, other))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Job.ONLY_OWNER_ADMIN_OR_ASSIGNEE_CAN_CHANGE_STATUS);
    }

    @Test
    @DisplayName("validate forbids a Member on an unassigned job")
    void validate_shouldForbid_memberOnUnassignedJob() {
        JobModel unassigned = job(JobStatus.NEW);
        unassigned.setAssignedTo(null);

        assertThatThrownBy(() -> transitioner.validate(unassigned, JobStatus.IN_PROGRESS, assignee))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Job.ONLY_OWNER_ADMIN_OR_ASSIGNEE_CAN_CHANGE_STATUS);
    }

    @Test
    @DisplayName("validate changes nothing")
    void validate_shouldNotTouchRepositoriesOrEvents() {
        JobModel job = job(JobStatus.IN_PROGRESS);

        transitioner.validate(job, JobStatus.COMPLETED, owner);

        assertThat(job.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        verifyNoInteractions(jobRepository, jobStatusHistoryRepository, eventPublisher, blockReasonService);
    }

    // --- transition: updates, history row, changedBy, events ---

    @Test
    @DisplayName("transition NEW → IN_PROGRESS saves the job, writes history with changedBy and publishes "
            + "JOB_STATUS_CHANGED")
    void transition_shouldStartJob() {
        givenSaveReturnsArgument();

        JobModel result = transitioner.transition(job(JobStatus.NEW), JobStatus.IN_PROGRESS, null, assignee);

        assertThat(result.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        ArgumentCaptor<JobModel> saved = ArgumentCaptor.forClass(JobModel.class);
        verify(jobRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        verify(jobStatusHistoryRepository).insert(jobId, "NEW", "IN_PROGRESS", assigneeId, null);
        assertThat(publishedEvents(1)).containsExactly(
                new JobStatusChangedEvent(jobId, projectId, assigneeId, JobStatus.NEW, JobStatus.IN_PROGRESS));
        verifyNoInteractions(blockReasonService, jobRelationshipRepository);
    }

    @Test
    @DisplayName("transition to BLOCKED records who blocked it, the reason and when, and publishes only JOB_BLOCKED")
    void transition_shouldBlockJob() {
        givenSaveReturnsArgument();
        givenBlockReason("Waiting for parts");
        Instant before = Instant.now();

        JobModel result = transitioner.transition(job(JobStatus.IN_PROGRESS), JobStatus.BLOCKED,
                "Waiting for parts", owner);

        assertThat(result.getStatus()).isEqualTo(JobStatus.BLOCKED);
        assertThat(result.getBlockedBy()).isEqualTo(ownerId);
        assertThat(result.getBlockedReasonId()).isNotNull();
        assertThat(result.getBlockedAt()).isAfterOrEqualTo(before);
        verify(jobStatusHistoryRepository).insert(jobId, "IN_PROGRESS", "BLOCKED", ownerId, "Waiting for parts");
        assertThat(publishedEvents(1)).containsExactly(
                new JobBlockedEvent(jobId, projectId, ownerId, "Waiting for parts"));
        verifyNoInteractions(jobRelationshipRepository);
    }

    @Test
    @DisplayName("transition to BLOCKED stores the stripped reason from the block reason record in history")
    void transition_shouldUseStoredBlockReason_inHistory() {
        givenSaveReturnsArgument();
        givenBlockReason("  Waiting for parts  ");

        transitioner.transition(job(JobStatus.IN_PROGRESS), JobStatus.BLOCKED, "  Waiting for parts  ", owner);

        verify(jobStatusHistoryRepository).insert(jobId, "IN_PROGRESS", "BLOCKED", ownerId, "Waiting for parts");
    }

    @ParameterizedTest(name = "reason \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("transition to BLOCKED without a reason fails with 400 and changes nothing")
    void transition_shouldRequireBlockReason(String reason) {
        JobModel job = job(JobStatus.IN_PROGRESS);

        assertThatThrownBy(() -> transitioner.transition(job, JobStatus.BLOCKED, reason, owner))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorMessages.Job.BLOCK_REASON_REQUIRED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        verifyNoInteractions(jobRepository, jobStatusHistoryRepository, eventPublisher, blockReasonService);
    }

    @Test
    @DisplayName("transition BLOCKED → IN_PROGRESS clears the blocker fields and publishes JOB_STATUS_CHANGED")
    void transition_shouldUnblockJob() {
        givenSaveReturnsArgument();

        JobModel result = transitioner.transition(blockedJob(), JobStatus.IN_PROGRESS, "ignored", assignee);

        assertThat(result.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        assertThat(result.getBlockedBy()).isNull();
        assertThat(result.getBlockedReasonId()).isNull();
        assertThat(result.getBlockedAt()).isNull();
        verify(jobStatusHistoryRepository).insert(jobId, "BLOCKED", "IN_PROGRESS", assigneeId, null);
        assertThat(publishedEvents(1)).containsExactly(
                new JobStatusChangedEvent(jobId, projectId, assigneeId, JobStatus.BLOCKED, JobStatus.IN_PROGRESS));
        verifyNoInteractions(blockReasonService);
    }

    @Test
    @DisplayName("transition to COMPLETED with no waiting jobs publishes only JOB_STATUS_CHANGED")
    void transition_shouldCompleteJob_withoutWaitingJobs() {
        givenSaveReturnsArgument();
        when(jobRelationshipRepository.findSourceJobIdsByTargetJobIdAndType(jobId, JobRelationshipType.BLOCKED_BY))
                .thenReturn(List.of());

        JobModel result = transitioner.transition(job(JobStatus.IN_PROGRESS), JobStatus.COMPLETED, null, owner);

        assertThat(result.getStatus()).isEqualTo(JobStatus.COMPLETED);
        verify(jobStatusHistoryRepository).insert(jobId, "IN_PROGRESS", "COMPLETED", ownerId, null);
        assertThat(publishedEvents(1)).containsExactly(
                new JobStatusChangedEvent(jobId, projectId, ownerId, JobStatus.IN_PROGRESS, JobStatus.COMPLETED));
    }

    @Test
    @DisplayName("transition to COMPLETED also publishes BLOCKER_CLEARED when other jobs are BLOCKED_BY it")
    void transition_shouldPublishBlockerCleared_whenJobsWaiting() {
        givenSaveReturnsArgument();
        List<UUID> waiting = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(jobRelationshipRepository.findSourceJobIdsByTargetJobIdAndType(jobId, JobRelationshipType.BLOCKED_BY))
                .thenReturn(waiting);

        transitioner.transition(job(JobStatus.IN_PROGRESS), JobStatus.COMPLETED, null, assignee);

        assertThat(publishedEvents(2)).containsExactly(
                new JobStatusChangedEvent(jobId, projectId, assigneeId, JobStatus.IN_PROGRESS, JobStatus.COMPLETED),
                new BlockerClearedEvent(jobId, projectId, assigneeId, waiting));
    }

    @Test
    @DisplayName("transition COMPLETED → IN_PROGRESS (reopen) by an Admin is attributed to the Admin")
    void transition_shouldReopenJob_attributedToChangedBy() {
        givenSaveReturnsArgument();
        UUID adminId = UUID.randomUUID();

        JobModel result = transitioner.transition(job(JobStatus.COMPLETED), JobStatus.IN_PROGRESS, null,
                member(adminId, ProjectMemberRole.ADMIN));

        assertThat(result.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        verify(jobStatusHistoryRepository).insert(jobId, "COMPLETED", "IN_PROGRESS", adminId, null);
        assertThat(publishedEvents(1)).containsExactly(
                new JobStatusChangedEvent(jobId, projectId, adminId, JobStatus.COMPLETED, JobStatus.IN_PROGRESS));
        verifyNoInteractions(jobRelationshipRepository);
    }

    @Test
    @DisplayName("transition returns what the repository saved")
    void transition_shouldReturnSavedJob() {
        JobModel persisted = job(JobStatus.IN_PROGRESS);
        when(jobRepository.save(any())).thenReturn(persisted);

        JobModel result = transitioner.transition(job(JobStatus.NEW), JobStatus.IN_PROGRESS, null, owner);

        assertThat(result).isSameAs(persisted);
    }

    @Test
    @DisplayName("an illegal transition fails with 400 and changes nothing")
    void transition_shouldNotPersist_whenIllegal() {
        JobModel job = job(JobStatus.NEW);

        assertThatThrownBy(() -> transitioner.transition(job, JobStatus.COMPLETED, null, owner))
                .isInstanceOf(BadRequestException.class);
        assertThat(job.getStatus()).isEqualTo(JobStatus.NEW);
        verify(jobRepository, never()).save(any());
        verify(jobStatusHistoryRepository, never()).insert(any(), anyString(), anyString(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("a forbidden transition fails with 403 and changes nothing")
    void transition_shouldNotPersist_whenForbidden() {
        JobModel job = job(JobStatus.IN_PROGRESS);
        ProjectMemberModel other = member(UUID.randomUUID(), ProjectMemberRole.MEMBER);

        assertThatThrownBy(() -> transitioner.transition(job, JobStatus.COMPLETED, null, other))
                .isInstanceOf(ForbiddenException.class);
        assertThat(job.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
        verifyNoInteractions(jobRepository, jobStatusHistoryRepository, eventPublisher);
    }
}
