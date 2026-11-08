package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.event.NoteAddedEvent;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobStatus;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.ApprovalRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NoteRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
import com.opsclear.repository.UserRepository;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification fan-out (ADR-0051, JOB-276) for APPROVAL_REQUESTED, APPROVAL_DECIDED and NOTE_ADDED: the real
 * endpoints trigger the events, and results are checked through {@code GET /api/notifications} and the database.
 * The test profile runs the notification executor synchronously, so fan-out has finished when the request returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Notification fan-out: approval and note events")
class NotificationFanOutIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ApprovalRepository approvalRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID adminId;
    private UUID assigneeId;
    private UUID formerId;
    private UUID outsiderId;
    private UUID projectId;
    private UUID jobId;
    private UUID assigneeMembershipId;
    // The user sync filter overwrites email and name from the JWT, so requests carry the real ones.
    private final Map<UUID, UserModel> users = new HashMap<>();

    @BeforeEach
    void setUp() {
        cleanUp();

        ownerId = user("owner@example.com", "Olga Owner");
        adminId = user("admin@example.com", "Adam Admin");
        assigneeId = user("assignee@example.com", "Ann Assignee");
        formerId = user("former@example.com", "Fred Former");
        outsiderId = user("outsider@example.com", "Oscar Outsider");

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Fan-out Org").slug("FAN").createdBy(ownerId).build());
        UUID orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, assigneeId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, formerId, OrganisationRole.MEMBER);
        // In the organisation but not in the project.
        organisationRepository.saveMember(orgId, outsiderId, OrganisationRole.MEMBER);

        projectId = projectRepository.save(ProjectModel.builder().name("Website Redesign")
                .friendlyId("PRJ-276").ownerId(ownerId).organisationId(orgId).build()).getId();
        addMember(ownerId, ProjectMemberRole.OWNER);
        addMember(adminId, ProjectMemberRole.ADMIN);
        assigneeMembershipId = addMember(assigneeId, ProjectMemberRole.MEMBER);
        addMember(formerId, ProjectMemberRole.MEMBER);

        jobId = jobRepository.save(JobModel.builder().friendlyId("JOB-276").projectId(projectId)
                .title("Fix login").status(JobStatus.IN_PROGRESS).assignedTo(assigneeId).createdBy(ownerId)
                .build()).getId();

        Set<UUID> addonIds = Set.of(addonId("NOTES"), addonId("APPROVALS"));
        subscriptionRepository.create(orgId, tierRepository.findAll().getFirst().getId(), "MONTHLY", addonIds);
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, "ACTIVE", null, null);
    }

    // Other integration classes only clean some tables, so leave nothing of ours behind.
    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        approvalRepository.deleteAll();
        noteRepository.deleteAll();
        jobRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- APPROVAL_REQUESTED ---

    @Test
    @DisplayName("APPROVAL_REQUESTED notifies the Owner and Admin with a payload snapshot, not the requester")
    void approvalRequested_shouldNotifyOwnersAndAdmins_notRequester() throws Exception {
        UUID approvalId = requestApproval(assigneeId, "Need a new transformer");

        assertThat(recipients(NotificationType.APPROVAL_REQUESTED)).containsExactlyInAnyOrder(ownerId, adminId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("APPROVAL_REQUESTED"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].actor.id").value(assigneeId.toString()))
                .andExpect(jsonPath("$.items[0].actor.name").value("Ann Assignee"))
                .andExpect(jsonPath("$.items[0].projectFriendlyId").value("PRJ-276"))
                .andExpect(jsonPath("$.items[0].jobFriendlyId").value("JOB-276"))
                .andExpect(jsonPath("$.items[0].approvalId").value(approvalId.toString()))
                .andExpect(jsonPath("$.items[0].payload.jobFriendlyId").value("JOB-276"))
                .andExpect(jsonPath("$.items[0].payload.jobTitle").value("Fix login"))
                .andExpect(jsonPath("$.items[0].payload.projectFriendlyId").value("PRJ-276"))
                .andExpect(jsonPath("$.items[0].payload.projectName").value("Website Redesign"))
                .andExpect(jsonPath("$.items[0].payload.actorName").value("Ann Assignee"))
                .andExpect(jsonPath("$.items[0].payload.description").value("Need a new transformer"));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("APPROVAL_REQUESTED by an Owner notifies the Admin but not that Owner")
    void approvalRequested_shouldExcludeOwner_whoRequested() throws Exception {
        requestApproval(ownerId, "Sign-off on budget");

        assertThat(recipients(NotificationType.APPROVAL_REQUESTED)).containsExactly(adminId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    // --- APPROVAL_DECIDED ---

    @Test
    @DisplayName("APPROVAL_DECIDED notifies the requester with the decision and comment, not the decider")
    void approvalDecided_shouldNotifyRequester_notDecider() throws Exception {
        UUID approvalId = requestApproval(assigneeId, "Need a new transformer");

        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId))
                        .with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "REJECTED", "comment", "Too expensive"))))
                .andExpect(status().isOk());

        assertThat(recipients(NotificationType.APPROVAL_DECIDED)).containsExactly(assigneeId);
        mockMvc.perform(get(ApiPaths.notifications("type=APPROVAL_DECIDED")).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].actor.id").value(ownerId.toString()))
                .andExpect(jsonPath("$.items[0].approvalId").value(approvalId.toString()))
                .andExpect(jsonPath("$.items[0].payload.decision").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].payload.comment").value("Too expensive"))
                .andExpect(jsonPath("$.items[0].payload.actorName").value("Olga Owner"));
    }

    // --- NOTE_ADDED ---

    @Test
    @DisplayName("NOTE_ADDED notifies the assignee and prior note authors once each, never the author")
    void noteAdded_shouldNotifyAssigneeAndParticipants_once() throws Exception {
        // Seeded straight into the repository, so they create no notifications of their own.
        noteRepository.insert(jobId, assigneeId, "Started on it");
        noteRepository.insert(jobId, ownerId, "Client wants it by Friday");
        noteRepository.insert(jobId, adminId, "Checked the logs");

        addNote(adminId, "Found the cause");

        // The assignee is also a participant but gets one notification; the author (admin) gets none.
        assertThat(recipients(NotificationType.NOTE_ADDED)).containsExactlyInAnyOrder(assigneeId, ownerId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("NOTE_ADDED"))
                .andExpect(jsonPath("$.items[0].actor.id").value(adminId.toString()))
                .andExpect(jsonPath("$.items[0].approvalId").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload.jobTitle").value("Fix login"))
                .andExpect(jsonPath("$.items[0].payload.actorName").value("Adam Admin"));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("NOTE_ADDED skips a former participant who is a Member and no longer assigned")
    void noteAdded_shouldSkipUnassignedMemberParticipant() throws Exception {
        noteRepository.insert(jobId, formerId, "I was on this before");
        noteRepository.insert(jobId, ownerId, "Reassigned to Ann");

        addNote(assigneeId, "Picking this up");

        assertThat(recipients(NotificationType.NOTE_ADDED)).containsExactly(ownerId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(formerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("An assignee removed from the project (job still assigned to them) gets no notification row")
    void noteAdded_shouldSkipAssignee_removedFromProject() throws Exception {
        noteRepository.insert(jobId, ownerId, "Client wants it by Friday");
        mockMvc.perform(delete(ApiPaths.member(projectId, assigneeMembershipId)).with(as(ownerId)))
                .andExpect(status().isNoContent());
        assertThat(jobRepository.findByIdAndDeletedAtIsNull(jobId).orElseThrow().getAssignedTo())
                .isEqualTo(assigneeId);

        addNote(adminId, "Found the cause");

        assertThat(recipients(NotificationType.NOTE_ADDED)).containsExactly(ownerId);
    }

    // --- Mutes ---

    @Test
    @DisplayName("A recipient who muted the type gets no notification; others still do")
    void mutedType_shouldNotBeCreated() throws Exception {
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS)
                        .with(as(ownerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("muted", List.of("APPROVAL_REQUESTED")))))
                .andExpect(status().isOk());

        requestApproval(assigneeId, "Need a new transformer");

        assertThat(recipients(NotificationType.APPROVAL_REQUESTED)).containsExactly(adminId);
    }

    // --- Rolled-back actions ---

    @Test
    @DisplayName("A rejected request (validation failure) creates no notification")
    void failedAction_shouldCreateNothing() throws Exception {
        noteRepository.insert(jobId, ownerId, "Prior note");

        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(as(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("content", "  "))))
                .andExpect(status().isBadRequest());

        assertThat(notificationRepository.countVisibleByRecipient(ownerId, false, null)).isZero();
        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("A note by someone outside the project (403) creates no notification")
    void noteByNonMember_shouldCreateNothing() throws Exception {
        noteRepository.insert(jobId, ownerId, "Prior note");

        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(as(outsiderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("content", "Let me in"))))
                .andExpect(status().isForbidden());

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("An approval request by someone outside the project (403) creates no notification")
    void approvalByNonMember_shouldCreateNothing() throws Exception {
        mockMvc.perform(post(ApiPaths.approvals(projectId, jobId))
                        .with(as(outsiderId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("description", "Let me in"))))
                .andExpect(status().isForbidden());

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("Deciding an already decided approval (409) creates no second APPROVAL_DECIDED")
    void conflictingDecision_shouldCreateNothing() throws Exception {
        UUID approvalId = requestApproval(assigneeId, "Need a new transformer");
        decide(approvalId, ownerId, "APPROVED");

        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId))
                        .with(as(adminId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", "REJECTED"))))
                .andExpect(status().isConflict());

        assertThat(recipients(NotificationType.APPROVAL_DECIDED)).containsExactly(assigneeId);
    }

    @Test
    @DisplayName("An event published in a transaction that rolls back is never handled; a committed one is")
    void rolledBackTransaction_shouldCreateNothing() {
        NoteAddedEvent event = new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, ownerId);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });
        assertThat(allNotificationCount()).isZero();

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        assertThat(recipients(NotificationType.NOTE_ADDED)).containsExactly(assigneeId);
    }

    // --- Fan-out edge cases ---

    @Test
    @DisplayName("A system event (no actor) notifies the recipients and has no actor")
    void systemEvent_shouldNotifyWithoutActor() throws Exception {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, null)));

        assertThat(recipients(NotificationType.NOTE_ADDED)).containsExactly(assigneeId);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actor").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload.actorName").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload.jobFriendlyId").value("JOB-276"));
    }

    @Test
    @DisplayName("A job deleted before fan-out notifies no one")
    void deletedJob_shouldNotifyNoOne() {
        dsl.update(JOBS).set(JOBS.DELETED_AT, LocalDateTime.now(ZoneOffset.UTC)).where(JOBS.ID.eq(jobId)).execute();

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new NoteAddedEvent(UUID.randomUUID(), projectId, jobId, ownerId)));

        assertThat(allNotificationCount()).isZero();
    }

    // --- Helpers ---

    private UUID user(String email, String name) {
        UUID id = UUID.randomUUID();
        users.put(id, userRepository.save(UserModel.builder().id(id).email(email).name(name).build()));
        return id;
    }

    private UUID addMember(UUID userId, ProjectMemberRole role) {
        return projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(userId).role(role).build()).getId();
    }

    private UUID addonId(String key) {
        return addonRepository.findAll().stream()
                .filter(a -> a.getKey().equals(key)).findFirst().orElseThrow().getId();
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

    private UUID requestApproval(UUID callerId, String description) throws Exception {
        String response = mockMvc.perform(post(ApiPaths.approvals(projectId, jobId))
                        .with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("description", description))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void decide(UUID approvalId, UUID callerId, String decision) throws Exception {
        mockMvc.perform(patch(ApiPaths.approvalStatus(projectId, jobId, approvalId))
                        .with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("status", decision))))
                .andExpect(status().isOk());
    }

    private void addNote(UUID callerId, String content) throws Exception {
        mockMvc.perform(post(ApiPaths.notes(projectId, jobId))
                        .with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("content", content))))
                .andExpect(status().isCreated());
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
