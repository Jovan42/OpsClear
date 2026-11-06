package com.opsclear.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
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
import com.opsclear.repository.SubscriptionAddonRepository;
import com.opsclear.repository.SubscriptionTierRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.JOB_STATUS_HISTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Velocity stats (ADR-0050) against a real Postgres: JobStatsRepository's CTE query and StatsService's
 * aggregation on seeded job_status_history (service level, fixed clock), and the
 * {@code GET /api/projects/{projectId}/stats} endpoint (MockMvc, mock JWT, history seeded relative to now).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Velocity stats query, service and endpoint")
class StatsIntegrationTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId BELGRADE = ZoneId.of("Europe/Belgrade");
    // Wednesday; 4 weeks in UTC = weeks of 2026-10-12, 10-19, 10-26 and 11-02 (current).
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final LocalDate WEEK_1 = LocalDate.parse("2026-10-12");
    private static final LocalDate WEEK_2 = LocalDate.parse("2026-10-19");
    private static final LocalDate WEEK_3 = LocalDate.parse("2026-10-26");
    private static final LocalDate WEEK_4 = LocalDate.parse("2026-11-02");
    private static final String PROJECT_FRIENDLY_ID = "PRJ-001";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
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
    @Autowired private SubscriptionAddonRepository addonRepository;
    @Autowired private SubscriptionTierRepository tierRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID memberId;
    private UUID formerId;
    private UUID outsiderId;
    private UUID orgId;
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
        // In the org but not in the project.
        outsiderId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(outsiderId).email("outsider@example.com").name("Otto Outsider")
                .build());

        OrganisationModel org = organisationRepository.save(
                OrganisationModel.builder().name("Stats Org").slug("STA").createdBy(ownerId).build());
        orgId = org.getId();
        organisationRepository.saveMember(orgId, ownerId, OrganisationRole.OWNER);
        organisationRepository.saveMember(orgId, memberId, OrganisationRole.MEMBER);
        organisationRepository.saveMember(orgId, outsiderId, OrganisationRole.MEMBER);
        friendlyIdRepository.seedForOrg(orgId);

        ProjectModel project = projectRepository.save(ProjectModel.builder().name("Stats Project")
                .friendlyId(PROJECT_FRIENDLY_ID).ownerId(ownerId).organisationId(orgId).build());
        projectId = project.getId();
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(ownerId).role(ProjectMemberRole.OWNER).build());
        projectMemberRepository.save(ProjectMemberModel.builder()
                .projectId(projectId).userId(memberId).role(ProjectMemberRole.MEMBER).build());

        subscribe(Set.of("DASHBOARD"));
    }

    // Real (webhook-confirmed) billing with the given add-ons, as RequiresAddonAspect requires.
    private void subscribe(Set<String> addonKeys) {
        subscriptionRepository.deleteAll();
        Set<UUID> addonIds = addonRepository.findAll().stream()
                .filter(a -> addonKeys.contains(a.getKey()))
                .map(a -> a.getId())
                .collect(Collectors.toSet());
        UUID tierId = tierRepository.findAll().getFirst().getId();
        subscriptionRepository.create(orgId, tierId, "MONTHLY", addonIds);
        subscriptionRepository.updateFromPaddleWebhook(orgId, "sub_test_" + orgId, "ACTIVE", null, null);
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
    @DisplayName("a job completed on its deadline day is not late; completed after that day it is (JOB-320)")
    void stats_shouldCountLate_onlyWhenCompletedAfterDeadlineDay() {
        // completed during the deadline day (JOB-245 on prod) → on time
        completedJob("2026-10-19T09:00:00", "2026-10-20T15:29:00", "2026-10-20T00:00:00", memberId);
        // completed at the next midnight UTC → late
        completedJob("2026-10-19T09:00:00", "2026-10-21T00:00:00", "2026-10-20T00:00:00", memberId);
        // completed the day after the deadline day → late
        completedJob("2026-10-19T09:00:00", "2026-10-21T15:00:00", "2026-10-20T00:00:00", memberId);
        // completed before the deadline day → on time
        completedJob("2026-10-19T09:00:00", "2026-10-20T15:00:00", "2026-10-21T00:00:00", memberId);
        completedJob("2026-10-19T09:00:00", "2026-10-20T15:00:00", null, memberId);

        StatsResult result = ownerStats(UTC);

        assertThat(result.getSummary().getCompletedCount()).isEqualTo(5);
        assertThat(result.getSummary().getCompletedWithDeadline()).isEqualTo(4);
        assertThat(result.getSummary().getCompletedLate()).isEqualTo(2);
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

    @Test
    @DisplayName("a job completed without going through In Progress has a lead time but no cycle time")
    void stats_shouldSkipCycleTime_forJobNeverInProgress() {
        UUID direct = insertJob(JobStatus.COMPLETED, "2026-10-19T09:00:00", null, memberId);
        history(direct, null, JobStatus.NEW, "2026-10-19T09:00:00");
        history(direct, JobStatus.NEW, JobStatus.COMPLETED, "2026-10-20T09:00:00");

        StatsResult onlyDirect = ownerStats(UTC);

        assertThat(onlyDirect.getSummary().getCompletedCount()).isEqualTo(1);
        assertThat(onlyDirect.getSummary().getAvgLeadTimeSeconds()).isEqualTo(86_400L);
        assertThat(onlyDirect.getSummary().getAvgCycleTimeSeconds()).isNull();

        // Started 2 days before completion: the cycle average counts only this job.
        completedJob("2026-10-18T09:00:00", "2026-10-20T09:00:00", null, memberId);

        StatsResult mixed = ownerStats(UTC);

        assertThat(mixed.getSummary().getCompletedCount()).isEqualTo(2);
        assertThat(mixed.getSummary().getAvgCycleTimeSeconds()).isEqualTo(172_800L);
    }

    @Test
    @DisplayName("an OWNER can filter by a member: only that member's jobs, no members list")
    void stats_shouldFilterByMember_forOwner() {
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, memberId);
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, ownerId);

        StatsResult result = statsService.getProjectStats(projectId, ownerId, memberId, 4, UTC, NOW);

        assertThat(result.getMemberId()).isEqualTo(memberId);
        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
        assertThat(result.getMembers()).isNull();
    }

    @Test
    @DisplayName("a MEMBER may pass their own memberId")
    void stats_shouldAllowMemberToPassOwnId() {
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, memberId);
        completedJob("2026-10-19T09:00:00", "2026-10-20T09:00:00", null, ownerId);

        StatsResult result = statsService.getProjectStats(projectId, memberId, memberId, 4, UTC, NOW);

        assertThat(result.getMemberId()).isEqualTo(memberId);
        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("as of now, with no weeks or zone: 12 weeks in UTC, current week last")
    void stats_shouldUseDefaults_asOfNow() {
        // Completed at the current second, so it's in the current week even just after Monday 00:00 UTC.
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String completedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC).toString();
        String createdAt = LocalDateTime.ofInstant(now.minus(1, ChronoUnit.HOURS), ZoneOffset.UTC).toString();
        completedJob(createdAt, completedAt, null, memberId);

        StatsResult result = statsService.getProjectStats(projectId, ownerId, null, null, null);

        assertThat(result.getWeeks()).isEqualTo(12);
        assertThat(result.getTimezone()).isEqualTo("UTC");
        assertThat(result.getBuckets()).hasSize(12);
        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
        assertThat(result.getBuckets().get(11).getCompletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("weeks outside 4, 12 and 26 are rejected")
    void stats_shouldRejectInvalidWeeks() {
        assertThatThrownBy(() -> statsService.getProjectStats(projectId, ownerId, null, 5, UTC, NOW))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("an unknown project is not found")
    void stats_shouldThrowNotFound_forUnknownProject() {
        assertThatThrownBy(() -> statsService.getProjectStats(UUID.randomUUID(), ownerId, null, 4, UTC, NOW))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("a user who isn't a project member is forbidden")
    void stats_shouldThrowForbidden_forNonMember() {
        assertThatThrownBy(() -> statsService.getProjectStats(projectId, formerId, null, 4, UTC, NOW))
                .isInstanceOf(ForbiddenException.class);
    }

    // =====================================================================================================
    // Endpoint: GET /api/projects/{projectId}/stats
    // The endpoint uses the real clock, so history is seeded relative to now. base() is two weeks back:
    // inside the 4-, 12- and 26-week ranges whatever the weekday, with room for a few days of history after it.
    // =====================================================================================================

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private static Instant base() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(14, ChronoUnit.DAYS);
    }

    private static String ts(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toString();
    }

    private static String mondayOf(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }

    // Mock JWT carrying the seeded user's email and name, so UserSyncFilter keeps them unchanged.
    private RequestPostProcessor as(UUID userId) {
        UserModel user = userRepository.findById(userId).orElseThrow();
        return jwt().jwt(j -> j.subject(userId.toString())
                .claim("email", user.getEmail())
                .claim("name", user.getName()));
    }

    private JsonNode getStats(String url, UUID callerId) throws Exception {
        String body = mockMvc.perform(get(url).with(as(callerId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static int completedInBuckets(JsonNode response) {
        int sum = 0;
        for (JsonNode bucket : response.get("buckets")) {
            sum += bucket.get("completedCount").asInt();
        }
        return sum;
    }

    private static JsonNode bucketFor(JsonNode response, String weekStart) {
        for (JsonNode bucket : response.get("buckets")) {
            if (bucket.get("weekStart").asText().equals(weekStart)) {
                return bucket;
            }
        }
        throw new AssertionError("No bucket for week " + weekStart);
    }

    private static List<String> memberNames(JsonNode response) {
        List<String> names = new ArrayList<>();
        response.get("members").forEach(m -> names.add(m.get("name").asText()));
        return names;
    }

    // Buckets are consecutive Mondays, oldest first, as many as requested.
    private static void assertWeeklyBuckets(JsonNode response, int weeks) {
        JsonNode buckets = response.get("buckets");
        assertThat(buckets).hasSize(weeks);
        LocalDate first = LocalDate.parse(buckets.get(0).get("weekStart").asText());
        assertThat(first.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        for (int i = 0; i < weeks; i++) {
            assertThat(buckets.get(i).get("weekStart").asText()).isEqualTo(first.plusWeeks(i).toString());
        }
    }

    // Completed job with the simplest history, timestamps as instants.
    private UUID completedJobAt(Instant createdAt, Instant completedAt, Instant deadline, UUID assignedTo) {
        return completedJob(ts(createdAt), ts(completedAt), deadline != null ? ts(deadline) : null, assignedTo);
    }

    private UUID addProjectMember(String name, ProjectMemberRole role) {
        UUID id = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(id).email(id + "@example.com").name(name).build());
        organisationRepository.saveMember(orgId, id, OrganisationRole.MEMBER);
        projectMemberRepository.save(ProjectMemberModel.builder().projectId(projectId).userId(id).role(role).build());
        return id;
    }

    @Test
    @DisplayName("GET stats as OWNER with defaults: 12 weekly buckets in UTC, summary metrics and members")
    void getStats_shouldReturnTeamStats_forOwnerWithDefaults() throws Exception {
        Instant base = base();
        // Member's job: 2h waiting in New, 4h blocked, completed 52h after creation, before its deadline.
        UUID memberJob = insertJob(JobStatus.COMPLETED, ts(base), ts(base.plus(DAY.multipliedBy(3))), memberId);
        history(memberJob, null, JobStatus.NEW, ts(base));
        history(memberJob, JobStatus.NEW, JobStatus.IN_PROGRESS, ts(base.plus(HOUR.multipliedBy(2))));
        history(memberJob, JobStatus.IN_PROGRESS, JobStatus.BLOCKED, ts(base.plus(HOUR.multipliedBy(26))));
        history(memberJob, JobStatus.BLOCKED, JobStatus.IN_PROGRESS, ts(base.plus(HOUR.multipliedBy(30))));
        Instant memberCompletedAt = base.plus(HOUR.multipliedBy(52));
        history(memberJob, JobStatus.IN_PROGRESS, JobStatus.COMPLETED, ts(memberCompletedAt));
        // Owner's job: started at creation, completed 24h later, the day after its deadline day (late).
        completedJobAt(base, base.plus(DAY), base.truncatedTo(ChronoUnit.DAYS), ownerId);
        int historyRows = dsl.fetchCount(JOB_STATUS_HISTORY);

        JsonNode response = getStats(ApiPaths.stats(projectId), ownerId);

        assertThat(response.get("weeks").asInt()).isEqualTo(12);
        assertThat(response.get("timezone").asText()).isEqualTo("UTC");
        assertThat(response.get("memberId").isNull()).isTrue();
        JsonNode summary = response.get("summary");
        assertThat(summary.get("completedCount").asInt()).isEqualTo(2);
        assertThat(summary.get("avgLeadTimeSeconds").asLong()).isEqualTo(38 * 3600L);   // (52h + 24h) / 2
        assertThat(summary.get("avgCycleTimeSeconds").asLong()).isEqualTo(37 * 3600L);  // (50h + 24h) / 2
        assertThat(summary.get("avgBlockedSeconds").asLong()).isEqualTo(2 * 3600L);     // (4h + 0) / 2
        assertThat(summary.get("completedWithDeadline").asInt()).isEqualTo(2);
        assertThat(summary.get("completedLate").asInt()).isEqualTo(1);
        assertThat(summary.get("lateRate").asDouble()).isEqualTo(0.5);
        assertWeeklyBuckets(response, 12);
        assertThat(completedInBuckets(response)).isEqualTo(2);
        JsonNode memberWeek = bucketFor(response, mondayOf(memberCompletedAt, UTC));
        assertThat(memberWeek.get("completedCount").asInt()).isPositive();
        assertThat(memberNames(response)).containsExactly("Mia Member", "Olivia Owner");
        JsonNode mia = response.get("members").get(0);
        assertThat(mia.get("userId").asText()).isEqualTo(memberId.toString());
        assertThat(mia.get("completedCount").asInt()).isEqualTo(1);
        assertThat(mia.get("avgLeadTimeSeconds").asLong()).isEqualTo(52 * 3600L);
        assertThat(mia.get("avgCycleTimeSeconds").asLong()).isEqualTo(50 * 3600L);
        assertThat(mia.get("avgBlockedSeconds").asLong()).isEqualTo(4 * 3600L);
        assertThat(mia.get("completedWithDeadline").asInt()).isEqualTo(1);
        assertThat(mia.get("completedLate").asInt()).isZero();
        assertThat(mia.get("lateRate").asDouble()).isEqualTo(0.0);
        // Read-only: nothing written.
        assertThat(dsl.fetchCount(JOB_STATUS_HISTORY)).isEqualTo(historyRows);
    }

    @Test
    @DisplayName("GET stats with nothing completed: zero counts, null averages and lateRate, empty weeks as zeros")
    void getStats_shouldReturnZerosAndNulls_whenNothingCompleted() throws Exception {
        mockMvc.perform(get(ApiPaths.stats(projectId)).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.completedCount").value(0))
                .andExpect(jsonPath("$.summary.avgLeadTimeSeconds").value(nullValue()))
                .andExpect(jsonPath("$.summary.avgCycleTimeSeconds").value(nullValue()))
                .andExpect(jsonPath("$.summary.avgBlockedSeconds").value(nullValue()))
                .andExpect(jsonPath("$.summary.lateRate").value(nullValue()))
                .andExpect(jsonPath("$.buckets.length()").value(12))
                .andExpect(jsonPath("$.buckets[0].completedCount").value(0))
                .andExpect(jsonPath("$.buckets[0].avgLeadTimeSeconds").value(nullValue()))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].completedCount").value(0))
                .andExpect(jsonPath("$.members[0].lateRate").value(nullValue()));
    }

    @Test
    @DisplayName("GET stats with weeks=4 and weeks=26: bucket count and range follow weeks")
    void getStats_shouldFollowWeeks_for4And26() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        completedJobAt(now.minus(HOUR), now.minus(Duration.ofMinutes(1)), null, memberId);
        // About 8.5 weeks back: outside the 4-week range, inside 26 weeks.
        completedJobAt(now.minus(DAY.multipliedBy(61)), now.minus(DAY.multipliedBy(60)), null, memberId);

        JsonNode four = getStats(ApiPaths.statsWeeks(projectId, "4"), ownerId);
        assertThat(four.get("weeks").asInt()).isEqualTo(4);
        assertWeeklyBuckets(four, 4);
        assertThat(four.get("summary").get("completedCount").asInt()).isEqualTo(1);

        JsonNode twentySix = getStats(ApiPaths.statsWeeks(projectId, "26"), ownerId);
        assertThat(twentySix.get("weeks").asInt()).isEqualTo(26);
        assertWeeklyBuckets(twentySix, 26);
        assertThat(twentySix.get("summary").get("completedCount").asInt()).isEqualTo(2);
        assertThat(completedInBuckets(twentySix)).isEqualTo(2);
    }

    @Test
    @DisplayName("GET stats with weeks outside 4, 12 and 26 returns 400")
    void getStats_shouldReturn400_forInvalidWeeks() throws Exception {
        mockMvc.perform(get(ApiPaths.statsWeeks(projectId, "5")).with(as(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Stats.INVALID_WEEKS));
        mockMvc.perform(get(ApiPaths.statsWeeks(projectId, "0")).with(as(ownerId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET stats with a non-numeric weeks or a malformed memberId returns 400")
    void getStats_shouldReturn400_forUnparseableParams() throws Exception {
        mockMvc.perform(get(ApiPaths.statsWeeks(projectId, "abc")).with(as(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'weeks'"));
        mockMvc.perform(get(ApiPaths.stats(projectId, null, null, "not-a-uuid")).with(as(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'memberId'"));
    }

    @Test
    @DisplayName("GET stats with an invalid tz returns 400")
    void getStats_shouldReturn400_forInvalidTimezone() throws Exception {
        mockMvc.perform(get(ApiPaths.statsTz(projectId, "Mars/Olympus_Mons")).with(as(ownerId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Stats.INVALID_TIMEZONE));
        mockMvc.perform(get(ApiPaths.statsTz(projectId, "")).with(as(ownerId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET stats validates weeks and tz before membership: a non-member with invalid params gets 400")
    void getStats_shouldReturn400BeforeMembershipCheck_forInvalidParams() throws Exception {
        mockMvc.perform(get(ApiPaths.statsWeeks(projectId, "5")).with(as(outsiderId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ApiPaths.statsTz(projectId, "Nowhere/Land")).with(as(outsiderId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET stats with a valid non-UTC tz echoes it back and buckets weeks in that zone")
    void getStats_shouldEchoTimezone_andBucketInIt() throws Exception {
        Instant base = base();
        Instant completedAt = base.plus(DAY);
        completedJobAt(base, completedAt, null, memberId);

        JsonNode response = getStats(ApiPaths.stats(projectId, "4", "Europe/Belgrade", null), ownerId);

        assertThat(response.get("timezone").asText()).isEqualTo("Europe/Belgrade");
        assertThat(response.get("weeks").asInt()).isEqualTo(4);
        assertWeeklyBuckets(response, 4);
        assertThat(bucketFor(response, mondayOf(completedAt, BELGRADE)).get("completedCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET stats excludes a reopened job that is still open and counts a re-completed job once")
    void getStats_shouldExcludeReopened_andCountRecompletedOnce() throws Exception {
        Instant base = base();
        UUID stillOpen = insertJob(JobStatus.IN_PROGRESS, ts(base), null, memberId);
        history(stillOpen, null, JobStatus.NEW, ts(base));
        history(stillOpen, JobStatus.NEW, JobStatus.IN_PROGRESS, ts(base.plus(HOUR)));
        history(stillOpen, JobStatus.IN_PROGRESS, JobStatus.COMPLETED, ts(base.plus(DAY)));
        history(stillOpen, JobStatus.COMPLETED, JobStatus.IN_PROGRESS, ts(base.plus(DAY.multipliedBy(2))));

        UUID recompleted = insertJob(JobStatus.COMPLETED, ts(base), null, memberId);
        history(recompleted, null, JobStatus.NEW, ts(base));
        history(recompleted, JobStatus.NEW, JobStatus.IN_PROGRESS, ts(base.plus(HOUR)));
        history(recompleted, JobStatus.IN_PROGRESS, JobStatus.COMPLETED, ts(base.plus(DAY)));
        history(recompleted, JobStatus.COMPLETED, JobStatus.IN_PROGRESS, ts(base.plus(DAY.multipliedBy(2))));
        Instant finalCompletion = base.plus(DAY.multipliedBy(5));
        history(recompleted, JobStatus.IN_PROGRESS, JobStatus.COMPLETED, ts(finalCompletion));

        JsonNode response = getStats(ApiPaths.stats(projectId), ownerId);

        assertThat(response.get("summary").get("completedCount").asInt()).isEqualTo(1);
        assertThat(response.get("summary").get("avgLeadTimeSeconds").asLong()).isEqualTo(5 * 86_400L);
        assertThat(response.get("summary").get("avgCycleTimeSeconds").asLong()).isEqualTo(5 * 86_400L - 3600L);
        assertThat(completedInBuckets(response)).isEqualTo(1);
        assertThat(bucketFor(response, mondayOf(finalCompletion, UTC)).get("completedCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET stats counts an unassigned job in the summary but not in members")
    void getStats_shouldCountUnassignedInSummaryOnly() throws Exception {
        Instant base = base();
        completedJobAt(base, base.plus(DAY), null, memberId);
        completedJobAt(base, base.plus(DAY), null, null);

        JsonNode response = getStats(ApiPaths.stats(projectId), ownerId);

        assertThat(response.get("summary").get("completedCount").asInt()).isEqualTo(2);
        assertThat(memberNames(response)).containsExactly("Mia Member", "Olivia Owner");
        int attributed = 0;
        for (JsonNode m : response.get("members")) {
            attributed += m.get("completedCount").asInt();
        }
        assertThat(attributed).isEqualTo(1);
    }

    @Test
    @DisplayName("GET stats as OWNER with memberId: only that member's jobs, members null")
    void getStats_shouldFilterByMember_forOwner() throws Exception {
        Instant base = base();
        completedJobAt(base, base.plus(DAY), null, memberId);
        completedJobAt(base, base.plus(DAY), null, ownerId);

        mockMvc.perform(get(ApiPaths.statsForMember(projectId, memberId)).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId.toString()))
                .andExpect(jsonPath("$.summary.completedCount").value(1))
                .andExpect(jsonPath("$.members").value(nullValue()));
    }

    @Test
    @DisplayName("GET stats as ADMIN: team view with members, and filtered by memberId with members null")
    void getStats_shouldShowTeamAndFilter_forAdmin() throws Exception {
        UUID adminId = addProjectMember("Ada Admin", ProjectMemberRole.ADMIN);
        Instant base = base();
        completedJobAt(base, base.plus(DAY), null, memberId);
        completedJobAt(base, base.plus(DAY), null, ownerId);

        JsonNode team = getStats(ApiPaths.stats(projectId), adminId);
        assertThat(team.get("summary").get("completedCount").asInt()).isEqualTo(2);
        assertThat(memberNames(team)).containsExactly("Ada Admin", "Mia Member", "Olivia Owner");

        mockMvc.perform(get(ApiPaths.statsForMember(projectId, ownerId)).with(as(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(ownerId.toString()))
                .andExpect(jsonPath("$.summary.completedCount").value(1))
                .andExpect(jsonPath("$.members").value(nullValue()));
    }

    @Test
    @DisplayName("GET stats as MEMBER without memberId: own stats only, memberId set to self, members null")
    void getStats_shouldReturnOwnStats_forMember() throws Exception {
        Instant base = base();
        completedJobAt(base, base.plus(DAY), null, memberId);
        completedJobAt(base, base.plus(DAY), null, ownerId);
        completedJobAt(base, base.plus(DAY), null, null);

        mockMvc.perform(get(ApiPaths.stats(projectId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId.toString()))
                .andExpect(jsonPath("$.summary.completedCount").value(1))
                .andExpect(jsonPath("$.buckets.length()").value(12))
                .andExpect(jsonPath("$.members").value(nullValue()));
    }

    @Test
    @DisplayName("GET stats as MEMBER with their own memberId returns 200")
    void getStats_shouldReturn200_forMemberWithOwnId() throws Exception {
        Instant base = base();
        completedJobAt(base, base.plus(DAY), null, memberId);
        completedJobAt(base, base.plus(DAY), null, ownerId);

        mockMvc.perform(get(ApiPaths.statsForMember(projectId, memberId)).with(as(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId.toString()))
                .andExpect(jsonPath("$.summary.completedCount").value(1))
                .andExpect(jsonPath("$.members").value(nullValue()));
    }

    @Test
    @DisplayName("GET stats as MEMBER with another member's id returns 403")
    void getStats_shouldReturn403_forMemberWithOtherId() throws Exception {
        mockMvc.perform(get(ApiPaths.statsForMember(projectId, ownerId)).with(as(memberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Stats.MEMBER_FILTER_NOT_ALLOWED));
    }

    @Test
    @DisplayName("GET stats as an org user who isn't a project member returns 403, like the dashboard")
    void getStats_shouldReturn403_forNonMember() throws Exception {
        mockMvc.perform(get(ApiPaths.stats(projectId)).with(as(outsiderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Member.NOT_A_MEMBER));
    }

    @Test
    @DisplayName("GET stats for an unknown project returns 404")
    void getStats_shouldReturn404_forUnknownProject() throws Exception {
        mockMvc.perform(get(ApiPaths.stats(UUID.randomUUID())).with(as(ownerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorMessages.Project.NOT_FOUND));
    }

    @Test
    @DisplayName("GET stats without the DASHBOARD add-on returns 403")
    void getStats_shouldReturn403_withoutDashboardAddon() throws Exception {
        subscribe(Set.of());

        mockMvc.perform(get(ApiPaths.stats(projectId)).with(as(ownerId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("DASHBOARD add-on is required to access this feature"));
    }

    @Test
    @DisplayName("GET stats without authentication returns 401")
    void getStats_shouldReturn401_withoutAuth() throws Exception {
        mockMvc.perform(get(ApiPaths.stats(projectId)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET stats accepts the friendly project ID (PRJ-xxx)")
    void getStats_shouldResolveFriendlyProjectId() throws Exception {
        Instant base = base();
        completedJobAt(base, base.plus(DAY), null, memberId);

        mockMvc.perform(get(ApiPaths.stats(PROJECT_FRIENDLY_ID)).with(as(ownerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.completedCount").value(1))
                .andExpect(jsonPath("$.members.length()").value(2));
    }
}
