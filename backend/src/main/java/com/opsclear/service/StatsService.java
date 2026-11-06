package com.opsclear.service;

import com.opsclear.exception.BadRequestException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.ForbiddenException;
import com.opsclear.exception.NotFoundException;
import com.opsclear.model.JobStatsRow;
import com.opsclear.model.ProjectMemberModel;
import com.opsclear.model.ProjectMemberRole;
import com.opsclear.model.StatsBucket;
import com.opsclear.model.StatsMember;
import com.opsclear.model.StatsResult;
import com.opsclear.model.StatsSummary;
import com.opsclear.repository.JobStatsRepository;
import com.opsclear.repository.ProjectMemberRepository;
import com.opsclear.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Project job completion velocity stats (ADR-0050). Reads through its own repository and does not call
 * DashboardService (ADR-0017 allows only that one service-to-service exception). Aggregation per week, per
 * member and in total happens here, from the repository's per-job rows.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatsService {

    public static final int DEFAULT_WEEKS = 12;
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("UTC");
    public static final Set<Integer> ALLOWED_WEEKS = Set.of(4, 12, 26);

    private final JobStatsRepository jobStatsRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    /**
     * Stats for the project as of now.
     *
     * @param memberId optional member filter; MEMBERs are always limited to themselves
     * @param weeks    4, 12 or 26; {@code null} means {@link #DEFAULT_WEEKS}
     * @param zone     viewer's time zone for week bucketing; {@code null} means UTC
     */
    @Transactional(readOnly = true)
    public StatsResult getProjectStats(UUID projectId, UUID callerId, UUID memberId, Integer weeks, ZoneId zone) {
        return getProjectStats(projectId, callerId, memberId, weeks, zone, Instant.now());
    }

    /**
     * Stats for the project as of {@code now}: the week containing {@code now} (in {@code zone}) is the last,
     * partial bucket.
     */
    @Transactional(readOnly = true)
    public StatsResult getProjectStats(UUID projectId, UUID callerId, UUID memberId, Integer weeks, ZoneId zone,
                                       Instant now) {
        int weekCount = weeks != null ? weeks : DEFAULT_WEEKS;
        requireAllowedWeeks(weekCount);
        ZoneId tz = zone != null ? zone : DEFAULT_ZONE;

        requireProjectExists(projectId);
        ProjectMemberModel caller = requireMember(projectId, callerId);
        boolean isMember = caller.getRole() == ProjectMemberRole.MEMBER;
        if (isMember) {
            requireOwnStats(memberId, callerId);
        }
        UUID effectiveMemberId = isMember ? callerId : memberId;

        LocalDate currentWeek = now.atZone(tz).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate oldestWeek = currentWeek.minusWeeks(weekCount - 1L);
        Instant rangeStart = oldestWeek.atStartOfDay(tz).toInstant();
        Instant rangeEnd = currentWeek.plusWeeks(1).atStartOfDay(tz).toInstant();

        List<JobStatsRow> rows = jobStatsRepository.findCompletedInRange(
                projectId, rangeStart, rangeEnd, tz, effectiveMemberId);

        Metrics total = new Metrics();
        Map<LocalDate, Metrics> byWeek = new LinkedHashMap<>();
        for (int i = 0; i < weekCount; i++) {
            byWeek.put(oldestWeek.plusWeeks(i), new Metrics());
        }
        for (JobStatsRow row : rows) {
            total.add(row);
            Metrics week = byWeek.get(row.getWeekStart());
            if (week != null) {
                week.add(row);
            }
        }

        List<StatsBucket> buckets = byWeek.entrySet().stream()
                .map(e -> e.getValue().toBucket(e.getKey()))
                .toList();

        List<StatsMember> members = !isMember && memberId == null
                ? buildMembers(projectId, rows)
                : null;

        log.debug("Stats loaded for project {} by user {}: {} weeks, tz {}, member {}, {} completed jobs",
                projectId, callerId, weekCount, tz.getId(), effectiveMemberId, rows.size());

        return StatsResult.builder()
                .weeks(weekCount)
                .timezone(tz.getId())
                .memberId(effectiveMemberId)
                .summary(total.toSummary())
                .buckets(buckets)
                .members(members)
                .build();
    }

    // All current project members (including zero completions), plus former members who are still the
    // assignee of a cohort job. Unassigned jobs are not attributed to anyone.
    private List<StatsMember> buildMembers(UUID projectId, List<JobStatsRow> rows) {
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, Metrics> byMember = new LinkedHashMap<>();
        for (ProjectMemberModel m : projectMemberRepository.findByProjectId(projectId)) {
            names.put(m.getUserId(), m.getUserName());
            byMember.put(m.getUserId(), new Metrics());
        }
        for (JobStatsRow row : rows) {
            if (row.getAssignedTo() == null) {
                continue;
            }
            names.putIfAbsent(row.getAssignedTo(), row.getAssigneeName());
            byMember.computeIfAbsent(row.getAssignedTo(), id -> new Metrics()).add(row);
        }
        List<StatsMember> members = new ArrayList<>();
        byMember.forEach((userId, metrics) -> members.add(metrics.toMember(userId, names.get(userId))));
        members.sort(Comparator.comparing(StatsMember::getName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return members;
    }

    /** Running sums for one group of cohort jobs (total, a week or a member). */
    private static final class Metrics {
        private int count;
        private long leadSum;
        private long cycleSum;
        private int cycleCount;
        private long blockedSum;
        private int withDeadline;
        private int late;

        void add(JobStatsRow row) {
            count++;
            leadSum += Duration.between(row.getCreatedAt(), row.getCompletedAt()).getSeconds();
            if (row.getFirstInProgressAt() != null) {
                cycleSum += Duration.between(row.getFirstInProgressAt(), row.getCompletedAt()).getSeconds();
                cycleCount++;
            }
            blockedSum += row.getBlockedSeconds();
            if (row.getDeadline() != null) {
                withDeadline++;
                // Same rule as the Dashboard overdue list: late only once the deadline day is over.
                if (DeadlineRules.isPastDeadline(row.getDeadline(), row.getCompletedAt())) {
                    late++;
                }
            }
        }

        StatsSummary toSummary() {
            return StatsSummary.builder()
                    .completedCount(count)
                    .avgLeadTimeSeconds(average(leadSum, count))
                    .avgCycleTimeSeconds(average(cycleSum, cycleCount))
                    .avgBlockedSeconds(average(blockedSum, count))
                    .completedWithDeadline(withDeadline)
                    .completedLate(late)
                    .lateRate(lateRate())
                    .build();
        }

        StatsBucket toBucket(LocalDate weekStart) {
            return StatsBucket.builder()
                    .weekStart(weekStart)
                    .completedCount(count)
                    .avgLeadTimeSeconds(average(leadSum, count))
                    .avgCycleTimeSeconds(average(cycleSum, cycleCount))
                    .avgBlockedSeconds(average(blockedSum, count))
                    .completedWithDeadline(withDeadline)
                    .completedLate(late)
                    .build();
        }

        StatsMember toMember(UUID userId, String name) {
            return StatsMember.builder()
                    .userId(userId)
                    .name(name)
                    .completedCount(count)
                    .avgLeadTimeSeconds(average(leadSum, count))
                    .avgCycleTimeSeconds(average(cycleSum, cycleCount))
                    .avgBlockedSeconds(average(blockedSum, count))
                    .completedWithDeadline(withDeadline)
                    .completedLate(late)
                    .lateRate(lateRate())
                    .build();
        }

        private Double lateRate() {
            return withDeadline == 0 ? null : (double) late / withDeadline;
        }

        private static Long average(long sum, int n) {
            return n == 0 ? null : Math.round((double) sum / n);
        }
    }

    // --- Guards ---

    private void requireAllowedWeeks(int weeks) {
        if (!ALLOWED_WEEKS.contains(weeks)) {
            throw new BadRequestException(ErrorMessages.Stats.INVALID_WEEKS);
        }
    }

    private void requireProjectExists(UUID projectId) {
        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .orElseThrow(() -> new NotFoundException(ErrorMessages.Project.NOT_FOUND));
    }

    private ProjectMemberModel requireMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ForbiddenException(ErrorMessages.Member.NOT_A_MEMBER));
    }

    private void requireOwnStats(UUID memberId, UUID callerId) {
        if (memberId != null && !memberId.equals(callerId)) {
            throw new ForbiddenException(ErrorMessages.Stats.MEMBER_FILTER_NOT_ALLOWED);
        }
    }
}
