package com.opsclear.service;

import com.opsclear.dto.CreateJobRequest;
import com.opsclear.dto.UpdateJobRequest;
import com.opsclear.event.BlockerClearedEvent;
import com.opsclear.event.JobAssignedEvent;
import com.opsclear.event.JobBlockedEvent;
import com.opsclear.event.JobDeletedEvent;
import com.opsclear.event.JobStatusChangedEvent;
import com.opsclear.event.JobUpdatedEvent;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.BlockReasonModel;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobRelationshipType;
import com.opsclear.model.JobStatus;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobService notification events (ADR-0051, JOB-277)")
class JobServiceNotificationEventsTest {

    private static final Instant DEADLINE = Instant.parse("2026-11-20T00:00:00Z");
    private static final Instant NEW_DEADLINE = Instant.parse("2026-11-27T00:00:00Z");

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

    private JobService jobService;

    private UUID projectId;
    private UUID jobId;
    private UUID ownerId;
    private UUID assigneeId;
    private JobModel job;

    @BeforeEach
    void setUp() {
        // Real transitioner over the same mocks: status events are published from it (JOB-285)
        JobStatusTransitioner transitioner = new JobStatusTransitioner(jobRepository, jobStatusHistoryRepository,
                jobRelationshipRepository, blockReasonService, eventPublisher);
        jobService = new JobService(jobRepository, jobRelationshipRepository, jobLinkRepository,
                jobStatusHistoryRepository, projectRepository, projectMemberRepository, userRepository,
                milestoneRepository, jobTypeRepository, organisationRepository, friendlyIdService, eventPublisher,
                transitioner, approvalRepository, approvalService, addonAccessChecker);
        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        job = JobModel.builder().id(jobId).projectId(projectId).title("Fix login").status(JobStatus.IN_PROGRESS)
                .assignedTo(assigneeId).deadline(DEADLINE).priority(JobPriority.MEDIUM).build();

        OrganisationModel org = OrganisationModel.builder().id(UUID.randomUUID()).name("Org").slug("ORG").build();
        when(organisationRepository.findByMember(any())).thenReturn(Optional.of(org));
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
                .thenReturn(Optional.of(ProjectModel.builder().id(projectId).name("Project").build()));
    }

    // --- Helpers ---

