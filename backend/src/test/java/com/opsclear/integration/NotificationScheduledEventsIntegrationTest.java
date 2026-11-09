package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.event.DeadlineAlertEvent;
import com.opsclear.event.JobAssignedEvent;
import com.opsclear.event.ScheduleRunMissedEvent;
import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.model.DeadlineAlertKind;
import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.NotificationType;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import com.opsclear.model.UserModel;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.JobDeadlineAlertRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.NotificationRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleAssigneeRepository;
import com.opsclear.repository.ScheduleMissedRunRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.scheduler.DeadlineAlertJob;
import com.opsclear.scheduler.SchedulerPoller;
import com.opsclear.service.NotificationService;
import org.jooq.DSLContext;
import org.assertj.core.groups.Tuple;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.JOB_DEADLINE_ALERTS;
import static com.opsclear.generated.jooq.Tables.NOTIFICATIONS;
import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Notification fan-out (ADR-0051, JOB-278) for the scheduled and system types: JOB_DEADLINE_SOON and JOB_OVERDUE from
 * {@link DeadlineAlertJob}, JOB_ASSIGNED for schedule-created jobs and SCHEDULE_RUN_MISSED from
 * {@link SchedulerPoller}. The scheduled components are called directly with a fixed clock; results are checked
 * through {@code GET /api/notifications} and the database. The test profile runs the notification executor
 * synchronously, so fan-out has finished when the call returns.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
@DisplayName("Notification fan-out: deadline alerts and recurring schedule events")
class NotificationScheduledEventsIntegrationTest {

    // The daily check runs at 06:00 UTC; "today" is 2026-11-10.
    private static final Instant NOW = Instant.parse("2026-11-10T06:00:00Z");
    private static final Instant TODAY = Instant.parse("2026-11-10T00:00:00Z");
    private static final Instant TOMORROW = Instant.parse("2026-11-11T00:00:00Z");
    private static final Instant DAY_AFTER_TOMORROW = Instant.parse("2026-11-12T00:00:00Z");
    private static final Instant YESTERDAY = Instant.parse("2026-11-09T00:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DSLContext dsl;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private DeadlineAlertJob deadlineAlertJob;
    @Autowired private SchedulerPoller poller;
    @Autowired private NotificationService notificationService;
    @Autowired private ApplicationEvents applicationEvents;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private JobDeadlineAlertRepository alertRepository;
    @Autowired private ScheduleAssigneeRepository assigneeRepository;
    @Autowired private ScheduleMissedRunRepository missedRunRepository;
    @Autowired private RecurringScheduleRepository scheduleRepository;
    @Autowired private JobTemplateRepository jobTemplateRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobRepository jobRepository;
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
    private UUID templateId;
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
                OrganisationModel.builder().name("Scheduled Org").slug("SCHED").createdBy(ownerId).build());
        UUID orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, adminId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, assigneeId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        friendlyIdRepository.seedForOrg(orgId);

        projectId = projectRepository.save(ProjectModel.builder().name("Website Redesign")
                .friendlyId("PRJ-278").ownerId(ownerId).organisationId(orgId).build()).getId();
        addMember(ownerId, ProjectMemberRole.OWNER);
        addMember(adminId, ProjectMemberRole.ADMIN);
        addMember(assigneeId, ProjectMemberRole.MEMBER);
        // A Member who isn't the assignee: can't see the jobs, so must never be notified about them.
        addMember(memberId, ProjectMemberRole.MEMBER);

