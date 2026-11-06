package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobStatsRow;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.StatsBucket;
import com.opsclear.model.StatsMember;
import com.opsclear.model.StatsResult;
import com.opsclear.model.StatsSummary;
import com.opsclear.repository.JobStatsRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService")
class StatsServiceTest {

    // Wednesday; with UTC and 4 weeks the buckets are 2026-10-12, 10-19, 10-26 and 11-02 (current, partial).
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final Instant NOW =Instant.parse("2026-11-04T10:00:00Z");
    private static final LocalDate WEEK_1 = LocalDate.parse("2026-10-12");
    private static final LocalDate WEEK_2 = LocalDate.parse("2026-10-19");
    private static final LocalDate WEEK_3 = LocalDate.parse("2026-10-26");
    private static final LocalDate WEEK_4 = LocalDate.parse("2026-11-02");
    private static final Instant RANGE_START_4W = Instant.parse("2026-10-12T00:00:00Z");
    private static final Instant RANGE_END = Instant.parse("2026-11-09T00:00:00Z");

    @Mock private JobStatsRepository jobStatsRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;

    private StatsService statsService;

    private UUID projectId;
    private UUID ownerId;
    private UUID adminId;
    private UUID memberId;
    private UUID otherMemberId;
    private ProjectMemberModel ownerMembership;
    private ProjectMemberModel adminMembership;
    private ProjectMemberModel memberMembership;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(jobStatsRepository, projectRepository, projectMemberRepository);

        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        otherMemberId = UUID.randomUUID();

