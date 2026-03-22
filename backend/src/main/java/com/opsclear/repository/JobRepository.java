package com.opsclear.repository;

import com.opsclear.model.JobModel;
import com.opsclear.model.JobPriority;
import com.opsclear.model.JobStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.MILESTONES;
import static com.opsclear.generated.jooq.Tables.PROJECT_BLOCK_REASONS;
import static com.opsclear.generated.jooq.Tables.USERS;
import static java.util.List.of;

@Repository
@RequiredArgsConstructor
public class JobRepository {

    private static final Field<String> ASSIGNED_TO_NAME = USERS.NAME.as("assigned_to_name");
    private static final Field<String> BLOCKED_REASON_TEXT =
            PROJECT_BLOCK_REASONS.REASON.as("blocked_reason_text");
    private static final Field<String> MILESTONE_NAME_TEXT = MILESTONES.NAME.as("milestone_name_text");

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    public Optional<JobModel> findByIdAndDeletedAtIsNull(UUID id) {
        return selectWithAssignee()
                .where(JOBS.ID.eq(id))
                .and(JOBS.DELETED_AT.isNull())
                .fetchOptional()
                .map(this::toModel);
    }

    public List<JobModel> findByProjectIdAndDeletedAtIsNull(UUID projectId) {
        return findByFilters(projectId, null, null, null, null);
    }

    public List<JobModel> findByFilters(UUID projectId, UUID assignedTo, String q,
                                        JobPriority priority, UUID milestoneId) {
        String pattern = q != null ? "%" + q.toLowerCase() + "%" : null;
        return selectWithAssignee()
                .where(JOBS.PROJECT_ID.eq(projectId))
                .and(JOBS.DELETED_AT.isNull())
                .and(assignedTo != null ? JOBS.ASSIGNED_TO.eq(assignedTo) : DSL.noCondition())
                .and(priority != null ? JOBS.PRIORITY.eq(priority.name()) : DSL.noCondition())
                .and(milestoneId != null ? JOBS.MILESTONE_ID.eq(milestoneId) : DSL.noCondition())
                .and(pattern != null ? DSL.or(
                        JOBS.TITLE.likeIgnoreCase(pattern),
                        JOBS.DESCRIPTION.likeIgnoreCase(pattern),
                        JOBS.CLIENT.likeIgnoreCase(pattern),
                        USERS.NAME.likeIgnoreCase(pattern)
                ) : DSL.noCondition())
                .orderBy(JOBS.PRIORITY.sortDesc(), JOBS.CREATED_AT.desc())
                .fetch()
                .map(this::toModel);
    }

