package com.opsclear.integration;

import com.opsclear.exception.ForbiddenException;
import com.opsclear.model.JobStatsRow;
import com.opsclear.model.JobStatus;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.OrganisationRole;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.StatsBucket;
import com.opsclear.model.StatsMember;
import com.opsclear.model.StatsResult;
import com.opsclear.model.UserModel;
import com.opsclear.repository.BlockReasonRepository;
import com.opsclear.repository.FriendlyIdRepository;
import com.opsclear.repository.JobRepository;
import com.opsclear.repository.JobStatsRepository;
import com.opsclear.repository.JobStatusHistoryRepository;
import com.opsclear.repository.MilestoneRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import com.opsclear.repository.UserRepository;
import com.opsclear.service.StatsService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.JOB_STATUS_HISTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Velocity stats (ADR-0050) against a real Postgres: JobStatsRepository's CTE query and StatsService's
 * aggregation on seeded job_status_history. Service/repository level: the endpoint is a later job.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Velocity stats query and service")
class StatsIntegrationTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId BELGRADE = ZoneId.of("Europe/Belgrade");
    // Wednesday; 4 weeks in UTC = weeks of 2026-10-12, 10-19, 10-26 and 11-02 (current).
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final LocalDate WEEK_1 = LocalDate.parse("2026-10-12");
    private static final LocalDate WEEK_2 = LocalDate.parse("2026-10-19");
    private static final LocalDate WEEK_3 = LocalDate.parse("2026-10-26");
    private static final LocalDate WEEK_4 = LocalDate.parse("2026-11-02");

    @Autowired private StatsService statsService;
    @Autowired private JobStatsRepository jobStatsRepository;
    @Autowired private DSLContext dsl;
    @Autowired private JobRepository jobRepository;
    @Autowired private JobStatusHistoryRepository jobStatusHistoryRepository;
    @Autowired private BlockReasonRepository blockReasonRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private FriendlyIdRepository friendlyIdRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID formerId;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        cleanUp();

        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        formerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(ownerId).email("owner@example.com").name("Olivia Owner").build());
        userRepository.save(UserModel.builder().id(memberId).email("member@example.com").name("Mia Member").build());
        userRepository.save(UserModel.builder().id(formerId).email("former@example.com").name("Fred Former").build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Stats Org").slug("STA").createdBy(ownerId).build());
        organisationRepository.saveMember(org.getId(), ownerId, OrganisationRole.OWNER);
        friendlyIdRepository.seedForOrg(org.getId());

        ProjectModel project = projectRepository.save(
                ProjectModel.builder().name("Stats Project").ownerId(ownerId).organisationId(org.getId()).build());
        projectId = project.getId();
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());
    }

    // Subscription and other integration tests clean only some tables in their own setUp, so leave the
    // shared database without this class's jobs, history and project members.
    @AfterEach
    void cleanUp() {
        jobStatusHistoryRepository.deleteAll();
        jobRepository.deleteAll();
        blockReasonRepository.deleteAll();
        milestoneRepository.deleteAll();
        projectMemberRepository.deleteAll();
        projectRepository.deleteAll();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- Seeding helpers (UTC timestamps, as stored) ---

    private UUID insertJob(JobStatus status, String createdAt, String deadline, UUID assignedTo) {
        return dsl.insertInto(JOBS)
                .set(JOBS.PROJECT_ID, projectId)
                .set(JOBS.TITLE, "Job")
                .set(JOBS.STATUS, status.name())
                .set(JOBS.CREATED_BY, ownerId)
                .set(JOBS.CREATED_AT, LocalDateTime.parse(createdAt))
                .set(JOBS.DEADLINE, deadline != null ? LocalDateTime.parse(deadline) : null)
                .set(JOBS.ASSIGNED_TO, assignedTo)
                .returning(JOBS.ID)
                .fetchSingle()
                .getId();
    }

    private void history(UUID jobId, JobStatus from, JobStatus to, String changedAt) {
        dsl.insertInto(JOB_STATUS_HISTORY)
                .set(JOB_STATUS_HISTORY.JOB_ID, jobId)
                .set(JOB_STATUS_HISTORY.CHANGED_FROM, from != null ? from.name() : null)
                .set(JOB_STATUS_HISTORY.CHANGED_TO, to.name())
                .set(JOB_STATUS_HISTORY.CHANGED_BY, ownerId)
                .set(JOB_STATUS_HISTORY.CHANGED_AT, LocalDateTime.parse(changedAt))
                .execute();
    }

    // Created, started and completed: the simplest history of a completed job.
    private UUID completedJob(String createdAt, String completedAt, String deadline, UUID assignedTo) {
        UUID id = insertJob(JobStatus.COMPLETED, createdAt, deadline, assignedTo);
        history(id, null, JobStatus.NEW, createdAt);
        history(id, JobStatus.NEW, JobStatus.IN_PROGRESS, createdAt);
        history(id, JobStatus.IN_PROGRESS, JobStatus.COMPLETED, completedAt);
        return id;
    }

    private StatsResult ownerStats(ZoneId zone) {
        return statsService.getProjectStats(projectId, ownerId, null, 4, zone, NOW);
    }

    // --- Tests ---

    @Test
    @DisplayName("lead, cycle and blocked time match a known history")
    void stats_shouldComputeLeadCycleAndBlocked_forKnownHistory() {
        UUID jobId = insertJob(JobStatus.COMPLETED, "2026-10-20T08:00:00", "2026-10-23T00:00:00", memberId);
        history(jobId, null, JobStatus.NEW, "2026-10-20T08:00:00");
        history(jobId, JobStatus.NEW, JobStatus.IN_PROGRESS, "2026-10-20T10:00:00");
        history(jobId, JobStatus.IN_PROGRESS, JobStatus.BLOCKED, "2026-10-21T10:00:00");
        history(jobId, JobStatus.BLOCKED, JobStatus.IN_PROGRESS, "2026-10-21T14:00:00");  // 4h blocked
        history(jobId, JobStatus.IN_PROGRESS, JobStatus.BLOCKED, "2026-10-22T10:00:00");
        history(jobId, JobStatus.BLOCKED, JobStatus.IN_PROGRESS, "2026-10-22T11:00:00");  // 1h blocked
        history(jobId, JobStatus.IN_PROGRESS, JobStatus.COMPLETED, "2026-10-22T12:00:00");

        List<JobStatsRow> rows = jobStatsRepository.findCompletedInRange(projectId,
                Instant.parse("2026-10-12T00:00:00Z"), Instant.parse("2026-11-09T00:00:00Z"), UTC, null);

        assertThat(rows).hasSize(1);
        JobStatsRow row = rows.getFirst();
        assertThat(row.getJobId()).isEqualTo(jobId);
        assertThat(row.getAssignedTo()).isEqualTo(memberId);
        assertThat(row.getAssigneeName()).isEqualTo("Mia Member");
        assertThat(row.getCreatedAt()).isEqualTo(Instant.parse("2026-10-20T08:00:00Z"));
        assertThat(row.getFirstInProgressAt()).isEqualTo(Instant.parse("2026-10-20T10:00:00Z"));
        assertThat(row.getCompletedAt()).isEqualTo(Instant.parse("2026-10-22T12:00:00Z"));
        assertThat(row.getDeadline()).isEqualTo(Instant.parse("2026-10-23T00:00:00Z"));
        assertThat(row.getBlockedSeconds()).isEqualTo(5 * 3600L);
        assertThat(row.getWeekStart()).isEqualTo(WEEK_2);

        StatsResult result = ownerStats(UTC);
        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
        assertThat(result.getSummary().getAvgLeadTimeSeconds()).isEqualTo(52 * 3600L);
        assertThat(result.getSummary().getAvgCycleTimeSeconds()).isEqualTo(50 * 3600L);
        assertThat(result.getSummary().getAvgBlockedSeconds()).isEqualTo(5 * 3600L);
        assertThat(result.getSummary().getCompletedWithDeadline()).isEqualTo(1);
        assertThat(result.getSummary().getCompletedLate()).isZero();
        assertThat(result.getSummary().getLateRate()).isEqualTo(0.0);
        assertThat(result.getBuckets()).extracting(StatsBucket::getCompletedCount).containsExactly(0, 1, 0, 0);
    }

    @Test
    @DisplayName("reopened and still open is excluded; reopened and re-completed counts once, in the final week")
    void stats_shouldHandleReopenedJobs() {
        UUID stillOpen = insertJob(JobStatus.IN_PROGRESS, "2026-10-19T09:00:00", null, memberId);
        history(stillOpen, null, JobStatus.NEW, "2026-10-19T09:00:00");
        history(stillOpen, JobStatus.NEW, JobStatus.IN_PROGRESS, "2026-10-19T10:00:00");
        history(stillOpen, JobStatus.IN_PROGRESS, JobStatus.COMPLETED, "2026-10-21T10:00:00");
        history(stillOpen, JobStatus.COMPLETED, JobStatus.IN_PROGRESS, "2026-10-23T10:00:00");

        UUID recompleted = insertJob(JobStatus.COMPLETED, "2026-10-13T09:00:00", null, memberId);
        history(recompleted, null, JobStatus.NEW, "2026-10-13T09:00:00");
        history(recompleted, JobStatus.NEW, JobStatus.IN_PROGRESS, "2026-10-13T10:00:00");
        history(recompleted, JobStatus.IN_PROGRESS, JobStatus.COMPLETED, "2026-10-14T10:00:00");
        history(recompleted, JobStatus.COMPLETED, JobStatus.IN_PROGRESS, "2026-10-15T10:00:00");
        history(recompleted, JobStatus.IN_PROGRESS, JobStatus.BLOCKED, "2026-10-16T10:00:00");
        history(recompleted, JobStatus.BLOCKED, JobStatus.IN_PROGRESS, "2026-10-16T12:00:00");
        history(recompleted, JobStatus.IN_PROGRESS, JobStatus.COMPLETED, "2026-10-28T10:00:00");

        StatsResult result = ownerStats(UTC);

        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
        assertThat(result.getBuckets()).extracting(StatsBucket::getCompletedCount).containsExactly(0, 0, 1, 0);
        // Working time runs from the first IN_PROGRESS to the last completion, so the reopen counts.
        assertThat(result.getSummary().getAvgCycleTimeSeconds()).isEqualTo(15 * 24 * 3600L);
        assertThat(result.getSummary().getAvgLeadTimeSeconds()).isEqualTo(15 * 24 * 3600L + 3600L);
        assertThat(result.getSummary().getAvgBlockedSeconds()).isEqualTo(2 * 3600L);
    }

    @Test
    @DisplayName("a soft-deleted completed job is excluded")
    void stats_shouldExcludeSoftDeletedJobs() {
        UUID deleted = completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, memberId);
        dsl.update(JOBS).set(JOBS.DELETED_AT, LocalDateTime.parse("2026-10-21T09:00:00"))
                .where(JOBS.ID.eq(deleted)).execute();
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, memberId);

        StatsResult result = ownerStats(UTC);

        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("weeks bucket in the viewer's zone around midnight and across a DST change")
    void stats_shouldBucketWeeksInViewerZone_aroundMidnightAndDst() {
        // Europe/Belgrade: CEST (+2) until 2026-10-25 03:00, CET (+1) after.
        // Mon 10-12 00:30 CEST: first minutes of the range.
        completedJob("2026-10-01T09:00:00", "2026-10-11T22:30:00", null, memberId);
        // Sun 10-11 23:30 CEST: just before the range, excluded.
        completedJob("2026-10-01T09:00:00", "2026-10-11T21:30:00", null, memberId);
        // Mon 10-19 00:30 CEST (Sunday in UTC): week 10-19.
        completedJob("2026-10-01T09:00:00", "2026-10-18T22:30:00", null, memberId);
        // Sun 10-25 23:30 CET, after the DST change: still week 10-19 (a fixed +2 would push it to 10-26).
        completedJob("2026-10-01T09:00:00", "2026-10-25T22:30:00", null, memberId);
        // Mon 10-26 00:30 CET (Sunday in UTC): week 10-26.
        completedJob("2026-10-01T09:00:00", "2026-10-25T23:30:00", null, memberId);

        StatsResult belgrade = ownerStats(BELGRADE);

        assertThat(belgrade.getBuckets()).extracting(StatsBucket::getWeekStart)
                .containsExactly(WEEK_1, WEEK_2, WEEK_3, WEEK_4);
        assertThat(belgrade.getBuckets()).extracting(StatsBucket::getCompletedCount).containsExactly(1, 2, 1, 0);
        assertThat(belgrade.getSummary().getCompletedCount()).isEqualTo(4);

        // The same jobs in UTC fall into different weeks, and the 10-11 22:30 one is before the UTC range.
        StatsResult utc = ownerStats(UTC);
        assertThat(utc.getBuckets()).extracting(StatsBucket::getCompletedCount).containsExactly(1, 2, 0, 0);
    }

    @Test
    @DisplayName("a job completed on its deadline day is late (midnight-UTC deadline)")
    void stats_shouldCountCompletionOnDeadlineDayAsLate() {
        completedJob("2026-10-19T09:00:00", "2026-10-20T15:00:00", "2026-10-20T00:00:00", memberId);
        completedJob("2026-10-19T09:00:00", "2026-10-20T15:00:00", "2026-10-21T00:00:00", memberId);
        completedJob("2026-10-19T09:00:00", "2026-10-20T15:00:00", null, memberId);

        StatsResult result = ownerStats(UTC);

        assertThat(result.getSummary().getCompletedCount()).isEqualTo(3);
        assertThat(result.getSummary().getCompletedWithDeadline()).isEqualTo(2);
        assertThat(result.getSummary().getCompletedLate()).isEqualTo(1);
        assertThat(result.getSummary().getLateRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("members lists current members and former-member assignees; unassigned jobs count in totals only")
    void stats_shouldListMembers_andKeepUnassignedInTotalsOnly() {
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, memberId);
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, formerId);
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, null);

        StatsResult result = ownerStats(UTC);

        assertThat(result.getSummary().getCompletedCount()).isEqualTo(3);
        assertThat(result.getMembers()).extracting(StatsMember::getName)
                .containsExactly("Fred Former", "Mia Member", "Olivia Owner");
        assertThat(result.getMembers()).extracting(StatsMember::getCompletedCount).containsExactly(1, 1, 0);
    }

    @Test
    @DisplayName("a MEMBER sees only jobs assigned to them, and can't request another member's stats")
    void stats_shouldScopeMemberToOwnJobs() {
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, memberId);
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, ownerId);
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, null);

        StatsResult result = statsService.getProjectStats(projectId, memberId, null, 4, UTC, NOW);

        assertThat(result.getMemberId()).isEqualTo(memberId);
        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
        assertThat(result.getMembers()).isNull();
        assertThatThrownBy(() -> statsService.getProjectStats(projectId, memberId, ownerId, 4, UTC, NOW))
                .isInstanceOf(ForbiddenException.class);
    }
}
