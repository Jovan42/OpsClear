package com.opsclear.repository;

import com.opsclear.model.JobStatsRow;
import com.opsclear.model.JobStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.JOB_STATUS_HISTORY;
import static com.opsclear.generated.jooq.Tables.USERS;

/**
 * Read-only velocity stats query (ADR-0050). One project-bounded query built from CTEs over jobs and
 * job_status_history; aggregation happens in StatsService. No view, cache or rollup table.
 */
@Repository
@RequiredArgsConstructor
public class JobStatsRepository {

    private static final String COMPLETED = JobStatus.COMPLETED.name();
    private static final String IN_PROGRESS = JobStatus.IN_PROGRESS.name();
    private static final String BLOCKED = JobStatus.BLOCKED.name();

    // history CTE columns
    private static final Field<UUID> H_JOB_ID = DSL.field(DSL.name("job_id"), SQLDataType.UUID);
    private static final Field<String> H_CHANGED_TO = DSL.field(DSL.name("changed_to"), SQLDataType.VARCHAR);
    private static final Field<LocalDateTime> H_CHANGED_AT =
            DSL.field(DSL.name("changed_at"), SQLDataType.LOCALDATETIME);
    private static final Field<LocalDateTime> H_INTERVAL_END =
            DSL.field(DSL.name("interval_end"), SQLDataType.LOCALDATETIME);
    private static final Field<LocalDateTime> H_LAST_COMPLETED_AT =
            DSL.field(DSL.name("last_completed_at"), SQLDataType.LOCALDATETIME);

