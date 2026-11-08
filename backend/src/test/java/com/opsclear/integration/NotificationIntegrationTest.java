package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.dto.MarkReadRequest;
import com.opsclear.model.NotificationModel;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.scheduler.NotificationCleanupJob;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification centre REST API (ADR-0051): list, unread count, mark read/unread, mark all read, the read-time
 * visibility filter, and the retention cleanup job, against a real Postgres. Nothing creates notifications yet
 * (fan-out is Backend 2), so rows are seeded through {@link NotificationRepository#insertBatch}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Notification REST API, visibility filter and cleanup")
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationCleanupJob notificationCleanupJob;
    @Autowired private JobRepository jobRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID callerId;
    private UUID otherId;
    private UUID actorId;
    private UUID orgId;
    private UUID projectId;
    private UUID leftProjectId;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        cleanUp();

        callerId = UUID.randomUUID();
        otherId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(callerId).email("caller@example.com").name("Cara Caller").build());
        userRepository.save(UserModel.builder().id(otherId).email("other@example.com").name("Oscar Other").build());
        userRepository.save(UserModel.builder().id(actorId).email("actor@example.com").name("Alice Example").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Notify Org").slug("NTF").createdBy(actorId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, actorId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, callerId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, otherId, OrganisationRole.MEMBER);

        projectId = projectRepository.save(ProjectModel.builder().name("Notify Project")
                .friendlyId("PRJ-012").ownerId(actorId).organisationId(orgId).build()).getId();
        addMember(projectId, actorId, ProjectMemberRole.OWNER);
        addMember(projectId, callerId, ProjectMemberRole.MEMBER);
        addMember(projectId, otherId, ProjectMemberRole.MEMBER);

        // The caller was never (or is no longer) a member of this one.
        leftProjectId = projectRepository.save(ProjectModel.builder().name("Left Project")
                .friendlyId("PRJ-013").ownerId(actorId).organisationId(orgId).build()).getId();
        addMember(leftProjectId, actorId, ProjectMemberRole.OWNER);

        jobId = dsl.insertInto(JOBS)
                .set(JOBS.PROJECT_ID, projectId)
                .set(JOBS.TITLE, "Fix login")
                .set(JOBS.STATUS, "BLOCKED")
                .set(JOBS.CREATED_BY, actorId)
                .set(JOBS.FRIENDLY_ID, "JOB-245")
                .returning(JOBS.ID)
                .fetchSingle()
                .getId();
    }

    // Other integration classes only clean some tables, so leave nothing of ours behind.
    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        jobRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- Helpers ---

    private void addMember(UUID pid, UUID userId, ProjectMemberRole role) {
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(pid).userId(userId).role(role).build());
    }

    private RequestPostProcessor as(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("email", userId + "@example.com"));
    }

    private NotificationModel.NotificationModelBuilder base(UUID recipientId, NotificationType type) {
        return NotificationModel.builder().orgId(orgId).recipientId(recipientId).type(type);
    }

    private UUID seed(NotificationModel notification) {
        return notificationRepository.insertBatch(List.of(notification)).getFirst().getId();
    }

    // Org-level notification for the caller, created minutesAgo ago.
    private UUID seedOrgLevel(UUID recipientId, int minutesAgo, Instant readAt) {
        return seed(base(recipientId, NotificationType.SUBSCRIPTION_PAST_DUE)
                .createdAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES)).readAt(readAt).build());
    }

    private UUID seedProject(UUID recipientId, UUID pid, NotificationType type, int minutesAgo, Instant readAt) {
        return seed(base(recipientId, type).projectId(pid)
                .createdAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES)).readAt(readAt).build());
    }

    private LocalDateTime readAtInDb(UUID id) {
        return dsl.select(NOTIFICATIONS.READ_AT).from(NOTIFICATIONS).where(NOTIFICATIONS.ID.eq(id)).fetchSingle()
                .value1();
    }

    private String body(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // --- List ---

    @Test
    @DisplayName("GET list returns the caller's notifications newest first with the ADR-0051 shape")
    void list_shouldReturnNewestFirst_withShape() throws Exception {
        UUID older = seedOrgLevel(callerId, 60, null);
        UUID newest = seed(base(callerId, NotificationType.JOB_BLOCKED)
                .actorId(actorId).projectId(projectId).jobId(jobId)
                .payload("{\"jobTitle\":\"Fix login\",\"blockReason\":\"Waiting for client\"}")
                .createdAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build());
        UUID middle = seedProject(callerId, projectId, NotificationType.NOTE_ADDED, 30, Instant.now());

        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.items[0].id").value(newest.toString()))
                .andExpect(jsonPath("$.items[0].type").value("JOB_BLOCKED"))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].actor.id").value(actorId.toString()))
                .andExpect(jsonPath("$.items[0].actor.name").value("Alice Example"))
                .andExpect(jsonPath("$.items[0].projectFriendlyId").value("PRJ-012"))
                .andExpect(jsonPath("$.items[0].jobFriendlyId").value("JOB-245"))
                .andExpect(jsonPath("$.items[0].approvalId").value(nullValue()))
                .andExpect(jsonPath("$.items[0].payload.jobTitle").value("Fix login"))
                .andExpect(jsonPath("$.items[0].payload.blockReason").value("Waiting for client"))
                .andExpect(jsonPath("$.items[1].id").value(middle.toString()))
                .andExpect(jsonPath("$.items[1].read").value(true))
                .andExpect(jsonPath("$.items[2].id").value(older.toString()))
                .andExpect(jsonPath("$.items[2].actor").value(nullValue()))
                .andExpect(jsonPath("$.items[2].projectFriendlyId").value(nullValue()))
                .andExpect(jsonPath("$.items[2].payload").isMap());
    }

    @Test
    @DisplayName("GET list returns only the caller's own notifications")
    void list_shouldExcludeOtherUsersNotifications() throws Exception {
        UUID mine = seedOrgLevel(callerId, 10, null);
        seedOrgLevel(otherId, 5, null);
        seedProject(otherId, projectId, NotificationType.JOB_ASSIGNED, 1, null);

        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(mine.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET list paginates newest first with page and size")
    void list_shouldPaginate() throws Exception {
        List<UUID> newestFirst = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            newestFirst.add(seedOrgLevel(callerId, i, null));
        }

        mockMvc.perform(get(ApiPaths.notifications("page=1&size=2")).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(newestFirst.get(2).toString()))
                .andExpect(jsonPath("$.items[1].id").value(newestFirst.get(3).toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get(ApiPaths.notifications("page=2&size=2")).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(newestFirst.get(4).toString()));

        mockMvc.perform(get(ApiPaths.notifications("page=9&size=2")).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    @DisplayName("GET list with unread=true returns only unread notifications")
    void list_shouldFilterUnread() throws Exception {
        UUID unread = seedOrgLevel(callerId, 10, null);
        seedOrgLevel(callerId, 5, Instant.now());

        mockMvc.perform(get(ApiPaths.notifications("unread=true")).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(unread.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get(ApiPaths.notifications("unread=false")).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    @DisplayName("GET list with type= returns only that type, combinable with unread")
    void list_shouldFilterByType() throws Exception {
        UUID note = seedProject(callerId, projectId, NotificationType.NOTE_ADDED, 10, null);
        seedProject(callerId, projectId, NotificationType.NOTE_ADDED, 8, Instant.now());
        seedProject(callerId, projectId, NotificationType.JOB_ASSIGNED, 5, null);

        mockMvc.perform(get(ApiPaths.notifications("type=NOTE_ADDED")).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].type").value("NOTE_ADDED"))
                .andExpect(jsonPath("$.items[1].type").value("NOTE_ADDED"));

        mockMvc.perform(get(ApiPaths.notifications("type=NOTE_ADDED&unread=true")).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(note.toString()));
    }

    @Test
    @DisplayName("GET list rejects an unknown type, a negative page and an out-of-range size with 400")
    void list_shouldRejectInvalidParams() throws Exception {
        mockMvc.perform(get(ApiPaths.notifications("type=NOPE")).with(as(callerId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiPaths.notifications("page=-1")).with(as(callerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page must be 0 or greater"));
        mockMvc.perform(get(ApiPaths.notifications("size=0")).with(as(callerId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiPaths.notifications("size=101")).with(as(callerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
    }

    @Test
    @DisplayName("GET list without authentication is 401")
    void list_shouldRequireAuth() throws Exception {
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a notification seeded without payload or createdAt gets {} and the current time")
    void insertBatch_shouldDefaultPayloadAndCreatedAt() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        NotificationModel saved = notificationRepository.insertBatch(List.of(
                base(callerId, NotificationType.INVITE_ACCEPTED).build())).getFirst();

        assertThat(saved.getPayload()).isEqualTo("{}");
        assertThat(saved.getCreatedAt()).isAfter(before);
        assertThat(saved.getOrgId()).isEqualTo(orgId);
        assertThat(saved.getRecipientId()).isEqualTo(callerId);
        assertThat(notificationRepository.insertBatch(List.of())).isEmpty();

        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].payload").isMap())
                .andExpect(jsonPath("$.items[0].payload").isEmpty());
    }

    @Test
    @DisplayName("insertBatch inserts several rows in one call and returns them newest first")
    void insertBatch_shouldInsertSeveral() {
        Instant now = Instant.now();
        List<NotificationModel> saved = notificationRepository.insertBatch(List.of(
                base(callerId, NotificationType.JOB_STATUS_CHANGED).projectId(projectId).jobId(jobId)
                        .actorId(actorId).createdAt(now.minusSeconds(60)).build(),
                base(otherId, NotificationType.JOB_STATUS_CHANGED).projectId(projectId).jobId(jobId)
                        .actorId(actorId).createdAt(now).build()));

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getRecipientId()).isEqualTo(otherId);
        assertThat(saved.get(0).getJobFriendlyId()).isEqualTo("JOB-245");
        assertThat(saved.get(1).getRecipientId()).isEqualTo(callerId);
        assertThat(dsl.fetchCount(NOTIFICATIONS)).isEqualTo(2);
    }

    // --- Unread count ---

    @Test
    @DisplayName("GET unread-count counts the caller's unread notifications only")
    void unreadCount_shouldCountCallersUnread() throws Exception {
        seedOrgLevel(callerId, 3, null);
        seedProject(callerId, projectId, NotificationType.JOB_ASSIGNED, 2, null);
        seedOrgLevel(callerId, 1, Instant.now());
        seedOrgLevel(otherId, 1, null);

        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    // --- Visibility filter ---

    @Test
    @DisplayName("a user removed from a project no longer sees or counts its notifications; org-level ones still show")
    void visibility_shouldHideProjectNotifications_afterRemoval() throws Exception {
        UUID orgLevel = seedOrgLevel(callerId, 10, null);
        UUID projectScoped = seedProject(callerId, projectId, NotificationType.JOB_ASSIGNED, 5, null);
        seedProject(callerId, leftProjectId, NotificationType.PROJECT_MEMBER_ADDED, 20, null);

        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(projectScoped.toString()))
                .andExpect(jsonPath("$.items[1].id").value(orgLevel.toString()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(callerId)))
                .andExpect(jsonPath("$.count").value(2));

        UUID membershipId = projectMemberRepository.findByProjectIdAndUserId(projectId, callerId)
                .orElseThrow().getId();
        projectMemberRepository.delete(membershipId);

        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(orgLevel.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(callerId)))
                .andExpect(jsonPath("$.count").value(1));
        // Hidden, not deleted: retention prunes it later.
        assertThat(dsl.fetchCount(NOTIFICATIONS, NOTIFICATIONS.ID.eq(projectScoped))).isEqualTo(1);
    }

    @Test
    @DisplayName("a user removed from the org (still in project_members) sees and marks nothing from it")
    void visibility_shouldHideEverything_afterOrgRemoval() throws Exception {
        UUID orgLevel = seedOrgLevel(callerId, 10, null);
        UUID projectScoped = seedProject(callerId, projectId, NotificationType.JOB_ASSIGNED, 5, null);
        organisationRepository.deleteMember(orgId, callerId);
        assertThat(projectMemberRepository.existsByProjectIdAndUserId(projectId, callerId)).isTrue();

        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(callerId)))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(patch(ApiPaths.notification(orgLevel)).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(true))))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(ApiPaths.notification(projectScoped)).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(true))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(ApiPaths.NOTIFICATIONS_READ_ALL).with(as(callerId)))
                .andExpect(status().isNoContent());

        assertThat(readAtInDb(orgLevel)).isNull();
        assertThat(readAtInDb(projectScoped)).isNull();
    }

    @Test
    @DisplayName("notifications of a soft-deleted project are hidden from list, count, PATCH and read-all")
    void visibility_shouldHideSoftDeletedProjectNotifications() throws Exception {
        UUID orgLevel = seedOrgLevel(callerId, 10, null);
        UUID deletedProjects = seedProject(callerId, projectId, NotificationType.NOTE_ADDED, 5, null);
        dsl.update(PROJECTS).set(PROJECTS.DELETED_AT, LocalDateTime.now()).where(PROJECTS.ID.eq(projectId)).execute();

        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(callerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(orgLevel.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(callerId)))
                .andExpect(jsonPath("$.count").value(1));
        mockMvc.perform(patch(ApiPaths.notification(deletedProjects)).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(true))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(ApiPaths.NOTIFICATIONS_READ_ALL).with(as(callerId)))
                .andExpect(status().isNoContent());

        assertThat(readAtInDb(orgLevel)).isNotNull();
        assertThat(readAtInDb(deletedProjects)).isNull();
    }

    // --- Mark read / unread ---

    @Test
    @DisplayName("PATCH read=true marks a notification read, read=false marks it unread again")
    void markRead_shouldToggleReadState() throws Exception {
        UUID id = seedProject(callerId, projectId, NotificationType.JOB_BLOCKED, 5, null);

        mockMvc.perform(patch(ApiPaths.notification(id)).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.projectFriendlyId").value("PRJ-012"));
        assertThat(readAtInDb(id)).isNotNull();

        mockMvc.perform(patch(ApiPaths.notification(id)).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(false));
        assertThat(readAtInDb(id)).isNull();
    }

    @Test
    @DisplayName("PATCH read=true on an already read notification keeps its read_at")
    void markRead_shouldKeepReadAt_whenAlreadyRead() throws Exception {
        Instant readAt = Instant.now().minus(Duration.ofHours(3)).truncatedTo(ChronoUnit.SECONDS);
        UUID id = seedOrgLevel(callerId, 300, readAt);
        LocalDateTime before = readAtInDb(id);

        mockMvc.perform(patch(ApiPaths.notification(id)).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        assertThat(readAtInDb(id)).isEqualTo(before);
    }

    @Test
    @DisplayName("PATCH another user's notification is 404 and leaves it unchanged")
    void markRead_shouldReturn404_forOtherUsersNotification() throws Exception {
        UUID othersId = seedOrgLevel(otherId, 5, null);

        mockMvc.perform(patch(ApiPaths.notification(othersId)).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Notification not found"));
        assertThat(readAtInDb(othersId)).isNull();
    }

    @Test
    @DisplayName("PATCH an unknown notification ID is 404")
    void markRead_shouldReturn404_forRandomId() throws Exception {
        mockMvc.perform(patch(ApiPaths.notification(UUID.randomUUID())).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(true))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH a notification hidden by the visibility filter is 404")
    void markRead_shouldReturn404_whenHiddenByVisibility() throws Exception {
        UUID hidden = seedProject(callerId, leftProjectId, NotificationType.PROJECT_MEMBER_ADDED, 5, null);

        mockMvc.perform(patch(ApiPaths.notification(hidden)).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(true))))
                .andExpect(status().isNotFound());
        assertThat(readAtInDb(hidden)).isNull();
    }

    @Test
    @DisplayName("PATCH without read, or with a malformed ID, is 400")
    void markRead_shouldReturn400_forInvalidInput() throws Exception {
        UUID id = seedOrgLevel(callerId, 5, null);

        mockMvc.perform(patch(ApiPaths.notification(id)).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("read: read is required"));
        mockMvc.perform(patch(ApiPaths.notification("not-a-uuid")).with(as(callerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new MarkReadRequest(true))))
                .andExpect(status().isBadRequest());
        assertThat(readAtInDb(id)).isNull();
    }

    // --- Mark all read ---

    @Test
    @DisplayName("POST read-all marks all of the caller's unread notifications read and no one else's")
    void markAllRead_shouldAffectOnlyCaller() throws Exception {
        UUID a = seedOrgLevel(callerId, 3, null);
        UUID b = seedProject(callerId, projectId, NotificationType.NOTE_ADDED, 2, null);
        Instant earlier = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
        UUID alreadyRead = seedOrgLevel(callerId, 120, earlier);
        LocalDateTime alreadyReadAt = readAtInDb(alreadyRead);
        UUID others = seedProject(otherId, projectId, NotificationType.NOTE_ADDED, 2, null);
        // Hidden by the visibility filter (caller isn't in this project), so read-all must leave it alone.
        UUID hidden = seedProject(callerId, leftProjectId, NotificationType.PROJECT_MEMBER_ADDED, 4, null);

        mockMvc.perform(post(ApiPaths.NOTIFICATIONS_READ_ALL).with(as(callerId)))
                .andExpect(status().isNoContent());

        assertThat(readAtInDb(hidden)).isNull();
        assertThat(readAtInDb(a)).isNotNull();
        assertThat(readAtInDb(b)).isNotNull();
        assertThat(readAtInDb(alreadyRead)).isEqualTo(alreadyReadAt);
        assertThat(readAtInDb(others)).isNull();
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(callerId)))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(otherId)))
                .andExpect(jsonPath("$.count").value(1));
    }

    // --- Retention cleanup ---

    @Test
    @DisplayName("cleanup deletes read notifications older than 30 days and any older than 90 days, keeps newer")
    void cleanup_shouldApplyRetention() {
        Instant now = Instant.now();
        UUID read31 = seed(base(callerId, NotificationType.NOTE_ADDED)
                .createdAt(now.minus(Duration.ofDays(31))).readAt(now.minus(Duration.ofDays(30))).build());
        UUID read29 = seed(base(callerId, NotificationType.NOTE_ADDED)
                .createdAt(now.minus(Duration.ofDays(29))).readAt(now.minus(Duration.ofDays(28))).build());
        UUID unread89 = seed(base(callerId, NotificationType.NOTE_ADDED)
                .createdAt(now.minus(Duration.ofDays(89))).build());
        UUID unread91 = seed(base(callerId, NotificationType.NOTE_ADDED)
                .createdAt(now.minus(Duration.ofDays(91))).build());
        UUID read91 = seed(base(otherId, NotificationType.NOTE_ADDED)
                .createdAt(now.minus(Duration.ofDays(91))).readAt(now.minus(Duration.ofDays(90))).build());
        UUID fresh = seed(base(otherId, NotificationType.NOTE_ADDED).createdAt(now).build());

        notificationCleanupJob.run();

        List<UUID> remaining = dsl.select(NOTIFICATIONS.ID).from(NOTIFICATIONS).fetch(NOTIFICATIONS.ID);
        assertThat(remaining).containsExactlyInAnyOrder(read29, unread89, fresh);
        assertThat(remaining).doesNotContain(read31, unread91, read91);
    }
}