        ownerMembership = membership(ownerId, "Olivia Owner", ProjectMemberRole.OWNER);
        adminMembership = membership(adminId, "Adam Admin", ProjectMemberRole.ADMIN);
        memberMembership = membership(memberId, "Mia Member", ProjectMemberRole.MEMBER);
    }

    private ProjectMemberModel membership(UUID userId, String name, ProjectMemberRole role) {
        return ProjectMemberModel.builder()
                .id(UUID.randomUUID()).projectId(projectId).userId(userId).userName(name).role(role).build();
    }

    private void givenProject() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId))
                .thenReturn(Optional.of(ProjectModel.builder().id(projectId).name("P").build()));
    }

    private void givenCaller(ProjectMemberModel membership) {
        givenProject();
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, membership.getUserId()))
                .thenReturn(Optional.of(membership));
    }

    private void givenRows(UUID assignedTo, List<JobStatsRow> rows) {
        when(jobStatsRepository.findCompletedInRange(
                eq(projectId), eq(RANGE_START_4W), eq(RANGE_END), eq(UTC), assignedTo == null
                        ? isNull() : eq(assignedTo)))
                .thenReturn(rows);
    }

    // Completed at the given instant; created, started and blocked relative to it, in seconds.
    private static JobStatsRow row(UUID assignedTo, String completedAt, long leadSeconds, Long cycleSeconds,
                                   long blockedSeconds, Instant deadline, LocalDate weekStart) {
        Instant completed = Instant.parse(completedAt);
        return JobStatsRow.builder()
                .jobId(UUID.randomUUID())
                .assignedTo(assignedTo)
                .createdAt(completed.minusSeconds(leadSeconds))
                .firstInProgressAt(cycleSeconds != null ? completed.minusSeconds(cycleSeconds) : null)
                .blockedSeconds(blockedSeconds)
                .completedAt(completed)
                .deadline(deadline)
                .weekStart(weekStart)
                .build();
    }

    // --- Guards ---

    @Test
    @DisplayName("getProjectStats — throws NotFoundException when the project doesn't exist")
    void getProjectStats_shouldThrowNotFound_whenProjectMissing() {
        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statsService.getProjectStats(projectId, ownerId, null, 4, UTC, NOW))
                .isInstanceOf(NotFoundException.class)
                .hasMessage(ErrorMessages.Project.NOT_FOUND);
        verifyNoInteractions(jobStatsRepository);
    }

    @Test
    @DisplayName("getProjectStats — throws ForbiddenException when the caller isn't a project member")
    void getProjectStats_shouldThrowForbidden_whenCallerNotMember() {
        givenProject();
        when(projectMemberRepository.findByProjectIdAndUserId(projectId, ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statsService.getProjectStats(projectId, ownerId, null, 4, UTC, NOW))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Member.NOT_A_MEMBER);
        verifyNoInteractions(jobStatsRepository);
    }

    @Test
    @DisplayName("getProjectStats — throws BadRequestException for weeks other than 4, 12 or 26")
    void getProjectStats_shouldThrowBadRequest_whenWeeksNotAllowed() {
        assertThatThrownBy(() -> statsService.getProjectStats(projectId, ownerId, null, 5, UTC, NOW))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(ErrorMessages.Stats.INVALID_WEEKS);
        verifyNoInteractions(projectRepository, jobStatsRepository);
    }

    // --- Role rules ---

    @Test
    @DisplayName("getProjectStats — MEMBER without memberId is forced to their own jobs, no members list")
    void getProjectStats_shouldForceMemberToSelf_whenMemberOmitsMemberId() {
        givenCaller(memberMembership);
        givenRows(memberId, List.of());

        StatsResult result = statsService.getProjectStats(projectId, memberId, null, 4, UTC, NOW);

        assertThat(result.getMemberId()).isEqualTo(memberId);
        assertThat(result.getMembers()).isNull();
        verify(projectMemberRepository, never()).findByProjectId(any());
    }

    @Test
    @DisplayName("getProjectStats — MEMBER may pass their own memberId")
    void getProjectStats_shouldAllowMember_whenMemberIdIsSelf() {
        givenCaller(memberMembership);
        givenRows(memberId, List.of());

        StatsResult result = statsService.getProjectStats(projectId, memberId, memberId, 4, UTC, NOW);

        assertThat(result.getMemberId()).isEqualTo(memberId);
        assertThat(result.getMembers()).isNull();
    }

    @Test
    @DisplayName("getProjectStats — MEMBER passing another user's memberId gets ForbiddenException")
    void getProjectStats_shouldThrowForbidden_whenMemberRequestsOtherMember() {
        givenCaller(memberMembership);

        assertThatThrownBy(() -> statsService.getProjectStats(
                projectId, memberId, otherMemberId, 4, UTC, NOW))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(ErrorMessages.Stats.MEMBER_FILTER_NOT_ALLOWED);
        verifyNoInteractions(jobStatsRepository);
    }

    @Test
    @DisplayName("getProjectStats — OWNER may filter by any memberId, no members list then")
    void getProjectStats_shouldFilterByMember_whenOwnerPassesMemberId() {
        givenCaller(ownerMembership);
        givenRows(otherMemberId, List.of(row(otherMemberId, "2026-11-03T12:00:00Z", 100, 50L, 0, null, WEEK_4)));

        StatsResult result = statsService.getProjectStats(
                projectId, ownerId, otherMemberId, 4, UTC, NOW);

        assertThat(result.getMemberId()).isEqualTo(otherMemberId);
        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
        assertThat(result.getMembers()).isNull();
        verify(projectMemberRepository, never()).findByProjectId(any());
    }

    @Test
    @DisplayName("getProjectStats — ADMIN may filter by memberId")
    void getProjectStats_shouldFilterByMember_whenAdminPassesMemberId() {
        givenCaller(adminMembership);
        givenRows(memberId, List.of());

        StatsResult result = statsService.getProjectStats(projectId, adminId, memberId, 4, UTC, NOW);

        assertThat(result.getMemberId()).isEqualTo(memberId);
        assertThat(result.getMembers()).isNull();
    }

    // --- Range ---

    @Test
    @DisplayName("getProjectStats — defaults to 12 weeks in UTC when weeks and zone are null")
    void getProjectStats_shouldDefaultTo12WeeksUtc_whenWeeksAndZoneNull() {
        givenCaller(ownerMembership);
        when(jobStatsRepository.findCompletedInRange(projectId, Instant.parse("2026-08-17T00:00:00Z"), RANGE_END,
                UTC, null)).thenReturn(List.of());
        when(projectMemberRepository.findByProjectId(projectId)).thenReturn(List.of(ownerMembership));

        StatsResult result = statsService.getProjectStats(projectId, ownerId, null, null, null, NOW);

        assertThat(result.getWeeks()).isEqualTo(12);
        assertThat(result.getTimezone()).isEqualTo("UTC");
        assertThat(result.getBuckets()).hasSize(12);
        assertThat(result.getBuckets().get(0).getWeekStart()).isEqualTo(LocalDate.parse("2026-08-17"));
        assertThat(result.getBuckets().get(11).getWeekStart()).isEqualTo(WEEK_4);
    }

    @Test
    @DisplayName("getProjectStats — computes the range from Monday 00:00 in the viewer's zone")
    void getProjectStats_shouldComputeRangeInViewerZone() {
        ZoneId belgrade = ZoneId.of("Europe/Belgrade");
        // Sunday 23:30 UTC = Monday 00:30 in Belgrade (CET, +1), so the current week already starts 2026-11-02.
        Instant now = Instant.parse("2026-11-01T23:30:00Z");
        givenCaller(ownerMembership);
        // Oldest week starts 2026-10-12 00:00 CEST (+2); the range ends 2026-11-09 00:00 CET (+1).
        when(jobStatsRepository.findCompletedInRange(projectId, Instant.parse("2026-10-11T22:00:00Z"),
                Instant.parse("2026-11-08T23:00:00Z"), belgrade, null)).thenReturn(List.of());
        when(projectMemberRepository.findByProjectId(projectId)).thenReturn(List.of());

        StatsResult result = statsService.getProjectStats(projectId, ownerId, null, 4, belgrade, now);

        assertThat(result.getTimezone()).isEqualTo("Europe/Belgrade");
        assertThat(result.getBuckets()).extracting(StatsBucket::getWeekStart)
                .containsExactly(WEEK_1, WEEK_2, WEEK_3, WEEK_4);
    }

    @Test
    @DisplayName("getProjectStats — overload without 'now' uses the current time")
    void getProjectStats_shouldUseCurrentTime_whenNowOmitted() {
        givenCaller(memberMembership);
        when(jobStatsRepository.findCompletedInRange(eq(projectId), any(), any(), eq(UTC), eq(memberId)))
                .thenReturn(List.of());

        StatsResult result = statsService.getProjectStats(projectId, memberId, null, 26, UTC);

        assertThat(result.getBuckets()).hasSize(26);
        assertThat(result.getBuckets().get(25).getWeekStart())
                .isBeforeOrEqualTo(LocalDate.now(UTC))
                .isAfter(LocalDate.now(UTC).minusWeeks(1));
    }

    // --- Metrics ---

    @Test
    @DisplayName("getProjectStats — averages lead, cycle and blocked time from per-job rows")
    void getProjectStats_shouldAverageLeadCycleAndBlocked() {
        givenCaller(memberMembership);
        givenRows(memberId, List.of(
                row(memberId, "2026-10-13T12:00:00Z", 100, 40L, 0, null, WEEK_1),
                row(memberId, "2026-10-14T12:00:00Z", 301, null, 61, null, WEEK_1)));

        StatsSummary summary = statsService
                .getProjectStats(projectId, memberId, null, 4, UTC, NOW).getSummary();

        assertThat(summary.getCompletedCount()).isEqualTo(2);
        assertThat(summary.getAvgLeadTimeSeconds()).isEqualTo(201L);      // (100 + 301) / 2 = 200.5, rounded
        assertThat(summary.getAvgCycleTimeSeconds()).isEqualTo(40L);      // only the job with an IN_PROGRESS entry
        assertThat(summary.getAvgBlockedSeconds()).isEqualTo(31L);        // (0 + 61) / 2 = 30.5, rounded
    }

    @Test
    @DisplayName("getProjectStats — counts late jobs among those with a deadline, excluding jobs without one")
    void getProjectStats_shouldCountLate_excludingJobsWithoutDeadline() {
        givenCaller(memberMembership);
        givenRows(memberId, List.of(
                // deadline before completion → late
                row(memberId, "2026-10-13T12:00:00Z", 10, 5L, 0, Instant.parse("2026-10-13T00:00:00Z"), WEEK_1),
                // deadline after completion → on time
                row(memberId, "2026-10-20T12:00:00Z", 10, 5L, 0, Instant.parse("2026-10-21T00:00:00Z"), WEEK_2),
                // deadline equal to completion → not late (deadline < completedAt only)
                row(memberId, "2026-10-21T00:00:00Z", 10, 5L, 0, Instant.parse("2026-10-21T00:00:00Z"), WEEK_2),
                // no deadline → excluded from the rate
                row(memberId, "2026-10-22T12:00:00Z", 10, 5L, 0, null, WEEK_2)));

        StatsResult result = statsService.getProjectStats(projectId, memberId, null, 4, UTC, NOW);

        StatsSummary summary = result.getSummary();
        assertThat(summary.getCompletedCount()).isEqualTo(4);
        assertThat(summary.getCompletedWithDeadline()).isEqualTo(3);
        assertThat(summary.getCompletedLate()).isEqualTo(1);
        assertThat(summary.getLateRate()).isEqualTo(1.0 / 3);
        assertThat(result.getBuckets().get(0).getCompletedLate()).isEqualTo(1);
        assertThat(result.getBuckets().get(1).getCompletedWithDeadline()).isEqualTo(2);
        assertThat(result.getBuckets().get(1).getCompletedLate()).isZero();
    }

    @Test
    @DisplayName("getProjectStats — lateRate is null when no completed job has a deadline")
    void getProjectStats_shouldReturnNullLateRate_whenNoDeadlines() {
        givenCaller(memberMembership);
        givenRows(memberId, List.of(row(memberId, "2026-10-13T12:00:00Z", 10, 5L, 0, null, WEEK_1)));

        StatsSummary summary = statsService
                .getProjectStats(projectId, memberId, null, 4, UTC, NOW).getSummary();

        assertThat(summary.getCompletedWithDeadline()).isZero();
        assertThat(summary.getCompletedLate()).isZero();
        assertThat(summary.getLateRate()).isNull();
    }

    @Test
    @DisplayName("getProjectStats — fills empty weeks: exactly 'weeks' buckets, oldest first, null averages")
    void getProjectStats_shouldFillEmptyWeeks() {
        givenCaller(memberMembership);
        givenRows(memberId, List.of(row(memberId, "2026-10-20T12:00:00Z", 100, 50L, 20, null, WEEK_2)));

        StatsResult result = statsService.getProjectStats(projectId, memberId, null, 4, UTC, NOW);

        assertThat(result.getBuckets()).extracting(StatsBucket::getWeekStart)
                .containsExactly(WEEK_1, WEEK_2, WEEK_3, WEEK_4);
        assertThat(result.getBuckets()).extracting(StatsBucket::getCompletedCount).containsExactly(0, 1, 0, 0);

        StatsBucket empty = result.getBuckets().get(0);
        assertThat(empty.getAvgLeadTimeSeconds()).isNull();
        assertThat(empty.getAvgCycleTimeSeconds()).isNull();
        assertThat(empty.getAvgBlockedSeconds()).isNull();
        assertThat(empty.getCompletedWithDeadline()).isZero();
        assertThat(empty.getCompletedLate()).isZero();

        StatsBucket filled = result.getBuckets().get(1);
        assertThat(filled.getAvgLeadTimeSeconds()).isEqualTo(100L);
        assertThat(filled.getAvgCycleTimeSeconds()).isEqualTo(50L);
        assertThat(filled.getAvgBlockedSeconds()).isEqualTo(20L);
    }

    @Test
    @DisplayName("getProjectStats — empty range has zero totals and null averages")
    void getProjectStats_shouldReturnZerosAndNulls_whenNothingCompleted() {
        givenCaller(memberMembership);
        givenRows(memberId, List.of());

        StatsSummary summary = statsService
                .getProjectStats(projectId, memberId, null, 4, UTC, NOW).getSummary();

        assertThat(summary.getCompletedCount()).isZero();
        assertThat(summary.getAvgLeadTimeSeconds()).isNull();
        assertThat(summary.getAvgCycleTimeSeconds()).isNull();
        assertThat(summary.getAvgBlockedSeconds()).isNull();
        assertThat(summary.getLateRate()).isNull();
    }

    @Test
    @DisplayName("getProjectStats — a row whose week key falls outside the buckets counts in totals only")
    void getProjectStats_shouldCountInTotalsOnly_whenWeekKeyOutsideBuckets() {
        givenCaller(memberMembership);
        givenRows(memberId, List.of(row(memberId, "2026-10-12T00:30:00Z", 10, 5L, 0, null,
                LocalDate.parse("2026-10-05"))));

        StatsResult result = statsService.getProjectStats(projectId, memberId, null, 4, UTC, NOW);

        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
        assertThat(result.getBuckets()).extracting(StatsBucket::getCompletedCount).containsOnly(0);
    }

    // --- Members ---

    @Test
    @DisplayName("getProjectStats — OWNER unfiltered gets all members, incl. zero completions and former assignees")
    void getProjectStats_shouldListMembers_whenOwnerUnfiltered() {
        UUID formerId = UUID.randomUUID();
        givenCaller(ownerMembership);
        JobStatsRow formerJob = row(formerId, "2026-10-13T12:00:00Z", 100, 50L, 0,
                Instant.parse("2026-10-13T00:00:00Z"), WEEK_1);
        formerJob.setAssigneeName("Fred Former");
        givenRows(null, List.of(
                row(memberId, "2026-10-20T12:00:00Z", 200, 100L, 10, null, WEEK_2),
                row(memberId, "2026-10-27T12:00:00Z", 400, 300L, 30, null, WEEK_3),
                formerJob,
                row(null, "2026-11-03T12:00:00Z", 1000, 500L, 0, null, WEEK_4)));
        when(projectMemberRepository.findByProjectId(projectId))
                .thenReturn(List.of(ownerMembership, adminMembership, memberMembership));

        StatsResult result = statsService.getProjectStats(projectId, ownerId, null, 4, UTC, NOW);

        assertThat(result.getMemberId()).isNull();
        assertThat(result.getMembers()).extracting(StatsMember::getName)
                .containsExactly("Adam Admin", "Fred Former", "Mia Member", "Olivia Owner");

        StatsMember admin = result.getMembers().get(0);
        assertThat(admin.getUserId()).isEqualTo(adminId);
        assertThat(admin.getCompletedCount()).isZero();
        assertThat(admin.getAvgLeadTimeSeconds()).isNull();
        assertThat(admin.getLateRate()).isNull();

        StatsMember former = result.getMembers().get(1);
        assertThat(former.getUserId()).isEqualTo(formerId);
        assertThat(former.getCompletedCount()).isEqualTo(1);
        assertThat(former.getCompletedWithDeadline()).isEqualTo(1);
        assertThat(former.getCompletedLate()).isEqualTo(1);
        assertThat(former.getLateRate()).isEqualTo(1.0);

        StatsMember member = result.getMembers().get(2);
        assertThat(member.getCompletedCount()).isEqualTo(2);
        assertThat(member.getAvgLeadTimeSeconds()).isEqualTo(300L);
        assertThat(member.getAvgCycleTimeSeconds()).isEqualTo(200L);
        assertThat(member.getAvgBlockedSeconds()).isEqualTo(20L);
    }

    @Test
    @DisplayName("getProjectStats — unassigned jobs count in totals and buckets only, not in members")
    void getProjectStats_shouldCountUnassignedInTotalsOnly() {
        givenCaller(adminMembership);
        givenRows(null, List.of(row(null, "2026-11-03T12:00:00Z", 100, 50L, 0, null, WEEK_4)));
        when(projectMemberRepository.findByProjectId(projectId)).thenReturn(List.of(adminMembership));

        StatsResult result = statsService.getProjectStats(projectId, adminId, null, 4, UTC, NOW);

        assertThat(result.getSummary().getCompletedCount()).isEqualTo(1);
        assertThat(result.getBuckets().get(3).getCompletedCount()).isEqualTo(1);
        assertThat(result.getMembers()).hasSize(1);
        assertThat(result.getMembers().get(0).getCompletedCount()).isZero();
        assertThat(result.getMembers()).extracting(StatsMember::getCompletedCount).containsOnly(0);
    }

    @Test
    @DisplayName("getProjectStats — members without a name sort last")
    void getProjectStats_shouldSortNamelessMembersLast() {
        UUID namelessId = UUID.randomUUID();
        givenCaller(ownerMembership);
        givenRows(null, List.of(row(namelessId, "2026-11-03T12:00:00Z", 100, 50L, 0, null, WEEK_4)));
        when(projectMemberRepository.findByProjectId(projectId)).thenReturn(List.of(ownerMembership));

        StatsResult result = statsService.getProjectStats(projectId, ownerId, null, 4, UTC, NOW);

        assertThat(result.getMembers()).extracting(StatsMember::getUserId).containsExactly(ownerId, namelessId);
    }
}