    // per_job / cohort CTE columns
    private static final Field<UUID> P_JOB_ID = DSL.field(DSL.name("job_id"), SQLDataType.UUID);
    private static final Field<UUID> P_ASSIGNED_TO = DSL.field(DSL.name("assigned_to"), SQLDataType.UUID);
    private static final Field<String> P_STATUS = DSL.field(DSL.name("status"), SQLDataType.VARCHAR);
    private static final Field<LocalDateTime> P_CREATED_AT =
            DSL.field(DSL.name("created_at"), SQLDataType.LOCALDATETIME);
    private static final Field<LocalDateTime> P_DEADLINE =
            DSL.field(DSL.name("deadline"), SQLDataType.LOCALDATETIME);
    private static final Field<LocalDateTime> P_COMPLETED_AT =
            DSL.field(DSL.name("completed_at"), SQLDataType.LOCALDATETIME);
    private static final Field<LocalDateTime> P_FIRST_IN_PROGRESS_AT =
            DSL.field(DSL.name("first_in_progress_at"), SQLDataType.LOCALDATETIME);
    private static final Field<BigDecimal> P_BLOCKED_SECONDS =
            DSL.field(DSL.name("blocked_seconds"), SQLDataType.NUMERIC);
    private static final Field<String> C_ASSIGNEE_NAME =
            DSL.field(DSL.name("assignee_name"), SQLDataType.VARCHAR);
    private static final Field<LocalDate> C_WEEK_START = DSL.field(DSL.name("week_start"), SQLDataType.LOCALDATE);

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * Returns one row per job of the project that is currently COMPLETED (and not soft-deleted) and whose
     * last completion falls in {@code [rangeStart, rangeEnd)}.
     *
     * @param assignedTo optional filter on the current assignee, {@code null} for all jobs
     * @param zone       time zone the ISO week key ({@link JobStatsRow#getWeekStart()}) is computed in
     */
    public List<JobStatsRow> findCompletedInRange(UUID projectId, Instant rangeStart, Instant rangeEnd,
                                                  ZoneId zone, UUID assignedTo) {
        CommonTableExpression<?> history = DSL.name("history").as(
                DSL.select(
                                JOB_STATUS_HISTORY.JOB_ID.as(H_JOB_ID.getName()),
                                JOB_STATUS_HISTORY.CHANGED_TO.as(H_CHANGED_TO.getName()),
                                JOB_STATUS_HISTORY.CHANGED_AT.as(H_CHANGED_AT.getName()),
                                DSL.lead(JOB_STATUS_HISTORY.CHANGED_AT)
                                        .over(DSL.partitionBy(JOB_STATUS_HISTORY.JOB_ID)
                                                .orderBy(JOB_STATUS_HISTORY.CHANGED_AT, JOB_STATUS_HISTORY.ID))
                                        .as(H_INTERVAL_END.getName()),
                                DSL.max(JOB_STATUS_HISTORY.CHANGED_AT)
                                        .filterWhere(JOB_STATUS_HISTORY.CHANGED_TO.eq(COMPLETED))
                                        .over(DSL.partitionBy(JOB_STATUS_HISTORY.JOB_ID))
                                        .as(H_LAST_COMPLETED_AT.getName()))
                        .from(JOB_STATUS_HISTORY)
                        .join(JOBS).on(JOBS.ID.eq(JOB_STATUS_HISTORY.JOB_ID))
                        .where(JOBS.PROJECT_ID.eq(projectId))
                        .and(JOBS.DELETED_AT.isNull()));

        CommonTableExpression<?> perJob = DSL.name("per_job").as(
                DSL.select(
                                JOBS.ID.as(P_JOB_ID.getName()),
                                JOBS.CREATED_AT.as(P_CREATED_AT.getName()),
                                JOBS.DEADLINE.as(P_DEADLINE.getName()),
                                JOBS.ASSIGNED_TO.as(P_ASSIGNED_TO.getName()),
                                JOBS.STATUS.as(P_STATUS.getName()),
                                DSL.max(history.field(H_CHANGED_AT))
                                        .filterWhere(history.field(H_CHANGED_TO).eq(COMPLETED))
                                        .as(P_COMPLETED_AT.getName()),
                                DSL.min(history.field(H_CHANGED_AT))
                                        .filterWhere(history.field(H_CHANGED_TO).eq(IN_PROGRESS))
                                        .as(P_FIRST_IN_PROGRESS_AT.getName()),
                                DSL.coalesce(
                                                DSL.sum(epochSeconds(history.field(H_INTERVAL_END),
                                                                history.field(H_CHANGED_AT)))
                                                        .filterWhere(history.field(H_CHANGED_TO).eq(BLOCKED)
                                                                .and(history.field(H_INTERVAL_END)
                                                                        .le(history.field(H_LAST_COMPLETED_AT)))),
                                                BigDecimal.ZERO)
                                        .as(P_BLOCKED_SECONDS.getName()))
                        .from(history)
                        .join(JOBS).on(JOBS.ID.eq(history.field(H_JOB_ID)))
                        .groupBy(JOBS.ID, JOBS.CREATED_AT, JOBS.DEADLINE, JOBS.ASSIGNED_TO, JOBS.STATUS));

        Field<LocalDateTime> completedAt = perJob.field(P_COMPLETED_AT);
        CommonTableExpression<?> cohort = DSL.name("cohort").as(
                DSL.select(
                                perJob.field(P_JOB_ID),
                                perJob.field(P_CREATED_AT),
                                perJob.field(P_DEADLINE),
                                perJob.field(P_ASSIGNED_TO),
                                USERS.NAME.as(C_ASSIGNEE_NAME.getName()),
                                completedAt,
                                perJob.field(P_FIRST_IN_PROGRESS_AT),
                                perJob.field(P_BLOCKED_SECONDS),
                                weekStart(completedAt, zone).as(C_WEEK_START.getName()))
                        .from(perJob)
                        .leftJoin(USERS).on(USERS.ID.eq(perJob.field(P_ASSIGNED_TO)))
                        .where(perJob.field(P_STATUS).eq(COMPLETED))
                        .and(completedAt.ge(toLocalDateTime(rangeStart)))
                        .and(completedAt.lt(toLocalDateTime(rangeEnd)))
                        .and(assignedTo != null
                                ? perJob.field(P_ASSIGNED_TO).eq(assignedTo)
                                : DSL.noCondition()));

        return dsl.with(history)
                .with(perJob)
                .with(cohort)
                .select(cohort.fields())
                .from(cohort)
                .orderBy(cohort.field(P_COMPLETED_AT), cohort.field(P_JOB_ID))
                .fetch()
                .map(this::toModel);
    }

    // Seconds between two UTC timestamps, as numeric.
    private static Field<BigDecimal> epochSeconds(Field<LocalDateTime> end, Field<LocalDateTime> start) {
        return DSL.field("extract(epoch from ({0} - {1}))", SQLDataType.NUMERIC, end, start);
    }

    // Monday of the ISO week containing the (UTC-stored) timestamp, in the given zone. Postgres applies the
    // zone's offset at that instant, so DST changes are handled.
    private static Field<LocalDate> weekStart(Field<LocalDateTime> utcTimestamp, ZoneId zone) {
        return DSL.field("cast(date_trunc('week', ({0} at time zone 'UTC') at time zone {1}) as date)",
                SQLDataType.LOCALDATE, utcTimestamp, DSL.val(zone.getId()));
    }

    private JobStatsRow toModel(Record r) {
        return JobStatsRow.builder()
                .jobId(r.get(P_JOB_ID))
                .assignedTo(r.get(P_ASSIGNED_TO))
                .assigneeName(r.get(C_ASSIGNEE_NAME))
                .createdAt(toInstant(r.get(P_CREATED_AT)))
                .deadline(toInstant(r.get(P_DEADLINE)))
                .completedAt(toInstant(r.get(P_COMPLETED_AT)))
                .firstInProgressAt(toInstant(r.get(P_FIRST_IN_PROGRESS_AT)))
                .blockedSeconds(r.get(P_BLOCKED_SECONDS).longValue()) // COALESCE'd to 0 in per_job
                .weekStart(r.get(C_WEEK_START))
                .build();
    }
}
