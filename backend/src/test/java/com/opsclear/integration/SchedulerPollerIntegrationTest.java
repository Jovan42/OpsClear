package com.opsclear.integration;

import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.UserModel;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.JobTemplateRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.RecurringScheduleRepository;
import com.opsclear.repository.ScheduleAssigneeRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.scheduler.SchedulerPoller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SchedulerPoller Integration")
class SchedulerPollerIntegrationTest {

    @Autowired private SchedulerPoller poller;
    @Autowired private RecurringScheduleRepository scheduleRepository;
    @Autowired private ScheduleAssigneeRepository assigneeRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private JobTemplateRepository jobTemplateRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID projectId;
    private UUID templateId;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        assigneeRepository.deleteAll();
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        scheduleRepository.deleteAll();
        jobTemplateRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(ownerId).email("poller@example.com").name("Owner").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Poller Org").slug("PLR").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Poller Project").ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());

        JobTemplateModel template = jobTemplateRepository.save(JobTemplateModel.builder()
                .friendlyId("TPL-001").projectId(projectId).name("Deploy Checklist")
                .title("Deploy {{date}}").description("Monthly run: {{month}} {{year}} #{{occurrence}}")
                .assigneeMode("NONE").createdBy(ownerId).build());
        templateId = template.getId();
    }

    @Test
    @DisplayName("processSchedule — creates job and advances schedule in real DB (normal run)")
    void processSchedule_shouldCreateJobAndAdvanceSchedule_onNormalRun() {
        // nextRunAt = 09:00; now = 10:00; cron fires at noon → nextOccurrence (12:00) is future → NORMAL RUN
        Instant previousRunAt = Instant.parse("2026-01-01T09:00:00Z");
        Instant testNow       = Instant.parse("2026-01-01T10:00:00Z");

        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(projectId);
        row.setTemplateId(templateId);
        row.setName("Integration Poller Test");
        row.setCronExpression("0 0 12 * * *");
        row.setTimezone("UTC");
        row.setNextRunAt(previousRunAt.atOffset(ZoneOffset.UTC));
        row.setCreatedBy(ownerId);
        RecurringSchedulesRecord saved = scheduleRepository.insert(row);

        poller.processSchedule(saved, testNow);

        // Job should be created with title resolved from wildcards
        List<com.opsclear.model.JobModel> jobs = jobRepository.findByProjectIdAndDeletedAtIsNull(projectId);
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getSourceScheduleId()).isEqualTo(saved.getId());
        assertThat(jobs.get(0).getTitle()).contains("Deploy");

        // Schedule should be advanced: lastRunAt set, rotationIndex incremented
        RecurringSchedulesRecord updated = scheduleRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getCurrentRotationIndex()).isEqualTo(1);
        assertThat(updated.getLastRunAt()).isNotNull();
        assertThat(updated.getLastRunAt().toInstant()).isEqualTo(previousRunAt);
    }

    @Test
    @DisplayName("scheduleRepository.findDue — returns schedules whose next_run_at is past")
    void findDue_shouldReturnDueSchedules() {
        Instant past   = Instant.parse("2020-01-01T00:00:00Z");
        Instant future = Instant.parse("2099-01-01T00:00:00Z");

        RecurringSchedulesRecord dueRow = new RecurringSchedulesRecord();
        dueRow.setProjectId(projectId);
        dueRow.setTemplateId(templateId);
        dueRow.setName("Due");
        dueRow.setCronExpression("0 0 9 * * MON");
        dueRow.setTimezone("UTC");
        dueRow.setNextRunAt(past.atOffset(ZoneOffset.UTC));
        dueRow.setCreatedBy(ownerId);
        scheduleRepository.insert(dueRow);

        RecurringSchedulesRecord notDueRow = new RecurringSchedulesRecord();
        notDueRow.setProjectId(projectId);
        notDueRow.setTemplateId(templateId);
        notDueRow.setName("Not Due");
        notDueRow.setCronExpression("0 0 9 * * MON");
        notDueRow.setTimezone("UTC");
        notDueRow.setNextRunAt(future.atOffset(ZoneOffset.UTC));
        notDueRow.setCreatedBy(ownerId);
        scheduleRepository.insert(notDueRow);

        List<RecurringSchedulesRecord> due = scheduleRepository.findDue(Instant.now());

        assertThat(due).hasSize(1);
        assertThat(due.get(0).getName()).isEqualTo("Due");
    }

    @Test
    @DisplayName("processSchedule — skips without job when timezone is invalid")
    void processSchedule_shouldSkip_whenTimezoneIsInvalid() {
        Instant nextRunAt = Instant.parse("2026-01-01T09:00:00Z");
        Instant testNow   = Instant.parse("2026-01-01T10:00:00Z");

        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(projectId);
        row.setTemplateId(templateId);
        row.setName("Invalid TZ Test");
        row.setCronExpression("0 0 12 * * *");
        row.setTimezone("Not/ATimezone");
        row.setNextRunAt(nextRunAt.atOffset(ZoneOffset.UTC));
        row.setCreatedBy(ownerId);
        RecurringSchedulesRecord saved = scheduleRepository.insert(row);

        poller.processSchedule(saved, testNow);

        assertThat(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).isEmpty();
        assertThat(scheduleRepository.findById(saved.getId()).orElseThrow().getLastRunAt()).isNull();
    }

    @Test
    @DisplayName("processSchedule — skips without job when cron has no future occurrence")
    void processSchedule_shouldSkip_whenNoNextOccurrence() {
        // "0 0 9 31 2 *" = 09:00 on Feb 31 — never fires; CronExpression.next() returns null
        Instant nextRunAt = Instant.parse("2026-01-01T09:00:00Z");
        Instant testNow   = Instant.parse("2026-01-01T10:00:00Z");

        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(projectId);
        row.setTemplateId(templateId);
        row.setName("No-Occurrence Test");
        row.setCronExpression("0 0 9 31 2 *");
        row.setTimezone("UTC");
        row.setNextRunAt(nextRunAt.atOffset(ZoneOffset.UTC));
        row.setCreatedBy(ownerId);
        RecurringSchedulesRecord saved = scheduleRepository.insert(row);

        poller.processSchedule(saved, testNow);

        assertThat(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).isEmpty();
        assertThat(scheduleRepository.findById(saved.getId()).orElseThrow().getLastRunAt()).isNull();
    }

    @Test
    @DisplayName("processSchedule — advances schedule without creating a job on missed run")
    void processSchedule_shouldAdvanceWithoutJob_onMissedRun() {
        // nextRunAt=09:00; now=14:00; cron fires at noon → nextOccurrence(12:00) < now → MISSED RUN
        Instant previousRunAt = Instant.parse("2026-01-01T09:00:00Z");
        Instant testNow       = Instant.parse("2026-01-01T14:00:00Z");

        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(projectId);
        row.setTemplateId(templateId);
        row.setName("Missed Run Test");
        row.setCronExpression("0 0 12 * * *");
        row.setTimezone("UTC");
        row.setNextRunAt(previousRunAt.atOffset(ZoneOffset.UTC));
        row.setCreatedBy(ownerId);
        RecurringSchedulesRecord saved = scheduleRepository.insert(row);

        poller.processSchedule(saved, testNow);

        assertThat(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).isEmpty();
        RecurringSchedulesRecord updated = scheduleRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getLastRunAt()).isNotNull();
        assertThat(updated.getLastRunAt().toInstant()).isEqualTo(previousRunAt);
        assertThat(updated.getNextRunAt().toInstant()).isAfter(testNow);
    }

    @Test
    @DisplayName("processSchedule — skips without job when template has been soft-deleted")
    void processSchedule_shouldSkip_whenTemplateNotFound() {
        // Normal-run path (nextOccurrence=noon > testNow=10:00) reaches template lookup → null → return
        Instant nextRunAt = Instant.parse("2026-01-01T09:00:00Z");
        Instant testNow   = Instant.parse("2026-01-01T10:00:00Z");

        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(projectId);
        row.setTemplateId(templateId);
        row.setName("Missing Template Test");
        row.setCronExpression("0 0 12 * * *");
        row.setTimezone("UTC");
        row.setNextRunAt(nextRunAt.atOffset(ZoneOffset.UTC));
        row.setCreatedBy(ownerId);
        RecurringSchedulesRecord saved = scheduleRepository.insert(row);

        jobTemplateRepository.softDelete(templateId);

        poller.processSchedule(saved, testNow);

        assertThat(jobRepository.findByProjectIdAndDeletedAtIsNull(projectId)).isEmpty();
        assertThat(scheduleRepository.findById(saved.getId()).orElseThrow().getLastRunAt()).isNull();
    }

    @Test
    @DisplayName("tick — processes due schedules and advances them")
    void tick_shouldProcessDueSchedules_whenScheduleIsDue() {
        // nextRunAt 3 hours ago with hourly cron → findDue returns it → missed-run advances schedule
        Instant pastNextRunAt = Instant.now().minusSeconds(3 * 3600);

        RecurringSchedulesRecord row = new RecurringSchedulesRecord();
        row.setProjectId(projectId);
        row.setTemplateId(templateId);
        row.setName("Tick Test");
        row.setCronExpression("0 0 * * * *");
        row.setTimezone("UTC");
        row.setNextRunAt(pastNextRunAt.atOffset(ZoneOffset.UTC));
        row.setCreatedBy(ownerId);
        RecurringSchedulesRecord saved = scheduleRepository.insert(row);

        poller.tick();

        RecurringSchedulesRecord updated = scheduleRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getLastRunAt()).isNotNull();
        assertThat(updated.getNextRunAt().toInstant()).isAfter(Instant.now().minusSeconds(60));
    }
}