    private void givenCaller(UUID userId, ProjectMemberRole role) {
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, userId)).thenReturn(Optional.of(
                ProjectMemberModel.builder().projectId(projectId).userId(userId).role(role).build()));
    }

    private void givenExistingJob() {
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenExistingJobForStatusChange() {
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenUserExists(UUID userId) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(UserModel.builder().id(userId).build()));
    }

    private UpdateJobRequest.UpdateJobRequestBuilder unchangedUpdate() {
        return UpdateJobRequest.builder().title("Fix login").assignedTo(assigneeId).deadline(DEADLINE);
    }

    private List<Object> publishedEvents(int count) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(count)).publishEvent(captor.capture());
        return captor.getAllValues();
    }

    // --- create ---

    @Test
    @DisplayName("create with an assignee publishes JobAssignedEvent")
    void create_shouldPublishJobAssigned_whenAssigned() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenUserExists(assigneeId);
        when(jobRepository.save(any())).thenReturn(job);

        jobService.create(projectId, CreateJobRequest.builder().title("Fix login").assignedTo(assigneeId).build(),
                ownerId);

        assertThat(publishedEvents(1)).containsExactly(new JobAssignedEvent(jobId, projectId, ownerId, assigneeId));
    }

    @Test
    @DisplayName("create without an assignee publishes nothing")
    void create_shouldPublishNothing_whenUnassigned() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        job.setAssignedTo(null);
        when(jobRepository.save(any())).thenReturn(job);

        jobService.create(projectId, CreateJobRequest.builder().title("Fix login").build(), ownerId);

        verifyNoInteractions(eventPublisher);
    }

    // --- update ---

    @Test
    @DisplayName("update changing assignee and deadline publishes only JobAssignedEvent")
    void update_shouldPublishOnlyJobAssigned_whenAssigneeAndDeadlineChange() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJob();
        UUID newAssigneeId = UUID.randomUUID();
        givenUserExists(newAssigneeId);

        jobService.update(projectId, jobId,
                unchangedUpdate().assignedTo(newAssigneeId).deadline(NEW_DEADLINE).build(), ownerId);

        assertThat(publishedEvents(1))
                .containsExactly(new JobAssignedEvent(jobId, projectId, ownerId, newAssigneeId));
    }

    @Test
    @DisplayName("update unassigning the job (and changing its deadline) publishes nothing")
    void update_shouldPublishNothing_whenUnassigned() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJob();

        jobService.update(projectId, jobId, unchangedUpdate().assignedTo(null).deadline(NEW_DEADLINE).build(),
                ownerId);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("update changing only the deadline publishes JobUpdatedEvent with the new deadline")
    void update_shouldPublishJobUpdated_whenDeadlineChanges() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJob();
        givenUserExists(assigneeId);

        jobService.update(projectId, jobId, unchangedUpdate().deadline(NEW_DEADLINE).build(), ownerId);

        assertThat(publishedEvents(1)).containsExactly(new JobUpdatedEvent(jobId, projectId, ownerId,
                List.of(JobUpdatedEvent.DEADLINE), NEW_DEADLINE, null));
    }

    @Test
    @DisplayName("update clearing the deadline publishes JobUpdatedEvent with a null deadline")
    void update_shouldPublishJobUpdated_whenDeadlineCleared() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJob();
        givenUserExists(assigneeId);

        jobService.update(projectId, jobId, unchangedUpdate().deadline(null).build(), ownerId);

        assertThat(publishedEvents(1)).containsExactly(new JobUpdatedEvent(jobId, projectId, ownerId,
                List.of(JobUpdatedEvent.DEADLINE), null, null));
    }

    @Test
    @DisplayName("update changing only the priority publishes JobUpdatedEvent with the new priority")
    void update_shouldPublishJobUpdated_whenPriorityChanges() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJob();
        givenUserExists(assigneeId);

        jobService.update(projectId, jobId, unchangedUpdate().priority(JobPriority.HIGH).build(), ownerId);

        assertThat(publishedEvents(1)).containsExactly(new JobUpdatedEvent(jobId, projectId, ownerId,
                List.of(JobUpdatedEvent.PRIORITY), null, JobPriority.HIGH));
    }

    @Test
    @DisplayName("update changing deadline and priority publishes one JobUpdatedEvent listing both")
    void update_shouldPublishOneJobUpdated_whenDeadlineAndPriorityChange() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJob();
        givenUserExists(assigneeId);

        jobService.update(projectId, jobId,
                unchangedUpdate().deadline(NEW_DEADLINE).priority(JobPriority.LOW).build(), ownerId);

        assertThat(publishedEvents(1)).containsExactly(new JobUpdatedEvent(jobId, projectId, ownerId,
                List.of(JobUpdatedEvent.DEADLINE, JobUpdatedEvent.PRIORITY), NEW_DEADLINE, JobPriority.LOW));
    }

    @Test
    @DisplayName("update changing only other fields (title, same priority) publishes nothing")
    void update_shouldPublishNothing_whenOtherFieldsChange() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJob();
        givenUserExists(assigneeId);

        jobService.update(projectId, jobId,
                unchangedUpdate().title("Fix login page").priority(JobPriority.MEDIUM).build(), ownerId);

        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("update rejected by a guard publishes nothing")
    void update_shouldPublishNothing_whenForbidden() {
        givenCaller(assigneeId, ProjectMemberRole.MEMBER);

        assertThatThrownBy(() -> jobService.update(projectId, jobId,
                unchangedUpdate().priority(JobPriority.HIGH).build(), assigneeId))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(eventPublisher);
    }

    // --- updateStatus ---

    @Test
    @DisplayName("moving to Blocked publishes only JobBlockedEvent with the stored reason")
    void updateStatus_shouldPublishOnlyJobBlocked_whenBlocked() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJobForStatusChange();
        when(blockReasonService.findOrCreate(projectId, "  Waiting for client "))
                .thenReturn(BlockReasonModel.builder().id(UUID.randomUUID()).reason("Waiting for client").build());

        jobService.updateStatus(projectId, jobId, JobStatus.BLOCKED, "  Waiting for client ", null, ownerId);

        assertThat(publishedEvents(1))
                .containsExactly(new JobBlockedEvent(jobId, projectId, ownerId, "Waiting for client"));
    }

    @Test
    @DisplayName("a non-Blocked, non-Completed change publishes only JobStatusChangedEvent")
    void updateStatus_shouldPublishJobStatusChanged_whenUnblocked() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        job.setStatus(JobStatus.BLOCKED);
        givenExistingJobForStatusChange();

        jobService.updateStatus(projectId, jobId, JobStatus.IN_PROGRESS, null, null, ownerId);

        assertThat(publishedEvents(1)).containsExactly(new JobStatusChangedEvent(jobId, projectId, ownerId,
                JobStatus.BLOCKED, JobStatus.IN_PROGRESS));
        verifyNoInteractions(jobRelationshipRepository);
    }

    @Test
    @DisplayName("completing a blocker publishes JobStatusChangedEvent and BlockerClearedEvent with the waiting jobs")
    void updateStatus_shouldPublishBlockerCleared_whenCompletedWithWaitingJobs() {
        givenCaller(assigneeId, ProjectMemberRole.MEMBER);
        givenExistingJobForStatusChange();
        List<UUID> waiting = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(jobRelationshipRepository.findSourceJobIdsByTargetJobIdAndType(jobId, JobRelationshipType.BLOCKED_BY))
                .thenReturn(waiting);

        jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, null, assigneeId);

        assertThat(publishedEvents(2)).containsExactly(
                new JobStatusChangedEvent(jobId, projectId, assigneeId, JobStatus.IN_PROGRESS, JobStatus.COMPLETED),
                new BlockerClearedEvent(jobId, projectId, assigneeId, waiting));
    }

    @Test
    @DisplayName("completing a job nothing waits on publishes only JobStatusChangedEvent")
    void updateStatus_shouldNotPublishBlockerCleared_whenNoWaitingJobs() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJobForStatusChange();
        when(jobRelationshipRepository.findSourceJobIdsByTargetJobIdAndType(jobId, JobRelationshipType.BLOCKED_BY))
                .thenReturn(List.of());

        jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, null, ownerId);

        assertThat(publishedEvents(1)).containsExactly(new JobStatusChangedEvent(jobId, projectId, ownerId,
                JobStatus.IN_PROGRESS, JobStatus.COMPLETED));
    }

    @Test
    @DisplayName("a rejected status change publishes nothing")
    void updateStatus_shouldPublishNothing_whenForbidden() {
        UUID otherMemberId = UUID.randomUUID();
        givenCaller(otherMemberId, ProjectMemberRole.MEMBER);
        when(jobRepository.findByIdForUpdate(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.updateStatus(projectId, jobId, JobStatus.COMPLETED, null, null, otherMemberId))
                .isInstanceOf(ForbiddenException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // --- softDelete ---

    @Test
    @DisplayName("soft delete publishes JobDeletedEvent")
    void softDelete_shouldPublishJobDeleted() {
        givenCaller(ownerId, ProjectMemberRole.OWNER);
        givenExistingJob();

        jobService.softDelete(projectId, jobId, ownerId);

        assertThat(publishedEvents(1)).containsExactly(new JobDeletedEvent(jobId, projectId, ownerId));
    }
}
