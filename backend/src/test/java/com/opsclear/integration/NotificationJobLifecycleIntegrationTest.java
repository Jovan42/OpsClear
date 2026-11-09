package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobRelationshipModel;
import com.opsclear.model.JobRelationshipType;
import com.opsclear.model.JobStatus;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.JobRelationshipRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification fan-out (ADR-0051, JOB-277) for the job lifecycle types: JOB_ASSIGNED, JOB_STATUS_CHANGED,
 * JOB_BLOCKED, BLOCKER_CLEARED, JOB_UPDATED and JOB_DELETED. The real job endpoints trigger the events, and results
 * are checked through {@code GET /api/notifications} and the database. The test profile runs the notification
 * executor synchronously, so fan-out has finished when the request returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Notification fan-out: job lifecycle events")
class NotificationJobLifecycleIntegrationTest {

    private static final String DEADLINE = "2026-12-01T00:00:00Z";
    private static final String NEW_DEADLINE = "2026-12-15T00:00:00Z";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JobRelationshipRepository jobRelationshipRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID adminId;
    private UUID assigneeId;
    private UUID memberId;
    private UUID projectId;
    private UUID jobId;
    // The user sync filter overwrites email and name from the JWT, so requests carry the real ones.
    private final Map<UUID, UserModel> users = new HashMap<>();

    @BeforeEach
    void setUp() {
        cleanUp();

        ownerId = user("owner@example.com", "Olga Owner");
        adminId = user("admin@example.com", "Adam Admin");
        assigneeId = user("assignee@example.com", "Ann Assignee");
        memberId = user("member@example.com", "Max Member");

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Lifecycle Org").slug("LIFE").createdBy(ownerId).build());
        UUID orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, assigneeId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        friendlyIdRepository.seedForOrg(orgId);

        projectId = projectRepository.save(ProjectModel.builder().name("Website Redesign")
                .friendlyId("PRJ-277").ownerId(ownerId).organisationId(orgId).build()).getId();
        addMember(ownerId, ProjectMemberRole.OWNER);
        addMember(adminId, ProjectMemberRole.ADMIN);
        addMember(assigneeId, ProjectMemberRole.MEMBER);
        // A Member who isn't the assignee: can't see the job, so must never be notified about it.
        addMember(memberId, ProjectMemberRole.MEMBER);

        jobId = job("JOB-277", "Fix login", JobStatus.IN_PROGRESS, assigneeId);
    }

    // Other integration classes only clean some tables, so leave nothing of ours behind.
    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        jobRelationshipRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- JOB_ASSIGNED ---

