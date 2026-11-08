package com.opsclear.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.event.ApprovalDecidedEvent;
import com.opsclear.event.ApprovalRequestedEvent;
import com.opsclear.event.NoteAddedEvent;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.JobModel;
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
@DisplayName("NotificationService fan-out")
class NotificationServiceFanOutTest {

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

    private UUID orgId;
    private UUID projectId;
    private UUID jobId;
    private UUID approvalId;
    private UUID ownerId;
    private UUID adminId;
    private UUID assigneeId;
    private UUID participantId;
    private ProjectModel project;
    private JobModel job;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, notificationSettingsService,
                jobRepository, projectRepository, projectMemberRepository, noteRepository, userRepository,
                objectMapper);
        orgId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        jobId = UUID.randomUUID();
        approvalId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        participantId = UUID.randomUUID();

        project = ProjectModel.builder()
                .id(projectId).friendlyId("PRJ-012").name("Website Redesign").organisationId(orgId).build();
        job = JobModel.builder()
                .id(jobId).friendlyId("JOB-245").title("Fix login").projectId(projectId)
                .status(JobStatus.IN_PROGRESS).assignedTo(assigneeId).build();
    }

    // --- Helpers ---

    private void givenJobScope() {
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findOwnerAndAdminUserIds(projectId)).thenReturn(List.of(ownerId, adminId));
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

    // --- APPROVAL_REQUESTED ---

    @Test
    @DisplayName("APPROVAL_REQUESTED notifies the project's Owners and Admins with the description")
    void notifyApprovalRequested_shouldNotifyOwnersAndAdmins() throws Exception {
        givenJobScope();
        givenActor(assigneeId, "Ann Assignee");
        givenNoMutes(ownerId, adminId);
        givenInsertReturnsInput();

        List<NotificationModel> saved = notificationService.notifyApprovalRequested(
                new ApprovalRequestedEvent(approvalId, projectId, jobId, assigneeId, "Need a decision"));

        assertThat(saved).hasSize(2);
        List<NotificationModel> batch = captureBatch();
        assertThat(batch).extracting(NotificationModel::getRecipientId).containsExactly(ownerId, adminId);
        NotificationModel first = batch.getFirst();
        assertThat(first.getType()).isEqualTo(NotificationType.APPROVAL_REQUESTED);
        assertThat(first.getOrgId()).isEqualTo(orgId);
        assertThat(first.getProjectId()).isEqualTo(projectId);
        assertThat(first.getJobId()).isEqualTo(jobId);
        assertThat(first.getApprovalId()).isEqualTo(approvalId);
        assertThat(first.getActorId()).isEqualTo(assigneeId);
        JsonNode payload = payload(first);
        assertThat(payload.get("jobFriendlyId").asText()).isEqualTo("JOB-245");
        assertThat(payload.get("jobTitle").asText()).isEqualTo("Fix login");
        assertThat(payload.get("projectFriendlyId").asText()).isEqualTo("PRJ-012");
        assertThat(payload.get("projectName").asText()).isEqualTo("Website Redesign");
        assertThat(payload.get("actorName").asText()).isEqualTo("Ann Assignee");
        assertThat(payload.get("description").asText()).isEqualTo("Need a decision");
    }

    @Test
    @DisplayName("APPROVAL_REQUESTED by an Owner excludes that Owner (the actor)")
    void notifyApprovalRequested_shouldExcludeActor() {
        givenJobScope();
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(adminId);
        givenInsertReturnsInput();

        notificationService.notifyApprovalRequested(
                new ApprovalRequestedEvent(approvalId, projectId, jobId, ownerId, "Need a decision"));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(adminId);
        verify(notificationSettingsService, never()).getMutedTypes(ownerId);
    }

    // --- APPROVAL_DECIDED ---

    @Test
    @DisplayName("APPROVAL_DECIDED notifies the requester with the decision and comment")
    void notifyApprovalDecided_shouldNotifyRequester() throws Exception {
        givenJobScope();
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        notificationService.notifyApprovalDecided(new ApprovalDecidedEvent(
                approvalId, projectId, jobId, ownerId, assigneeId, ApprovalStatus.REJECTED, "Too expensive"));

        List<NotificationModel> batch = captureBatch();
        assertThat(batch).extracting(NotificationModel::getRecipientId).containsExactly(assigneeId);
        assertThat(batch.getFirst().getType()).isEqualTo(NotificationType.APPROVAL_DECIDED);
        assertThat(batch.getFirst().getApprovalId()).isEqualTo(approvalId);
        JsonNode payload = payload(batch.getFirst());
        assertThat(payload.get("decision").asText()).isEqualTo("REJECTED");
        assertThat(payload.get("comment").asText()).isEqualTo("Too expensive");
    }

    @Test
    @DisplayName("APPROVAL_DECIDED without a comment leaves the comment out of the payload")
    void notifyApprovalDecided_shouldOmitNullComment() throws Exception {
        givenJobScope();
        givenActor(ownerId, "Olga Owner");
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        notificationService.notifyApprovalDecided(new ApprovalDecidedEvent(
                approvalId, projectId, jobId, ownerId, assigneeId, ApprovalStatus.APPROVED, null));

        JsonNode payload = payload(captureBatch().getFirst());
        assertThat(payload.get("decision").asText()).isEqualTo("APPROVED");
        assertThat(payload.has("comment")).isFalse();
    }

    @Test
    @DisplayName("APPROVAL_DECIDED on one's own request notifies no one")
    void notifyApprovalDecided_shouldNotifyNoOne_whenDeciderIsRequester() {
        givenJobScope();
        givenActor(ownerId, "Olga Owner");

        List<NotificationModel> saved = notificationService.notifyApprovalDecided(new ApprovalDecidedEvent(
                approvalId, projectId, jobId, ownerId, ownerId, ApprovalStatus.APPROVED, null));

        assertThat(saved).isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
        verifyNoInteractions(notificationSettingsService);
    }

    @Test
    @DisplayName("APPROVAL_DECIDED skips a requester who can no longer see the job (unassigned Member)")
    void notifyApprovalDecided_shouldSkipRequester_whoCannotSeeJob() {
        givenJobScope();
        givenActor(ownerId, "Olga Owner");

        List<NotificationModel> saved = notificationService.notifyApprovalDecided(new ApprovalDecidedEvent(
                approvalId, projectId, jobId, ownerId, participantId, ApprovalStatus.APPROVED, null));

        assertThat(saved).isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    // --- NOTE_ADDED ---

    @Test
    @DisplayName("NOTE_ADDED notifies the assignee and visible prior note authors, once each, never the author")
    void notifyNoteAdded_shouldNotifyAssigneeAndParticipants_once() throws Exception {
        givenJobScope();
        givenActor(adminId, "Adam Admin");
        // The assignee wrote notes too (de-dup); the actor's own note is in the list (actor exclusion).
        when(noteRepository.findDistinctAuthorIdsByJobId(jobId)).thenReturn(List.of(assigneeId, ownerId, adminId));
        givenNoMutes(assigneeId, ownerId);
        givenInsertReturnsInput();

        notificationService.notifyNoteAdded(new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, adminId));

        List<NotificationModel> batch = captureBatch();
        assertThat(batch).extracting(NotificationModel::getRecipientId).containsExactly(assigneeId, ownerId);
        assertThat(batch).allMatch(n -> n.getType() == NotificationType.NOTE_ADDED && n.getApprovalId() == null);
        assertThat(payload(batch.getFirst()).get("actorName").asText()).isEqualTo("Adam Admin");
    }

    @Test
    @DisplayName("NOTE_ADDED skips a former participant who is a Member and no longer assigned")
    void notifyNoteAdded_shouldSkipUnassignedMemberParticipant() {
        givenJobScope();
        givenActor(assigneeId, "Ann Assignee");
        when(noteRepository.findDistinctAuthorIdsByJobId(jobId)).thenReturn(List.of(participantId, assigneeId));

        List<NotificationModel> saved =
                notificationService.notifyNoteAdded(new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, assigneeId));

        assertThat(saved).isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    @Test
    @DisplayName("NOTE_ADDED on an unassigned job notifies only visible participants")
    void notifyNoteAdded_shouldIgnoreMissingAssignee() {
        job.setAssignedTo(null);
        givenJobScope();
        givenActor(adminId, "Adam Admin");
        when(noteRepository.findDistinctAuthorIdsByJobId(jobId)).thenReturn(List.of(ownerId, participantId));
        givenNoMutes(ownerId);
        givenInsertReturnsInput();

        notificationService.notifyNoteAdded(new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, adminId));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(ownerId);
    }

    @Test
    @DisplayName("A system event (no actor) removes no one and leaves the actor name out")
    void notifyNoteAdded_shouldHandleMissingActor() throws Exception {
        givenJobScope();
        when(noteRepository.findDistinctAuthorIdsByJobId(jobId)).thenReturn(List.of());
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        notificationService.notifyNoteAdded(new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, null));

        NotificationModel only = captureBatch().getFirst();
        assertThat(only.getActorId()).isNull();
        assertThat(payload(only).has("actorName")).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("An actor who no longer exists leaves the actor name out")
    void notifyNoteAdded_shouldOmitActorName_whenActorUnknown() throws Exception {
        UUID ghostId = UUID.randomUUID();
        givenJobScope();
        when(userRepository.findById(ghostId)).thenReturn(Optional.empty());
        when(noteRepository.findDistinctAuthorIdsByJobId(jobId)).thenReturn(List.of(ghostId));
        givenNoMutes(assigneeId);
        givenInsertReturnsInput();

        notificationService.notifyNoteAdded(new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, ghostId));

        assertThat(payload(captureBatch().getFirst()).has("actorName")).isFalse();
    }

    // --- Mutes ---

    @Test
    @DisplayName("Recipients who muted the type get nothing; the rest still do")
    void fanOut_shouldDropRecipientsWhoMutedType() {
        givenJobScope();
        givenActor(assigneeId, "Ann Assignee");
        when(notificationSettingsService.getMutedTypes(ownerId)).thenReturn(Set.of(NotificationType.APPROVAL_REQUESTED));
        when(notificationSettingsService.getMutedTypes(adminId)).thenReturn(Set.of(NotificationType.NOTE_ADDED));
        givenInsertReturnsInput();

        notificationService.notifyApprovalRequested(
                new ApprovalRequestedEvent(approvalId, projectId, jobId, assigneeId, "Need a decision"));

        assertThat(captureBatch()).extracting(NotificationModel::getRecipientId).containsExactly(adminId);
    }

    @Test
    @DisplayName("When every recipient muted the type nothing is inserted")
    void fanOut_shouldInsertNothing_whenAllMuted() {
        givenJobScope();
        givenActor(assigneeId, "Ann Assignee");
        when(notificationSettingsService.getMutedTypes(any())).thenReturn(Set.of(NotificationType.APPROVAL_REQUESTED));

        List<NotificationModel> saved = notificationService.notifyApprovalRequested(
                new ApprovalRequestedEvent(approvalId, projectId, jobId, assigneeId, "Need a decision"));

        assertThat(saved).isEmpty();
        verify(notificationRepository, never()).insertBatch(any());
    }

    // --- Deleted job or project ---

    @Test
    @DisplayName("A job deleted before fan-out notifies no one")
    void fanOut_shouldSkip_whenJobDeleted() {
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.empty());

        List<NotificationModel> saved =
                notificationService.notifyNoteAdded(new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, ownerId));

        assertThat(saved).isEmpty();
        verifyNoInteractions(projectRepository, projectMemberRepository, noteRepository, notificationRepository);
    }

    @Test
    @DisplayName("A project deleted before fan-out notifies no one")
    void fanOut_shouldSkip_whenProjectDeleted() {
        when(jobRepository.findByIdAndDeletedAtIsNull(jobId)).thenReturn(Optional.of(job));
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        List<NotificationModel> saved = notificationService.notifyApprovalRequested(
                new ApprovalRequestedEvent(approvalId, projectId, jobId, ownerId, "Need a decision"));

        assertThat(saved).isEmpty();
        verifyNoInteractions(projectMemberRepository, notificationRepository);
    }

    // --- NotificationPayload ---

    @Test
    @DisplayName("The payload snapshot skips null values and keeps insertion order")
    void payload_shouldSkipNullsAndKeepOrder() {
        NotificationPayload payload = NotificationPayload.forJob(job, project, null)
                .put(NotificationPayload.COMMENT, null)
                .put(NotificationPayload.DECISION, "APPROVED");

        assertThat(payload.values()).containsOnlyKeys("jobFriendlyId", "jobTitle", "projectFriendlyId",
                "projectName", "decision");
        assertThat(payload.toJson(objectMapper)).isEqualTo("{\"jobFriendlyId\":\"JOB-245\",\"jobTitle\":\"Fix login\","
                + "\"projectFriendlyId\":\"PRJ-012\",\"projectName\":\"Website Redesign\",\"decision\":\"APPROVED\"}");
    }
}
