package com.opsclear.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.event.DeadlineAlertEvent;
import com.opsclear.event.ScheduleRunMissedEvent;
import com.opsclear.model.DeadlineAlertKind;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationType;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrganisationRepository;
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
@DisplayName("NotificationService fan-out: deadline alerts and missed schedule runs")
class NotificationServiceScheduledEventsTest {

    private static final Instant DEADLINE = Instant.parse("2026-11-11T00:00:00Z");
    private static final Instant MISSED_AT = Instant.parse("2026-11-09T09:00:00Z");

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationSettingsService notificationSettingsService;
    @Mock private JobRepository jobRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private NoteRepository noteRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganisationRepository organisationRepository;
    @Captor private ArgumentCaptor<List<NotificationModel>> batchCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private NotificationService notificationService;

    private UUID orgId;
    private UUID projectId;
    private UUID jobId;
    private UUID scheduleId;
    private UUID ownerId;
    private UUID adminId;
    private UUID assigneeId;
    private ProjectModel project;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationSettingsService,
                jobRepository, projectRepository, projectMemberRepository, noteRepository, userRepository,
                objectMapper, organisationRepository);
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        scheduleId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        project = ProjectModel.builder().id(projectId).friendlyId("PRJ-012").name("Website Redesign")
                .organisationId(orgId).build();
    }

    // --- Helpers ---

    private JobModel job(JobStatus status, UUID assignee, Instant deadline) {
        return JobModel.builder().id(jobId).friendlyId("JOB-245").title("Fix login").projectId(projectId)
                .status(status).assignedTo(assignee).deadline(deadline).priority(JobPriority.HIGH).build();
    }

    private void givenJobScope(JobModel scopeJob) {
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(scopeJob));
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findOwnerAndAdminUserIds(projectId)).thenReturn(List.of(ownerId, adminId));
        if (scopeJob.getAssignedTo() != null) {
            when(projectMemberRepository.existsByProjectIdAndUserId(projectId, scopeJob.getAssignedTo()))
                    .thenReturn(true);
        }
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

    private static DeadlineAlertEvent alert(UUID jobId, UUID projectId, DeadlineAlertKind kind) {
        return new DeadlineAlertEvent(jobId, projectId, kind, DEADLINE);
    }

    // --- JOB_DEADLINE_SOON ---

    @Test
    @DisplayName("JOB_DEADLINE_SOON notifies only the assignee, with no actor and the deadline in the payload")
    void notifyDeadlineAlert_soon_shouldNotifyAssigneeOnly() throws Exception {
        givenJobScope(job(JobStatus.IN_PROGRESS, assigneeId, DEADLINE));
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        List<NotificationModel> saved = notificationService.notifyDeadlineAlert(
                alert(jobId, projectId, DeadlineAlertKind.DEADLINE_SOON));

        assertThat(saved).hasSize(1);
        NotificationModel notification = captureBatch().get(0);
        assertThat(notification.getRecipientId()).isEqualTo(assigneeId);
        assertThat(notification.getType()).isEqualTo(NotificationType.JOB_DEADLINE_SOON);
        assertThat(notification.getActorId()).isNull();
        assertThat(notification.getOrgId()).isEqualTo(orgId);
        assertThat(notification.getProjectId()).isEqualTo(projectId);
        assertThat(notification.getJobId()).isEqualTo(jobId);
        JsonNode payload = payload(notification);
        assertThat(payload.get("jobFriendlyId").asText()).isEqualTo("JOB-245");
        assertThat(payload.get("jobTitle").asText()).isEqualTo("Fix login");
        assertThat(payload.get("projectName").asText()).isEqualTo("Website Redesign");
        assertThat(payload.get("deadline").asText()).isEqualTo("2026-11-11T00:00:00Z");
        assertThat(payload.has("actorName")).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("JOB_DEADLINE_SOON for an unassigned job notifies no one (Owners/Admins aren't recipients)")
    void notifyDeadlineAlert_soon_shouldNotifyNoOne_whenUnassigned() {
        givenJobScope(job(JobStatus.NEW, null, DEADLINE));

        List<NotificationModel> saved = notificationService.notifyDeadlineAlert(
                alert(jobId, projectId, DeadlineAlertKind.DEADLINE_SOON));

        assertThat(saved).isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    // --- JOB_OVERDUE ---

    @Test
    @DisplayName("JOB_OVERDUE notifies the assignee and the project's Owners/Admins")
    void notifyDeadlineAlert_overdue_shouldNotifyAssigneeAndOwnersAndAdmins() {
        givenJobScope(job(JobStatus.BLOCKED, assigneeId, DEADLINE));
        givenNoMutes(assigneeId, ownerId, adminId);
        givenInsertReturnsInput();

        notificationService.notifyDeadlineAlert(alert(jobId, projectId, DeadlineAlertKind.OVERDUE));

        List<NotificationModel> batch = captureBatch();
        assertThat(batch).extracting(NotificationModel::getRecipientId)
                .containsExactlyInAnyOrder(assigneeId, ownerId, adminId);
        assertThat(batch).extracting(NotificationModel::getType).containsOnly(NotificationType.JOB_OVERDUE);
        assertThat(batch).extracting(NotificationModel::getActorId).containsOnlyNulls();
    }

    @Test
    @DisplayName("JOB_OVERDUE skips a recipient who muted it")
    void notifyDeadlineAlert_overdue_shouldRespectMutes() {
        givenJobScope(job(JobStatus.IN_PROGRESS, assigneeId, DEADLINE));
        givenNoMutes(assigneeId, adminId);
        when(notificationSettingsService.getMutedTypes(ownerId)).thenReturn(Set.of(NotificationType.JOB_OVERDUE));
        givenInsertReturnsInput();

        notificationService.notifyDeadlineAlert(alert(jobId, projectId, DeadlineAlertKind.OVERDUE));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId)
                .containsExactlyInAnyOrder(assigneeId, adminId);
    }

    // --- Stale alerts: state changed between the check and the fan-out ---

    @Test
    @DisplayName("A deadline alert is dropped when the job was completed before the fan-out")
    void notifyDeadlineAlert_shouldNotifyNoOne_whenJobCompleted() {
        givenJobScope(job(JobStatus.COMPLETED, assigneeId, DEADLINE));

        assertThat(notificationService.notifyDeadlineAlert(alert(jobId, projectId, DeadlineAlertKind.OVERDUE)))
                .isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    @Test
    @DisplayName("A deadline alert is dropped when the job's deadline moved before the fan-out")
    void notifyDeadlineAlert_shouldNotifyNoOne_whenDeadlineMoved() {
        givenJobScope(job(JobStatus.IN_PROGRESS, assigneeId, Instant.parse("2026-11-20T00:00:00Z")));

        assertThat(notificationService.notifyDeadlineAlert(alert(jobId, projectId, DeadlineAlertKind.OVERDUE)))
                .isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    @Test
    @DisplayName("A deadline alert is dropped when the job's deadline was cleared before the fan-out")
    void notifyDeadlineAlert_shouldNotifyNoOne_whenDeadlineCleared() {
        givenJobScope(job(JobStatus.IN_PROGRESS, assigneeId, null));

        assertThat(notificationService.notifyDeadlineAlert(
                alert(jobId, projectId, DeadlineAlertKind.DEADLINE_SOON))).isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    @Test
    @DisplayName("A deadline alert is dropped when the project was completed before the fan-out")
    void notifyDeadlineAlert_shouldNotifyNoOne_whenProjectCompleted() {
        project.setStatus(ProjectStatus.COMPLETED);
        givenJobScope(job(JobStatus.IN_PROGRESS, assigneeId, DEADLINE));

        assertThat(notificationService.notifyDeadlineAlert(alert(jobId, projectId, DeadlineAlertKind.OVERDUE)))
                .isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    @Test
    @DisplayName("A deadline alert is dropped when the job was deleted before the fan-out")
    void notifyDeadlineAlert_shouldNotifyNoOne_whenJobDeleted() {
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        assertThat(notificationService.notifyDeadlineAlert(alert(jobId, projectId, DeadlineAlertKind.OVERDUE)))
                .isEmpty();
        verifyNoInteractions(projectRepository, notificationRepository);
    }

    // --- SCHEDULE_RUN_MISSED ---

    @Test
    @DisplayName("SCHEDULE_RUN_MISSED notifies the project's Owners/Admins with schedule name and missed time, no job")
    void notifyScheduleRunMissed_shouldNotifyOwnersAndAdmins() throws Exception {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findOwnerAndAdminUserIds(projectId)).thenReturn(List.of(ownerId, adminId));
        givenNoMutes(ownerId, adminId);
        givenInsertReturnsInput();

        List<NotificationModel> saved = notificationService.notifyScheduleRunMissed(
                new ScheduleRunMissedEvent(scheduleId, projectId, "Weekly report", MISSED_AT));

        assertThat(saved).hasSize(2);
        List<NotificationModel> batch = captureBatch();
        assertThat(batch).extracting(NotificationModel::getRecipientId).containsExactlyInAnyOrder(ownerId, adminId);
        NotificationModel notification = batch.get(0);
        assertThat(notification.getType()).isEqualTo(NotificationType.SCHEDULE_RUN_MISSED);
        assertThat(notification.getActorId()).isNull();
        assertThat(notification.getOrgId()).isEqualTo(orgId);
        assertThat(notification.getProjectId()).isEqualTo(projectId);
        assertThat(notification.getJobId()).isNull();
        JsonNode payload = payload(notification);
        assertThat(payload.get("projectFriendlyId").asText()).isEqualTo("PRJ-012");
        assertThat(payload.get("projectName").asText()).isEqualTo("Website Redesign");
        assertThat(payload.get("scheduleName").asText()).isEqualTo("Weekly report");
        assertThat(payload.get("missedAt").asText()).isEqualTo("2026-11-09T09:00:00Z");
        assertThat(payload.has("actorName")).isFalse();
    }

    @Test
    @DisplayName("SCHEDULE_RUN_MISSED skips an Owner/Admin who muted it")
    void notifyScheduleRunMissed_shouldRespectMutes() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findOwnerAndAdminUserIds(projectId)).thenReturn(List.of(ownerId, adminId));
        givenNoMutes(ownerId);
        when(notificationSettingsService.getMutedTypes(adminId))
                .thenReturn(Set.of(NotificationType.SCHEDULE_RUN_MISSED));
        givenInsertReturnsInput();

        notificationService.notifyScheduleRunMissed(
                new ScheduleRunMissedEvent(scheduleId, projectId, "Weekly report", MISSED_AT));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(ownerId);
    }

    @Test
    @DisplayName("SCHEDULE_RUN_MISSED notifies no one when the project was deleted before the fan-out")
    void notifyScheduleRunMissed_shouldNotifyNoOne_whenProjectDeleted() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThat(notificationService.notifyScheduleRunMissed(
                new ScheduleRunMissedEvent(scheduleId, projectId, "Weekly report", MISSED_AT))).isEmpty();
        verifyNoInteractions(projectMemberRepository, notificationRepository);
    }
}