    @Test
    @DisplayName("JOB_ASSIGNED on reassign notifies only the new assignee, with the deadline and priority")
    void reassign_shouldNotifyNewAssigneeOnly() throws Exception {
        updateJob(ownerId, jobBody("Fix login", memberId, DEADLINE, "HIGH"));

        assertThat(recipients(NotificationType.JOB_ASSIGNED)).containsExactly(memberId);
        assertThat(allNotificationCount()).isEqualTo(1);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("JOB_ASSIGNED"))
                .andExpect(jsonPath("$.items[0].actor.id").value(ownerId.toString()))
                .andExpect(jsonPath("$.items[0].jobFriendlyId").value("JOB-277"))
                .andExpect(jsonPath("$.items[0].projectFriendlyId").value("PRJ-277"))
                .andExpect(jsonPath("$.items[0].payload.jobTitle").value("Fix login"))
                .andExpect(jsonPath("$.items[0].payload.projectName").value("Website Redesign"))
                .andExpect(jsonPath("$.items[0].payload.actorName").value("Olga Owner"))
                .andExpect(jsonPath("$.items[0].payload.deadline").value(DEADLINE))
                .andExpect(jsonPath("$.items[0].payload.priority").value("HIGH"));
        // The previous assignee isn't notified (out of V1).
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("JOB_ASSIGNED on create with an assignee notifies that assignee")
    void createWithAssignee_shouldNotifyAssignee() throws Exception {
        UUID createdId = createJob(adminId, jobBody("Write docs", assigneeId, null, null));

        assertThat(recipients(NotificationType.JOB_ASSIGNED)).containsExactly(assigneeId);
        assertThat(allNotificationCount()).isEqualTo(1);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("JOB_ASSIGNED"))
                .andExpect(jsonPath("$.items[0].payload.jobTitle").value("Write docs"))
                .andExpect(jsonPath("$.items[0].payload.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.items[0].payload.deadline").doesNotExist());
        assertThat(dsl.fetchValue(dsl.select(NOTIFICATIONS.JOB_ID).from(NOTIFICATIONS))).isEqualTo(createdId);
    }

    @Test
    @DisplayName("Creating an unassigned job notifies no one")
    void createUnassigned_shouldNotifyNoOne() throws Exception {
        createJob(adminId, jobBody("Write docs", null, null, null));

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("Self-assignment by the actor (on create or reassign) notifies no one")
    void selfAssign_shouldNotifyNoOne() throws Exception {
        createJob(ownerId, jobBody("Plan sprint", ownerId, null, null));
        updateJob(adminId, jobBody("Fix login", adminId, null, null));

        assertThat(allNotificationCount()).isZero();
    }

    // --- JOB_STATUS_CHANGED ---

    @Test
    @DisplayName("JOB_STATUS_CHANGED notifies Owners/Admins with from/to status, not the assignee who acted")
    void statusChange_shouldNotifyOwnersAndAdmins_excludingActor() throws Exception {
        changeStatus(assigneeId, "COMPLETED", null);

        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).containsExactlyInAnyOrder(ownerId, adminId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("JOB_STATUS_CHANGED"))
                .andExpect(jsonPath("$.items[0].actor.id").value(assigneeId.toString()))
                .andExpect(jsonPath("$.items[0].payload.fromStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].payload.toStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].payload.actorName").value("Ann Assignee"));
    }

    @Test
    @DisplayName("JOB_STATUS_CHANGED on reopen notifies the assignee and the other Admin, not the Owner who acted")
    void reopen_shouldNotifyAssigneeAndAdmin() throws Exception {
        changeStatus(assigneeId, "COMPLETED", null);
        notificationRepository.deleteAll();

        changeStatus(ownerId, "IN_PROGRESS", null);

        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).containsExactlyInAnyOrder(assigneeId, adminId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].payload.fromStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].payload.toStatus").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("JOB_STATUS_CHANGED gives an Admin who is also the assignee exactly one notification")
    void statusChange_shouldDeduplicateAdminAssignee() throws Exception {
        UUID adminJobId = job("JOB-278", "Renew certificate", JobStatus.NEW, adminId);

        changeStatus(adminJobId, ownerId, "IN_PROGRESS", null);

        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).containsExactly(adminId);
    }

    @Test
    @DisplayName("A rejected status change (invalid transition) creates no notification")
    void invalidStatusChange_shouldCreateNothing() throws Exception {
        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, jobId))
                        .with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "NEW"))))
                .andExpect(status().isBadRequest());

        assertThat(allNotificationCount()).isZero();
    }

    // --- JOB_BLOCKED ---

    @Test
    @DisplayName("Moving to Blocked sends only JOB_BLOCKED (with the reason), never JOB_STATUS_CHANGED")
    void block_shouldSendOnlyJobBlocked() throws Exception {
        changeStatus(assigneeId, "BLOCKED", "Waiting for client");

        assertThat(recipients(NotificationType.JOB_BLOCKED)).containsExactlyInAnyOrder(ownerId, adminId);
        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).isEmpty();
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("JOB_BLOCKED"))
                .andExpect(jsonPath("$.items[0].payload.blockReason").value("Waiting for client"))
                .andExpect(jsonPath("$.items[0].payload.fromStatus").doesNotExist());
    }

    @Test
    @DisplayName("Unblocking sends JOB_STATUS_CHANGED from BLOCKED, to the assignee when an Owner unblocks")
    void unblock_shouldSendStatusChanged() throws Exception {
        changeStatus(assigneeId, "BLOCKED", "Waiting for client");
        notificationRepository.deleteAll();

        changeStatus(ownerId, "IN_PROGRESS", null);

        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).containsExactlyInAnyOrder(assigneeId, adminId);
        assertThat(recipients(NotificationType.JOB_BLOCKED)).isEmpty();
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].payload.fromStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.items[0].payload.toStatus").value("IN_PROGRESS"));
    }

    // --- BLOCKER_CLEARED ---

    @Test
    @DisplayName("Completing a blocker sends BLOCKER_CLEARED to open waiting jobs' assignees, plus JOB_STATUS_CHANGED")
    void completeBlocker_shouldNotifyOpenWaitingJobAssignees() throws Exception {
        UUID openWaitingId = job("JOB-301", "Deploy release", JobStatus.NEW, memberId);
        UUID doneWaitingId = job("JOB-302", "Old follow-up", JobStatus.COMPLETED, adminId);
        blockedBy(openWaitingId, jobId);
        blockedBy(doneWaitingId, jobId);
        // A RELATED_TO link isn't a blocker.
        UUID relatedId = job("JOB-303", "Related work", JobStatus.NEW, adminId);
        relationship(relatedId, jobId, JobRelationshipType.RELATED_TO);

        changeStatus(ownerId, "COMPLETED", null);

        // Only the open waiting job's assignee; the completed one's assignee (admin) gets no BLOCKER_CLEARED.
        assertThat(recipients(NotificationType.BLOCKER_CLEARED)).containsExactly(memberId);
        assertThat(dsl.fetchValue(dsl.select(NOTIFICATIONS.JOB_ID).from(NOTIFICATIONS)
                .where(NOTIFICATIONS.TYPE.eq(NotificationType.BLOCKER_CLEARED.name())))).isEqualTo(openWaitingId);
        // Both fire: the blocker's watchers still get JOB_STATUS_CHANGED.
        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).containsExactlyInAnyOrder(assigneeId, adminId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("BLOCKER_CLEARED"))
                .andExpect(jsonPath("$.items[0].jobFriendlyId").value("JOB-301"))
                .andExpect(jsonPath("$.items[0].payload.jobTitle").value("Deploy release"))
                .andExpect(jsonPath("$.items[0].payload.blockerJobFriendlyId").value("JOB-277"))
                .andExpect(jsonPath("$.items[0].payload.blockerJobTitle").value("Fix login"))
                .andExpect(jsonPath("$.items[0].payload.actorName").value("Olga Owner"));
    }

    @Test
    @DisplayName("Completing a job nothing waits on sends no BLOCKER_CLEARED")
    void completeNonBlocker_shouldSendNoBlockerCleared() throws Exception {
        changeStatus(assigneeId, "COMPLETED", null);

        assertThat(recipients(NotificationType.BLOCKER_CLEARED)).isEmpty();
    }

    // --- JOB_UPDATED ---

    @Test
    @DisplayName("JOB_UPDATED on a deadline change notifies the assignee with the field and new value")
    void deadlineChange_shouldNotifyAssignee() throws Exception {
        updateJob(ownerId, jobBody("Fix login", assigneeId, NEW_DEADLINE, null));

        assertThat(recipients(NotificationType.JOB_UPDATED)).containsExactly(assigneeId);
        assertThat(allNotificationCount()).isEqualTo(1);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("JOB_UPDATED"))
                .andExpect(jsonPath("$.items[0].payload.changedFields", contains("deadline")))
                .andExpect(jsonPath("$.items[0].payload.deadline").value(NEW_DEADLINE))
                .andExpect(jsonPath("$.items[0].payload.priority").doesNotExist());
    }

    @Test
    @DisplayName("JOB_UPDATED on a priority change notifies the assignee with the field and new value")
    void priorityChange_shouldNotifyAssignee() throws Exception {
        updateJob(adminId, jobBody("Fix login", assigneeId, null, "CRITICAL"));

        assertThat(recipients(NotificationType.JOB_UPDATED)).containsExactly(assigneeId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].payload.changedFields", contains("priority")))
                .andExpect(jsonPath("$.items[0].payload.priority").value("CRITICAL"))
                .andExpect(jsonPath("$.items[0].payload.deadline").doesNotExist());
    }

    @Test
    @DisplayName("A title-only edit notifies no one")
    void titleEdit_shouldNotifyNoOne() throws Exception {
        updateJob(ownerId, jobBody("Fix login page", assigneeId, null, null));

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("Changing assignee and deadline in one edit sends only JOB_ASSIGNED (carrying the deadline)")
    void assigneeAndDeadline_shouldSendOnlyJobAssigned() throws Exception {
        updateJob(ownerId, jobBody("Fix login", memberId, NEW_DEADLINE, null));

        assertThat(recipients(NotificationType.JOB_ASSIGNED)).containsExactly(memberId);
        assertThat(recipients(NotificationType.JOB_UPDATED)).isEmpty();
        assertThat(allNotificationCount()).isEqualTo(1);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("JOB_ASSIGNED"))
                .andExpect(jsonPath("$.items[0].payload.deadline").value(NEW_DEADLINE));
    }

    // --- JOB_DELETED ---

    @Test
    @DisplayName("JOB_DELETED notifies the assignee, and the payload snapshot is readable after the delete")
    void delete_shouldNotifyAssigneeWithSnapshot() throws Exception {
        mockMvc.perform(delete(ApiPaths.job(projectId, jobId)).with(as(adminId)))
                .andExpect(status().isNoContent());

        assertThat(recipients(NotificationType.JOB_DELETED)).containsExactly(assigneeId);
        assertThat(allNotificationCount()).isEqualTo(1);
        mockMvc.perform(get(ApiPaths.job(projectId, jobId)).with(as(assigneeId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("JOB_DELETED"))
                .andExpect(jsonPath("$.items[0].actor.id").value(adminId.toString()))
                .andExpect(jsonPath("$.items[0].payload.jobFriendlyId").value("JOB-277"))
                .andExpect(jsonPath("$.items[0].payload.jobTitle").value("Fix login"))
                .andExpect(jsonPath("$.items[0].payload.projectName").value("Website Redesign"))
                .andExpect(jsonPath("$.items[0].payload.actorName").value("Adam Admin"));
    }

    // --- Member visibility ---

    @Test
    @DisplayName("A Member who isn't the assignee never receives job lifecycle notifications")
    void nonAssigneeMember_shouldNeverBeNotified() throws Exception {
        UUID waitingId = job("JOB-301", "Deploy release", JobStatus.NEW, assigneeId);
        blockedBy(waitingId, jobId);

        changeStatus(ownerId, "BLOCKED", "Waiting for client");
        changeStatus(ownerId, "IN_PROGRESS", null);
        changeStatus(ownerId, "COMPLETED", null);
        changeStatus(ownerId, "IN_PROGRESS", null);
        updateJob(ownerId, jobBody("Fix login", assigneeId, NEW_DEADLINE, "HIGH"));
        mockMvc.perform(delete(ApiPaths.job(projectId, jobId)).with(as(ownerId)))
                .andExpect(status().isNoContent());

        // Every type fired for someone, but none reached the non-assignee Member.
        assertThat(recipients(NotificationType.JOB_BLOCKED)).isNotEmpty();
        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).isNotEmpty();
        assertThat(recipients(NotificationType.BLOCKER_CLEARED)).isNotEmpty();
        assertThat(recipients(NotificationType.JOB_UPDATED)).isNotEmpty();
        assertThat(recipients(NotificationType.JOB_DELETED)).isNotEmpty();
        assertThat(dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.RECIPIENT_ID.eq(memberId))).isZero();
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- Mutes ---

    @Test
    @DisplayName("A muted JOB_ASSIGNED isn't created")
    void mutedJobAssigned_shouldNotBeCreated() throws Exception {
        mute(memberId, NotificationType.JOB_ASSIGNED);

        updateJob(ownerId, jobBody("Fix login", memberId, null, null));

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("A muted JOB_STATUS_CHANGED isn't created for that user; others still get it")
    void mutedJobStatusChanged_shouldNotBeCreated() throws Exception {
        mute(ownerId, NotificationType.JOB_STATUS_CHANGED);

        changeStatus(assigneeId, "COMPLETED", null);

        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).containsExactly(adminId);
    }

    @Test
    @DisplayName("A muted JOB_BLOCKED isn't created for that user; others still get it")
    void mutedJobBlocked_shouldNotBeCreated() throws Exception {
        mute(adminId, NotificationType.JOB_BLOCKED);

        changeStatus(assigneeId, "BLOCKED", "Waiting for client");

        assertThat(recipients(NotificationType.JOB_BLOCKED)).containsExactly(ownerId);
    }

    @Test
    @DisplayName("A muted BLOCKER_CLEARED isn't created; the JOB_STATUS_CHANGED still is")
    void mutedBlockerCleared_shouldNotBeCreated() throws Exception {
        UUID waitingId = job("JOB-301", "Deploy release", JobStatus.NEW, memberId);
        blockedBy(waitingId, jobId);
        mute(memberId, NotificationType.BLOCKER_CLEARED);

        changeStatus(ownerId, "COMPLETED", null);

        assertThat(recipients(NotificationType.BLOCKER_CLEARED)).isEmpty();
        assertThat(recipients(NotificationType.JOB_STATUS_CHANGED)).containsExactlyInAnyOrder(assigneeId, adminId);
    }

    @Test
    @DisplayName("A muted JOB_UPDATED isn't created")
    void mutedJobUpdated_shouldNotBeCreated() throws Exception {
        mute(assigneeId, NotificationType.JOB_UPDATED);

        updateJob(ownerId, jobBody("Fix login", assigneeId, NEW_DEADLINE, "LOW"));

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("A muted JOB_DELETED isn't created")
    void mutedJobDeleted_shouldNotBeCreated() throws Exception {
        mute(assigneeId, NotificationType.JOB_DELETED);

        mockMvc.perform(delete(ApiPaths.job(projectId, jobId)).with(as(ownerId)))
                .andExpect(status().isNoContent());

        assertThat(allNotificationCount()).isZero();
    }

    // --- Helpers ---

    private UUID user(String email, String name) {
        UUID id = UUID.randomUUID();
        users.put(id, userRepository.save(UserModel.builder().id(id).email(email).name(name).build()));
        return id;
    }

    private void addMember(UUID userId, ProjectMemberRole role) {
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(userId).role(role).build());
    }

    // Seeded straight into the repository, so it creates no notification of its own.
    private UUID job(String friendlyId, String title, JobStatus status, UUID assignedTo) {
        return jobRepository.save(JobModel.builder().friendlyId(friendlyId).projectId(projectId).title(title)
                .status(status).priority(JobPriority.MEDIUM).assignedTo(assignedTo)
                .deadline(Instant.parse(DEADLINE)).createdBy(ownerId).build()).getId();
    }

    private void blockedBy(UUID waitingJobId, UUID blockerJobId) {
        relationship(waitingJobId, blockerJobId, JobRelationshipType.BLOCKED_BY);
    }

    private void relationship(UUID sourceJobId, UUID targetJobId, JobRelationshipType type) {
        jobRelationshipRepository.save(JobRelationshipModel.builder().sourceJobId(sourceJobId)
                .targetJobId(targetJobId).type(type).createdBy(ownerId).build());
    }

    private RequestPostProcessor as(UUID userId) {
        UserModel user = users.get(userId);
        return jwt().jwt(j -> j.subject(userId.toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName()));
    }

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // Full PUT/POST body; null fields are sent as null (a PUT with a null deadline would clear it, so the seeded
    // deadline is sent back unless the test changes it).
    private Map<String, Object> jobBody(String title, UUID assignedTo, String deadline, String priority) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("title", title);
        fields.put("assignedTo", assignedTo);
        fields.put("deadline", deadline != null ? deadline : DEADLINE);
        fields.put("priority", priority);
        return fields;
    }

    private UUID createJob(UUID callerId, Map<String, Object> fields) throws Exception {
        fields.remove("deadline");
        String response = mockMvc.perform(post(ApiPaths.jobs(projectId))
                        .with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fields)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void updateJob(UUID callerId, Map<String, Object> fields) throws Exception {
        mockMvc.perform(put(ApiPaths.job(projectId, jobId))
                        .with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fields)))
                .andExpect(status().isOk());
    }

    private void changeStatus(UUID callerId, String newStatus, String reason) throws Exception {
        changeStatus(jobId, callerId, newStatus, reason);
    }

    private void changeStatus(UUID targetJobId, UUID callerId, String newStatus, String reason) throws Exception {
        Map<String, Object> fields = new HashMap<>();
        fields.put("status", newStatus);
        fields.put("reason", reason);
        mockMvc.perform(patch(ApiPaths.jobStatus(projectId, targetJobId))
                        .with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(fields)))
                .andExpect(status().isOk());
    }

    private void mute(UUID userId, NotificationType... types) throws Exception {
        List<String> muted = Arrays.stream(types).map(Enum::name).toList();
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS)
                        .with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("muted", muted))))
                .andExpect(status().isOk());
    }

    private List<UUID> recipients(NotificationType type) {
        return dsl.select(NOTIFICATIONS.RECIPIENT_ID).from(NOTIFICATIONS)
                .where(NOTIFICATIONS.TYPE.eq(type.name()))
                .fetch(NOTIFICATIONS.RECIPIENT_ID);
    }

    private int allNotificationCount() {
        return dsl.fetchCount(NOTIFICATIONS);
    }
}