    public List<JobModel> findByIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return selectWithAssignee()
                .where(JOBS.ID.in(ids))
                .and(JOBS.DELETED_AT.isNull())
                .fetch()
                .map(this::toModel);
    }

    public JobModel save(JobModel job) {
        if (job.getId() == null) {
            UUID id = dsl.insertInto(JOBS)
                    .set(JOBS.PROJECT_ID, job.getProjectId())
                    .set(JOBS.TITLE, job.getTitle())
                    .set(JOBS.DESCRIPTION, job.getDescription())
                    .set(JOBS.CLIENT, job.getClient())
                    .set(JOBS.ASSIGNED_TO, job.getAssignedTo())
                    .set(JOBS.DEADLINE, toLocalDateTime(job.getDeadline()))
                    .set(JOBS.STATUS, job.getStatus().name())
                    .set(JOBS.PRIORITY, job.getPriority().name())
                    .set(JOBS.MILESTONE_ID, job.getMilestoneId())
                    .set(JOBS.CREATED_BY, job.getCreatedBy())
                    .set(JOBS.CREATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .set(JOBS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .returning(JOBS.ID)
                    .fetchSingle()
                    .getId();
            return findById(id).orElseThrow();
        }
        dsl.update(JOBS)
                .set(JOBS.TITLE, job.getTitle())
                .set(JOBS.DESCRIPTION, job.getDescription())
                .set(JOBS.CLIENT, job.getClient())
                .set(JOBS.ASSIGNED_TO, job.getAssignedTo())
                .set(JOBS.DEADLINE, toLocalDateTime(job.getDeadline()))
                .set(JOBS.STATUS, job.getStatus().name())
                .set(JOBS.PRIORITY, job.getPriority().name())
                .set(JOBS.MILESTONE_ID, job.getMilestoneId())
                .set(JOBS.BLOCKED_BY, job.getBlockedBy())
                .set(JOBS.BLOCKED_REASON_ID, job.getBlockedReasonId())
                .set(JOBS.BLOCKED_AT, toLocalDateTime(job.getBlockedAt()))
                .set(JOBS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .set(JOBS.DELETED_AT, toLocalDateTime(job.getDeletedAt()))
                .where(JOBS.ID.eq(job.getId()))
                .execute();
        return findById(job.getId()).orElseThrow();
    }

    public void deleteAll() {
        dsl.deleteFrom(JOBS).execute();
    }

    public Optional<JobModel> findById(UUID id) {
        return selectWithAssignee()
                .where(JOBS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    private SelectOnConditionStep<Record> selectWithAssignee() {
        return dsl.select(of(
                        JOBS.ID,
                        JOBS.PROJECT_ID,
                        JOBS.TITLE,
                        JOBS.DESCRIPTION,
                        JOBS.CLIENT,
                        JOBS.ASSIGNED_TO,
                        JOBS.DEADLINE,
                        JOBS.STATUS,
                        JOBS.PRIORITY,
                        JOBS.CREATED_BY,
                        JOBS.CREATED_AT,
                        JOBS.UPDATED_AT,
                        JOBS.DELETED_AT,
                        JOBS.BLOCKED_BY,
                        JOBS.BLOCKED_REASON_ID,
                        JOBS.BLOCKED_AT,
                        JOBS.MILESTONE_ID,
                        ASSIGNED_TO_NAME,
                        BLOCKED_REASON_TEXT,
                        MILESTONE_NAME_TEXT))
                .from(JOBS)
                .leftJoin(USERS).on(JOBS.ASSIGNED_TO.eq(USERS.ID))
                .leftJoin(PROJECT_BLOCK_REASONS).on(JOBS.BLOCKED_REASON_ID.eq(PROJECT_BLOCK_REASONS.ID))
                .leftJoin(MILESTONES).on(JOBS.MILESTONE_ID.eq(MILESTONES.ID));
    }

    private JobModel toModel(Record r) {
        return JobModel.builder()
                .id(r.get(JOBS.ID))
                .projectId(r.get(JOBS.PROJECT_ID))
                .title(r.get(JOBS.TITLE))
                .description(r.get(JOBS.DESCRIPTION))
                .client(r.get(JOBS.CLIENT))
                .assignedTo(r.get(JOBS.ASSIGNED_TO))
                .assignedToName(r.get(ASSIGNED_TO_NAME))
                .deadline(toInstant(r.get(JOBS.DEADLINE)))
                .status(JobStatus.valueOf(r.get(JOBS.STATUS)))
                .priority(JobPriority.valueOf(r.get(JOBS.PRIORITY)))
                .createdBy(r.get(JOBS.CREATED_BY))
                .createdAt(toInstant(r.get(JOBS.CREATED_AT)))
                .updatedAt(toInstant(r.get(JOBS.UPDATED_AT)))
                .deletedAt(toInstant(r.get(JOBS.DELETED_AT)))
                .milestoneId(r.get(JOBS.MILESTONE_ID))
                .milestoneName(r.get(MILESTONE_NAME_TEXT))
                .blockedBy(r.get(JOBS.BLOCKED_BY))
                .blockedReasonId(r.get(JOBS.BLOCKED_REASON_ID))
                .blockedReason(r.get(BLOCKED_REASON_TEXT))
                .blockedAt(toInstant(r.get(JOBS.BLOCKED_AT)))
                .build();
    }
}
