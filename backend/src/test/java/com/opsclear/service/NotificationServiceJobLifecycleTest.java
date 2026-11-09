package com.opsclear.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.event.BlockerClearedEvent;
import com.opsclear.event.JobAssignedEvent;
import com.opsclear.event.JobBlockedEvent;
import com.opsclear.event.JobDeletedEvent;
import com.opsclear.event.JobStatusChangedEvent;
import com.opsclear.event.JobUpdatedEvent;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationType;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService fan-out: job lifecycle types (JOB-277)")
class NotificationServiceJobLifecycleTest {

    private static final Instant DEADLINE = Instant.parse("2026-11-20T00:00:00Z");

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationSettingsService notificationSettingsService;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private NoteRepository noteRepository;
    @Mock private UserRepository userRepository;
    @Captor private ArgumentCaptor<List<NotificationModel>> batchCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationService notificationService;

    private UUID projectId;
    private UUID jobId;
    private UUID ownerId;
    private UUID adminId;
    private UUID assigneeId;
    private ProjectModel project;
    private JobModel job;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationSettingsService,
                jobRepository, projectRepository, projectMemberRepository, noteRepository, userRepository,
                objectMapper);
        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        project = ProjectModel.builder().id(projectId).friendlyId("PRJ-012").name("Website Redesign")
                .organisationId(UUID.randomUUID()).build();
        job = job(jobId, "JOB-245", "Fix login", JobStatus.IN_PROGRESS, assigneeId);
    }

    // --- Helpers ---

    private JobModel job(UUID id, String friendlyId, String title, JobStatus status, UUID assignee) {
        return JobModel.builder().id(id).friendlyId(friendlyId).title(title).projectId(projectId).status(status)
                .assignedTo(assignee).deadline(DEADLINE).priority(JobPriority.HIGH).build();
    }

    private void givenProjectScope(JobModel scopeJob) {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findOwnerAndAdminUserIds(projectId)).thenReturn(List.of(ownerId, adminId));
        if (scopeJob.getAssignedTo() != null) {
            when(projectMemberRepository.existsByProjectIdAndUserId(projectId, scopeJob.getAssignedTo()))
                    .thenReturn(true);
        }
    }

    private void givenJobScope(JobModel scopeJob) {
        when(jobRepository.findByIdAndDeletedAtIsNull(scopeJob.getId())).thenReturn(Optional.of(scopeJob));
        givenProjectScope(scopeJob);
    }

    private void givenActor(UUID actorId, String name) {
        when(userRepository.findById(actorId))
                .thenReturn(Optional.of(UserModel.builder().id(actorId).name(name).build()));
    }

    private void givenNoMutes(UUID... userIds) {
        for (UUID userId : userIds) {
            when(notificationSettingsService.getMutedTypes(userId)).thenReturn(Set.of());
        }
    }

    private void givenInsertReturnsInput() {
        when(notificationRepository.insertBatch(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private List<NotificationModel> captureBatch() {
        verify(notificationRepository).insertBatch(batchCaptor.capture());
        return batchCaptor.getValue();
    }

    private JsonNode payload(NotificationModel notification) throws Exception {
        return objectMapper.readTree(notification.getPayload());
    }

    // --- JOB_ASSIGNED ---

    @Test
    @DisplayName("JOB_ASSIGNED notifies the new assignee with the deadline and priority")
    void notifyJobAssigned_shouldNotifyAssignee() throws Exception {
        givenJobScope(job);
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        List<NotificationModel> saved = notificationService.notifyJobAssigned(
                new JobAssignedEvent(jobId, projectId, ownerId, assigneeId));

        assertThat(saved).hasSize(1);
        NotificationModel only = captureBatch().getFirst();
        assertThat(only.getRecipientId()).isEqualTo(assigneeId);
        assertThat(only.getType()).isEqualTo(NotificationType.JOB_ASSIGNED);
        assertThat(only.getActorId()).isEqualTo(ownerId);
        JsonNode payload = payload(only);
        assertThat(payload.get("jobFriendlyId").asText()).isEqualTo("JOB-245");
        assertThat(payload.get("actorName").asText()).isEqualTo("Olga Owner");
        assertThat(payload.get("deadline").asText()).isEqualTo("2026-11-20T00:00:00Z");
        assertThat(payload.get("priority").asText()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("JOB_ASSIGNED without a deadline leaves it out of the payload")
    void notifyJobAssigned_shouldOmitMissingDeadline() throws Exception {
        job.setDeadline(null);
        givenJobScope(job);
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        notificationService.notifyJobAssigned(new JobAssignedEvent(jobId, projectId, ownerId, assigneeId));

        assertThat(payload(captureBatch().getFirst()).has("deadline")).isFalse();
    }

    @Test
    @DisplayName("JOB_ASSIGNED to oneself notifies no one")
    void notifyJobAssigned_shouldNotifyNoOne_whenSelfAssigned() {
        givenJobScope(job);
        givenActor(assigneeId, "Ann Assignee");

        assertThat(notificationService.notifyJobAssigned(
                new JobAssignedEvent(jobId, projectId, assigneeId, assigneeId))).isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    @Test
    @DisplayName("JOB_ASSIGNED is skipped when the job was reassigned to someone else before fan-out")
    void notifyJobAssigned_shouldSkip_whenNoLongerAssignee() {
        givenJobScope(job);
        givenActor(ownerId, "Olga Owner");

        assertThat(notificationService.notifyJobAssigned(
                new JobAssignedEvent(jobId, projectId, ownerId, adminId))).isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    // --- JOB_STATUS_CHANGED ---

    @Test
    @DisplayName("JOB_STATUS_CHANGED notifies the assignee and Owners/Admins, not the actor, with from/to status")
    void notifyJobStatusChanged_shouldNotifyAssigneeAndOwnersAndAdmins() throws Exception {
        givenJobScope(job);
        givenActor(adminId, "Adam Admin");
        givenNoMutes(assigneeId, ownerId);
        givenInsertReturnsInput();

        notificationService.notifyJobStatusChanged(new JobStatusChangedEvent(jobId, projectId, adminId,
                JobStatus.IN_PROGRESS, JobStatus.COMPLETED));

        List<NotificationModel> batch = captureBatch();
        assertThat(batch).extracting(NotificationModel::getRecipientId).containsExactly(assigneeId, ownerId);
        assertThat(batch).allMatch(n -> n.getType() == NotificationType.JOB_STATUS_CHANGED);
        JsonNode payload = payload(batch.getFirst());
        assertThat(payload.get("fromStatus").asText()).isEqualTo("IN_PROGRESS");
        assertThat(payload.get("toStatus").asText()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("JOB_STATUS_CHANGED gives an Admin who is also the assignee one notification")
    void notifyJobStatusChanged_shouldDeduplicate_whenAdminIsAssignee() {
        job.setAssignedTo(adminId);
        givenJobScope(job);
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(adminId);
        givenInsertReturnsInput();

        notificationService.notifyJobStatusChanged(new JobStatusChangedEvent(jobId, projectId, ownerId,
                JobStatus.NEW, JobStatus.IN_PROGRESS));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(adminId);
    }

    @Test
    @DisplayName("JOB_STATUS_CHANGED on an unassigned job notifies only the Owners/Admins")
    void notifyJobStatusChanged_shouldIgnoreMissingAssignee() {
        job.setAssignedTo(null);
        givenJobScope(job);
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(adminId);
        givenInsertReturnsInput();

        notificationService.notifyJobStatusChanged(new JobStatusChangedEvent(jobId, projectId, ownerId,
                JobStatus.NEW, JobStatus.IN_PROGRESS));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(adminId);
    }

    // --- JOB_BLOCKED ---

    @Test
    @DisplayName("JOB_BLOCKED notifies the assignee and Owners/Admins with the block reason")
    void notifyJobBlocked_shouldNotifyWithReason() throws Exception {
        givenJobScope(job);
        givenActor(assigneeId, "Ann Assignee");
        givenNoMutes(ownerId, adminId);
        givenInsertReturnsInput();

        notificationService.notifyJobBlocked(new JobBlockedEvent(jobId, projectId, assigneeId, "Waiting for client"));

        List<NotificationModel> batch = captureBatch();
        assertThat(batch).extracting(NotificationModel::getRecipientId).containsExactly(ownerId, adminId);
        assertThat(batch).allMatch(n -> n.getType() == NotificationType.JOB_BLOCKED);
        assertThat(payload(batch.getFirst()).get("blockReason").asText()).isEqualTo("Waiting for client");
    }

    @Test
    @DisplayName("JOB_BLOCKED respects mutes")
    void notifyJobBlocked_shouldDropMutedRecipients() {
        givenJobScope(job);
        givenActor(assigneeId, "Ann Assignee");
        when(notificationSettingsService.getMutedTypes(ownerId)).thenReturn(Set.of(NotificationType.JOB_BLOCKED));
        givenNoMutes(adminId);
        givenInsertReturnsInput();

        notificationService.notifyJobBlocked(new JobBlockedEvent(jobId, projectId, assigneeId, "Waiting"));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(adminId);
    }

    // --- BLOCKER_CLEARED ---

    @Test
    @DisplayName("BLOCKER_CLEARED notifies the assignee of each open waiting job, about that job")
    void notifyBlockerCleared_shouldNotifyWaitingAssignees() throws Exception {
        UUID blockerId = UUID.randomUUID();
        JobModel blocker = job(blockerId, "JOB-100", "Set up server", JobStatus.COMPLETED, adminId);
        UUID doneId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        JobModel done = job(doneId, "JOB-102", "Already done", JobStatus.COMPLETED, ownerId);
        when(jobRepository.findByIdAndDeletedAtIsNull(blockerId)).thenReturn(Optional.of(blocker));
        givenJobScope(job);
        when(jobRepository.findByIdAndDeletedAtIsNull(doneId)).thenReturn(Optional.of(done));
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, ownerId)).thenReturn(true);
        when(jobRepository.findByIdAndDeletedAtIsNull(deletedId)).thenReturn(Optional.empty());
        givenActor(adminId, "Adam Admin");
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        List<NotificationModel> saved = notificationService.notifyBlockerCleared(
                new BlockerClearedEvent(blockerId, projectId, adminId, List.of(jobId, doneId, deletedId)));

        assertThat(saved).hasSize(1);
        NotificationModel only = captureBatch().getFirst();
        assertThat(only.getRecipientId()).isEqualTo(assigneeId);
        assertThat(only.getType()).isEqualTo(NotificationType.BLOCKER_CLEARED);
        assertThat(only.getJobId()).isEqualTo(jobId);
        JsonNode payload = payload(only);
        assertThat(payload.get("jobFriendlyId").asText()).isEqualTo("JOB-245");
        assertThat(payload.get("blockerJobFriendlyId").asText()).isEqualTo("JOB-100");
        assertThat(payload.get("blockerJobTitle").asText()).isEqualTo("Set up server");
        assertThat(payload.get("actorName").asText()).isEqualTo("Adam Admin");
    }

    @Test
    @DisplayName("BLOCKER_CLEARED sends nothing when the blocker was reopened before fan-out")
    void notifyBlockerCleared_shouldSkip_whenBlockerReopened() {
        UUID blockerId = UUID.randomUUID();
        when(jobRepository.findByIdAndDeletedAtIsNull(blockerId)).thenReturn(Optional.of(
                job(blockerId, "JOB-100", "Set up server", JobStatus.IN_PROGRESS, adminId)));

        assertThat(notificationService.notifyBlockerCleared(
                new BlockerClearedEvent(blockerId, projectId, adminId, List.of(jobId)))).isEmpty();
        verifyNoInteractions(projectRepository, notificationRepository, userRepository);
    }

    @Test
    @DisplayName("BLOCKER_CLEARED sends nothing when the blocker was deleted before fan-out")
    void notifyBlockerCleared_shouldSkip_whenBlockerDeleted() {
        UUID blockerId = UUID.randomUUID();
        when(jobRepository.findByIdAndDeletedAtIsNull(blockerId)).thenReturn(Optional.empty());

        assertThat(notificationService.notifyBlockerCleared(
                new BlockerClearedEvent(blockerId, projectId, adminId, List.of(jobId)))).isEmpty();
        verifyNoInteractions(projectRepository, notificationRepository);
    }

    // --- JOB_UPDATED ---

    @Test
    @DisplayName("JOB_UPDATED notifies the assignee with the changed fields and new values")
    void notifyJobUpdated_shouldNotifyAssignee() throws Exception {
        givenJobScope(job);
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        notificationService.notifyJobUpdated(new JobUpdatedEvent(jobId, projectId, ownerId,
                List.of(JobUpdatedEvent.DEADLINE, JobUpdatedEvent.PRIORITY), DEADLINE, JobPriority.LOW));

        NotificationModel only = captureBatch().getFirst();
        assertThat(only.getRecipientId()).isEqualTo(assigneeId);
        assertThat(only.getType()).isEqualTo(NotificationType.JOB_UPDATED);
        JsonNode payload = payload(only);
        assertThat(payload.get("changedFields")).extracting(JsonNode::asText).containsExactly("deadline", "priority");
        assertThat(payload.get("deadline").asText()).isEqualTo("2026-11-20T00:00:00Z");
        assertThat(payload.get("priority").asText()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("JOB_UPDATED for a cleared deadline lists the field without a value")
    void notifyJobUpdated_shouldOmitClearedDeadline() throws Exception {
        givenJobScope(job);
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        notificationService.notifyJobUpdated(new JobUpdatedEvent(jobId, projectId, ownerId,
                List.of(JobUpdatedEvent.DEADLINE), null, null));

        JsonNode payload = payload(captureBatch().getFirst());
        assertThat(payload.get("changedFields")).extracting(JsonNode::asText).containsExactly("deadline");
        assertThat(payload.has("deadline")).isFalse();
        assertThat(payload.has("priority")).isFalse();
    }

    @Test
    @DisplayName("JOB_UPDATED on an unassigned job notifies no one (Owners/Admins aren't recipients)")
    void notifyJobUpdated_shouldNotifyNoOne_whenUnassigned() {
        job.setAssignedTo(null);
        givenJobScope(job);
        givenActor(ownerId, "Olga Owner");

        assertThat(notificationService.notifyJobUpdated(new JobUpdatedEvent(jobId, projectId, ownerId,
                List.of(JobUpdatedEvent.PRIORITY), null, JobPriority.LOW))).isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    // --- JOB_DELETED ---

    @Test
    @DisplayName("JOB_DELETED reads the deleted job and notifies its assignee with the snapshot")
    void notifyJobDeleted_shouldNotifyAssignee_fromDeletedJob() throws Exception {
        job.softDelete();
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        givenProjectScope(job);
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        notificationService.notifyJobDeleted(new JobDeletedEvent(jobId, projectId, ownerId));

        NotificationModel only = captureBatch().getFirst();
        assertThat(only.getRecipientId()).isEqualTo(assigneeId);
        assertThat(only.getType()).isEqualTo(NotificationType.JOB_DELETED);
        assertThat(only.getJobId()).isEqualTo(jobId);
        JsonNode payload = payload(only);
        assertThat(payload.get("jobFriendlyId").asText()).isEqualTo("JOB-245");
        assertThat(payload.get("jobTitle").asText()).isEqualTo("Fix login");
        assertThat(payload.get("projectName").asText()).isEqualTo("Website Redesign");
        verify(jobRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("JOB_DELETED for an unknown job notifies no one")
    void notifyJobDeleted_shouldSkip_whenJobUnknown() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThat(notificationService.notifyJobDeleted(new JobDeletedEvent(jobId, projectId, ownerId))).isEmpty();
        verifyNoInteractions(projectRepository, notificationRepository);
    }

    @Test
    @DisplayName("JOB_DELETED in a deleted project notifies no one")
    void notifyJobDeleted_shouldSkip_whenProjectDeleted() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThat(notificationService.notifyJobDeleted(new JobDeletedEvent(jobId, projectId, ownerId))).isEmpty();
        verifyNoInteractions(projectMemberRepository, notificationRepository);
    }
}