        templateId = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-278").projectId(projectId).name("Weekly report").title("Weekly report {{date}}")
                .assigneeMode("NONE").createdBy(ownerId).build()).getId();
    }

    // Other integration classes only clean some tables, so leave nothing of ours behind.
    @AfterEach
    void cleanUp() {
        alertRepository.deleteAll();
        notificationRepository.deleteAll();
        assigneeRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        missedRunRepository.deleteAll();
        scheduleRepository.deleteAll();
        jobTemplateRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- JOB_DEADLINE_SOON ---

    @Test
    @DisplayName("Due tomorrow: one JOB_DEADLINE_SOON to the assignee, still one after running twice")
    void dueTomorrow_shouldNotifyAssigneeOnce() throws Exception {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, TOMORROW);

        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isEqualTo(1);
        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isZero();

        assertThat(recipients(NotificationType.JOB_DEADLINE_SOON)).containsExactly(assigneeId);
        assertThat(allNotificationCount()).isEqualTo(1);
        assertThat(alertRows()).containsExactly(tuple(jobId, "DEADLINE_SOON", TOMORROW));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("JOB_DEADLINE_SOON"))
                .andExpect(jsonPath("$.items[0].actor").doesNotExist())
                .andExpect(jsonPath("$.items[0].jobFriendlyId").value("JOB-001"))
                .andExpect(jsonPath("$.items[0].projectFriendlyId").value("PRJ-278"))
                .andExpect(jsonPath("$.items[0].payload.jobTitle").value("Fix login"))
                .andExpect(jsonPath("$.items[0].payload.projectName").value("Website Redesign"))
                .andExpect(jsonPath("$.items[0].payload.deadline").value("2026-11-11T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].payload.actorName").doesNotExist());
        // Owners/Admins aren't recipients of JOB_DEADLINE_SOON.
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("Due later on tomorrow's UTC date counts as tomorrow; the day after doesn't")
    void dueTomorrow_shouldUseUtcDate() {
        UUID lateTomorrow = job("JOB-001", "Fix login", JobStatus.NEW, assigneeId,
                TOMORROW.plus(Duration.ofHours(23)));
        job("JOB-002", "Write docs", JobStatus.NEW, assigneeId, DAY_AFTER_TOMORROW);

        deadlineAlertJob.checkDeadlines(NOW);

        assertThat(dsl.select(NOTIFICATIONS.JOB_ID).from(NOTIFICATIONS).fetch(NOTIFICATIONS.JOB_ID))
                .containsExactly(lateTomorrow);
    }

    // --- JOB_OVERDUE ---

    @Test
    @DisplayName("Due today: no JOB_OVERDUE yet (and no JOB_DEADLINE_SOON)")
    void dueToday_shouldNotAlertYet() {
        // The assignee and Owners/Admins would receive JOB_OVERDUE (see the due-yesterday test).
        job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, TODAY);

        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isZero();

        assertThat(allNotificationCount()).isZero();
        assertThat(alertRows()).isEmpty();
    }

    @Test
    @DisplayName("Due yesterday: JOB_OVERDUE to the assignee and the project's Owners/Admins, not other Members")
    void dueYesterday_shouldNotifyAssigneeAndOwnersAndAdmins() throws Exception {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.BLOCKED, assigneeId, YESTERDAY);

        deadlineAlertJob.checkDeadlines(NOW);
        deadlineAlertJob.checkDeadlines(NOW.plus(Duration.ofDays(1)));

        assertThat(recipients(NotificationType.JOB_OVERDUE))
                .containsExactlyInAnyOrder(assigneeId, ownerId, adminId);
        assertThat(allNotificationCount()).isEqualTo(3);
        assertThat(alertRows()).containsExactly(tuple(jobId, "OVERDUE", YESTERDAY));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("JOB_OVERDUE"))
                .andExpect(jsonPath("$.items[0].actor").doesNotExist())
                .andExpect(jsonPath("$.items[0].jobFriendlyId").value("JOB-001"))
                .andExpect(jsonPath("$.items[0].payload.deadline").value("2026-11-09T00:00:00Z"));
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("Moved deadline alerts again; each deadline value has its own job_deadline_alerts row")
    void movedDeadline_shouldAlertAgain() {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, TOMORROW);
        deadlineAlertJob.checkDeadlines(NOW);

        moveDeadline(jobId, DAY_AFTER_TOMORROW);
        deadlineAlertJob.checkDeadlines(NOW.plus(Duration.ofDays(1)));

        assertThat(recipients(NotificationType.JOB_DEADLINE_SOON)).containsExactly(assigneeId, assigneeId);
        assertThat(dsl.select(NOTIFICATIONS.PAYLOAD).from(NOTIFICATIONS).orderBy(NOTIFICATIONS.CREATED_AT)
                .fetch(r -> r.value1().data()))
                .anySatisfy(p -> assertThat(p).contains("2026-11-11T00:00:00Z"))
                .anySatisfy(p -> assertThat(p).contains("2026-11-12T00:00:00Z"));
        assertThat(alertRows()).containsExactly(
                tuple(jobId, "DEADLINE_SOON", TOMORROW),
                tuple(jobId, "DEADLINE_SOON", DAY_AFTER_TOMORROW));
    }

    @Test
    @DisplayName("Moved overdue deadline alerts JOB_OVERDUE again for the new value")
    void movedOverdueDeadline_shouldAlertAgain() {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, YESTERDAY);
        deadlineAlertJob.checkDeadlines(NOW);

        moveDeadline(jobId, TODAY);
        deadlineAlertJob.checkDeadlines(NOW.plus(Duration.ofDays(1)));

        assertThat(recipients(NotificationType.JOB_OVERDUE)).hasSize(6);
        assertThat(alertRows()).containsExactly(
                tuple(jobId, "OVERDUE", YESTERDAY),
                tuple(jobId, "OVERDUE", TODAY));
    }

    // --- Skipped jobs ---
    // Each job below is overdue and assigned, so the assignee and Owners/Admins would otherwise be notified.

    @Test
    @DisplayName("A completed job gets no deadline alert")
    void completedJob_shouldNotAlert() {
        job("JOB-001", "Fix login", JobStatus.COMPLETED, assigneeId, YESTERDAY);
        job("JOB-002", "Write docs", JobStatus.COMPLETED, assigneeId, TOMORROW);

        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isZero();

        assertThat(allNotificationCount()).isZero();
        assertThat(alertRows()).isEmpty();
    }

    @Test
    @DisplayName("A job in a completed project gets no deadline alert")
    void completedProject_shouldNotAlert() {
        job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, YESTERDAY);
        job("JOB-002", "Write docs", JobStatus.NEW, assigneeId, TOMORROW);
        completeProject();

        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isZero();

        assertThat(allNotificationCount()).isZero();
        assertThat(alertRows()).isEmpty();
    }

    @Test
    @DisplayName("A deleted job gets no deadline alert")
    void deletedJob_shouldNotAlert() {
        UUID overdueId = job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, YESTERDAY);
        UUID soonId = job("JOB-002", "Write docs", JobStatus.NEW, assigneeId, TOMORROW);
        softDelete(overdueId);
        softDelete(soonId);

        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isZero();

        assertThat(allNotificationCount()).isZero();
        assertThat(alertRows()).isEmpty();
    }

    @Test
    @DisplayName("A job in a deleted project gets no deadline alert")
    void deletedProject_shouldNotAlert() {
        job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, YESTERDAY);
        dsl.update(PROJECTS).set(PROJECTS.DELETED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(PROJECTS.ID.eq(projectId)).execute();

        assertThat(deadlineAlertJob.checkDeadlines(NOW)).isZero();

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("An unassigned job due tomorrow records the alert but notifies no one")
    void unassignedDueTomorrow_shouldNotifyNoOne() {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.NEW, null, TOMORROW);

        deadlineAlertJob.checkDeadlines(NOW);

        assertThat(allNotificationCount()).isZero();
        assertThat(alertRows()).containsExactly(tuple(jobId, "DEADLINE_SOON", TOMORROW));
    }

    // --- Mutes and visibility ---

    @Test
    @DisplayName("JOB_OVERDUE skips an Owner who muted it; the others still get it")
    void mutedOverdue_shouldSkipThatRecipient() throws Exception {
        job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, YESTERDAY);
        mute(ownerId, NotificationType.JOB_OVERDUE);

        deadlineAlertJob.checkDeadlines(NOW);

        assertThat(recipients(NotificationType.JOB_OVERDUE)).containsExactlyInAnyOrder(assigneeId, adminId);
    }

    @Test
    @DisplayName("JOB_DEADLINE_SOON skips an assignee who muted it, but the alert is still recorded")
    void mutedDeadlineSoon_shouldSkipAssignee() throws Exception {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, TOMORROW);
        mute(assigneeId, NotificationType.JOB_DEADLINE_SOON);

        deadlineAlertJob.checkDeadlines(NOW);

        assertThat(allNotificationCount()).isZero();
        assertThat(alertRows()).containsExactly(tuple(jobId, "DEADLINE_SOON", TOMORROW));
    }

    @Test
    @DisplayName("An assignee removed from the project (still assigned) gets no JOB_OVERDUE; Owners/Admins do")
    void removedAssignee_shouldNotBeNotified() {
        job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, YESTERDAY);
        projectMemberRepository.delete(
                projectMemberRepository.findByProjectIdAndUserId(projectId, assigneeId).orElseThrow().getId());

        deadlineAlertJob.checkDeadlines(NOW);

        assertThat(recipients(NotificationType.JOB_OVERDUE)).containsExactlyInAnyOrder(ownerId, adminId);
    }

    // --- Deadline alert state changed between the check and the fan-out ---
    // Fan-out is async in production, so it can see newer data than the event. Publishing the event directly with
    // stale data reproduces that. The assignee would otherwise get the alert (see dueTomorrow_shouldNotifyAssigneeOnce).

    @Test
    @DisplayName("A deadline alert is dropped when the job was completed before the fan-out")
    void staleAlert_jobCompleted_shouldNotifyNoOne() {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.COMPLETED, assigneeId, TOMORROW);

        publish(new DeadlineAlertEvent(jobId, projectId, DeadlineAlertKind.DEADLINE_SOON, TOMORROW));

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("A deadline alert is dropped when the deadline moved before the fan-out")
    void staleAlert_deadlineMoved_shouldNotifyNoOne() {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, DAY_AFTER_TOMORROW);

        publish(new DeadlineAlertEvent(jobId, projectId, DeadlineAlertKind.DEADLINE_SOON, TOMORROW));

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("A deadline alert is dropped when the project was completed before the fan-out")
    void staleAlert_projectCompleted_shouldNotifyNoOne() {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, TOMORROW);
        completeProject();

        publish(new DeadlineAlertEvent(jobId, projectId, DeadlineAlertKind.DEADLINE_SOON, TOMORROW));

        assertThat(allNotificationCount()).isZero();
    }

    @Test
    @DisplayName("A current deadline alert published directly is delivered (control for the stale cases)")
    void currentAlert_shouldNotifyAssignee() {
        UUID jobId = job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, TOMORROW);

        publish(new DeadlineAlertEvent(jobId, projectId, DeadlineAlertKind.DEADLINE_SOON, TOMORROW));

        assertThat(recipients(NotificationType.JOB_DEADLINE_SOON)).containsExactly(assigneeId);
    }

    @Test
    @DisplayName("run() checks deadlines against the current UTC date")
    void run_shouldAlertRelativeToNow() {
        Instant realTomorrow = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(Duration.ofDays(1));
        job("JOB-001", "Fix login", JobStatus.IN_PROGRESS, assigneeId, realTomorrow);

        deadlineAlertJob.run();

        assertThat(recipients(NotificationType.JOB_DEADLINE_SOON)).containsExactly(assigneeId);
    }

    // --- Schedule-created jobs: JOB_ASSIGNED ---

    @Test
    @DisplayName("A schedule-created job with a rotated assignee notifies them with JOB_ASSIGNED, no actor")
    void scheduleCreatedJob_shouldNotifyAssignee() throws Exception {
        RecurringSchedulesRecord schedule = schedule("0 0 12 * * *", Instant.parse("2026-01-01T09:00:00Z"));
        assigneeRepository.replaceForSchedule(schedule.getId(), List.of(assigneeId));

        poller.processSchedule(schedule, Instant.parse("2026-01-01T10:00:00Z"));

        JobModel created = jobRepository.findByProjectIdAndDeletedAtIsNull(projectId).getFirst();
        assertThat(created.getAssignedTo()).isEqualTo(assigneeId);
        assertThat(recipients(NotificationType.JOB_ASSIGNED)).containsExactly(assigneeId);
        assertThat(allNotificationCount()).isEqualTo(1);
        assertThat(dsl.select(NOTIFICATIONS.ACTOR_ID).from(NOTIFICATIONS).fetchOne(NOTIFICATIONS.ACTOR_ID)).isNull();
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(assigneeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("JOB_ASSIGNED"))
                .andExpect(jsonPath("$.items[0].actor").doesNotExist())
                .andExpect(jsonPath("$.items[0].jobFriendlyId").value(created.getFriendlyId()))
                .andExpect(jsonPath("$.items[0].payload.jobTitle").value("Weekly report 2026-01-01"))
                .andExpect(jsonPath("$.items[0].payload.actorName").doesNotExist());
    }

    @Test
    @DisplayName("A schedule-created job without an assignee notifies no one")
    void scheduleCreatedUnassignedJob_shouldNotifyNoOne() {
        RecurringSchedulesRecord schedule = schedule("0 0 12 * * *", Instant.parse("2026-01-01T09:00:00Z"));

        poller.processSchedule(schedule, Instant.parse("2026-01-01T10:00:00Z"));

        assertThat(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).hasSize(1);
        assertThat(allNotificationCount()).isZero();
        // No event at all: one with a null assignee would fail inside the async listener, where it's swallowed.
        assertThat(applicationEvents.stream(JobAssignedEvent.class)).isEmpty();
    }

    @Test
    @DisplayName("tick(): a due schedule's job is assigned and notified (the poller opens a transaction itself)")
    void tick_shouldNotifyAssigneeOfScheduleCreatedJob() {
        // Due a minute ago; the yearly cron's next occurrence is in the future, so this is a normal run.
        schedule("0 0 0 1 1 *", Instant.now().minus(Duration.ofMinutes(1)));
        assigneeRepository.replaceForSchedule(scheduleRepository.findByProjectId(projectId).getFirst().getId(),
                List.of(assigneeId));

        poller.tick();

        assertThat(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).hasSize(1);
        assertThat(recipients(NotificationType.JOB_ASSIGNED)).containsExactly(assigneeId);
    }

    // --- SCHEDULE_RUN_MISSED ---

    @Test
    @DisplayName("A missed run notifies the project's Owners/Admins with the schedule name and missed time")
    void missedRun_shouldNotifyOwnersAndAdmins() throws Exception {
        // Due 09:00, noon cron, polled at 13:00 → the 12:00 run was missed.
        RecurringSchedulesRecord schedule = schedule("0 0 12 * * *", Instant.parse("2026-01-01T09:00:00Z"));

        poller.processSchedule(schedule, Instant.parse("2026-01-01T13:00:00Z"));

        assertThat(missedRunRepository.findByScheduleId(schedule.getId())).hasSize(1);
        assertThat(recipients(NotificationType.SCHEDULE_RUN_MISSED)).containsExactlyInAnyOrder(ownerId, adminId);
        assertThat(allNotificationCount()).isEqualTo(2);
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].type").value("SCHEDULE_RUN_MISSED"))
                .andExpect(jsonPath("$.items[0].actor").doesNotExist())
                .andExpect(jsonPath("$.items[0].projectFriendlyId").value("PRJ-278"))
                .andExpect(jsonPath("$.items[0].jobFriendlyId").doesNotExist())
                .andExpect(jsonPath("$.items[0].payload.projectName").value("Website Redesign"))
                .andExpect(jsonPath("$.items[0].payload.scheduleName").value("Weekly report"))
                .andExpect(jsonPath("$.items[0].payload.missedAt").value("2026-01-01T12:00:00Z"));
        // Members (assignee or not) aren't recipients.
        mockMvc.perform(get(ApiPaths.NOTIFICATIONS_UNREAD_COUNT).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("tick(): a schedule that missed runs notifies Owners/Admins once per recorded missed run")
    void tick_shouldNotifyPerMissedRun() {
        // Due two days ago with a daily cron at the same time of day → two missed runs before now.
        Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        String cron = String.format("%d %d %d * * *", twoDaysAgo.atZone(ZoneOffset.UTC).getSecond(),
                twoDaysAgo.atZone(ZoneOffset.UTC).getMinute(), twoDaysAgo.atZone(ZoneOffset.UTC).getHour());
        RecurringSchedulesRecord schedule = schedule(cron, twoDaysAgo);

        poller.tick();

        assertThat(missedRunRepository.findByScheduleId(schedule.getId())).hasSize(2);
        assertThat(recipients(NotificationType.SCHEDULE_RUN_MISSED))
                .containsExactlyInAnyOrder(ownerId, adminId, ownerId, adminId);
    }

    @Test
    @DisplayName("SCHEDULE_RUN_MISSED skips an Admin who muted it; the Owner still gets it")
    void mutedMissedRun_shouldSkipThatRecipient() throws Exception {
        RecurringSchedulesRecord schedule = schedule("0 0 12 * * *", Instant.parse("2026-01-01T09:00:00Z"));
        mute(adminId, NotificationType.SCHEDULE_RUN_MISSED);

        poller.processSchedule(schedule, Instant.parse("2026-01-01T13:00:00Z"));

        assertThat(recipients(NotificationType.SCHEDULE_RUN_MISSED)).containsExactly(ownerId);
    }

    @Test
    @DisplayName("SCHEDULE_RUN_MISSED notifies no one when the project was deleted before the fan-out")
    void staleMissedRun_projectDeleted_shouldNotifyNoOne() {
        // Owner and Admin would otherwise be notified (see missedRun_shouldNotifyOwnersAndAdmins).
        UUID scheduleId = schedule("0 0 12 * * *", Instant.parse("2026-01-01T09:00:00Z")).getId();
        dsl.update(PROJECTS).set(PROJECTS.DELETED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(PROJECTS.ID.eq(projectId)).execute();

        // Called directly: through the async listener, a failure here would be swallowed and look like "no one".
        assertThat(notificationService.notifyScheduleRunMissed(new ScheduleRunMissedEvent(scheduleId, projectId,
                "Weekly report", Instant.parse("2026-01-01T12:00:00Z")))).isEmpty();

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
    private UUID job(String friendlyId, String title, JobStatus status, UUID assignedTo, Instant deadline) {
        return jobRepository.save(JobModel.builder().friendlyId(friendlyId).projectId(projectId).title(title)
                .status(status).priority(JobPriority.MEDIUM).assignedTo(assignedTo)
                .deadline(deadline).createdBy(ownerId).build()).getId();
    }

    private void moveDeadline(UUID jobId, Instant deadline) {
        dsl.update(JOBS).set(JOBS.DEADLINE, LocalDateTime.ofInstant(deadline, ZoneOffset.UTC))
                .where(JOBS.ID.eq(jobId)).execute();
    }

    private void softDelete(UUID jobId) {
        dsl.update(JOBS).set(JOBS.DELETED_AT, LocalDateTime.now(ZoneOffset.UTC)).where(JOBS.ID.eq(jobId)).execute();
    }

    private void completeProject() {
        dsl.update(PROJECTS).set(PROJECTS.STATUS, ProjectStatus.COMPLETED.name())
                .where(PROJECTS.ID.eq(projectId)).execute();
    }

    private RecurringSchedulesRecord schedule(String cron, Instant nextRunAt) {
        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(projectId);
        row.setTemplateId(templateId);
        row.setName("Weekly report");
        row.setCronExpression(cron);
        row.setTimezone("UTC");
        row.setNextRunAt(nextRunAt.atOffset(ZoneOffset.UTC));
        row.setCreatedBy(ownerId);
        return scheduleRepository.insert(row);
    }

    private RequestPostProcessor as(UUID userId) {
        UserModel user = users.get(userId);
        return jwt().jwt(j -> j.subject(userId.toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName()));
    }

    private void mute(UUID userId, NotificationType... types) throws Exception {
        List<String> muted = Arrays.stream(types).map(Enum::name).toList();
        mockMvc.perform(put(ApiPaths.NOTIFICATION_SETTINGS)
                        .with(as(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("muted", muted))))
                .andExpect(status().isOk());
    }

    private void publish(Object event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    private List<UUID> recipients(NotificationType type) {
        return dsl.select(NOTIFICATIONS.RECIPIENT_ID).from(NOTIFICATIONS)
                .where(NOTIFICATIONS.TYPE.eq(type.name()))
                .fetch(NOTIFICATIONS.RECIPIENT_ID);
    }

    private int allNotificationCount() {
        return dsl.fetchCount(NOTIFICATIONS);
    }

    // (job_id, kind, deadline) of every recorded alert, oldest deadline first.
    private List<Tuple> alertRows() {
        return dsl.select(JOB_DEADLINE_ALERTS.JOB_ID, JOB_DEADLINE_ALERTS.KIND, JOB_DEADLINE_ALERTS.DEADLINE)
                .from(JOB_DEADLINE_ALERTS)
                .orderBy(JOB_DEADLINE_ALERTS.DEADLINE)
                .fetch(r -> tuple(r.value1(), r.value2(), r.value3().toInstant(ZoneOffset.UTC)));
    }
}
