package com.opsclear.repository;

import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.ProjectMemberRole;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectOnConditionStep;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.APPROVALS;
import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static com.opsclear.generated.jooq.Tables.PROJECT_MEMBERS;
import static java.util.List.of;

@Repository
@RequiredArgsConstructor
public class ApprovalRepository {

    private static final Field<String> JOB_TITLE = JOBS.TITLE.as("job_title");

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public ApprovalModel insert(UUID jobId, UUID requesterId, String description) {
        UUID id = dsl.insertInto(APPROVALS)
                .set(APPROVALS.JOB_ID, jobId)
                .set(APPROVALS.REQUESTER_ID, requesterId)
                .set(APPROVALS.DESCRIPTION, description)
                .returning(APPROVALS.ID)
                .fetchSingle()
                .getId();

        return findById(id);
    }

    public Optional<ApprovalModel> findByIdAndJobId(UUID approvalId, UUID jobId) {
        return selectWithJob()
                .where(APPROVALS.ID.eq(approvalId))
                .and(APPROVALS.JOB_ID.eq(jobId))
                .fetchOptional()
                .map(this::toModel);
    }

    public List<ApprovalModel> findByJobId(UUID jobId) {
        return selectWithJob()
                .where(APPROVALS.JOB_ID.eq(jobId))
                .orderBy(APPROVALS.REQUESTED_AT.desc())
                .fetch()
                .map(this::toModel);
    }

    public List<ApprovalModel> findPendingByProjectId(UUID projectId) {
        return selectWithJob()
                .where(JOBS.PROJECT_ID.eq(projectId))
                .and(APPROVALS.STATUS.eq(ApprovalStatus.PENDING.name()))
                .orderBy(APPROVALS.REQUESTED_AT.asc())
                .fetch()
                .map(this::toModel);
    }

    public List<ApprovalModel> findPendingAcrossOrgs(UUID callerId) {
        return dsl.select(of(
                        APPROVALS.ID,
                        APPROVALS.JOB_ID,
                        JOB_TITLE,
                        JOBS.FRIENDLY_ID,
                        APPROVALS.REQUESTER_ID,
                        APPROVALS.APPROVER_ID,
                        APPROVALS.DESCRIPTION,
                        APPROVALS.STATUS,
                        APPROVALS.COMMENT,
                        APPROVALS.REQUESTED_AT,
                        APPROVALS.DECIDED_AT,
                        PROJECTS.ID,
                        PROJECTS.FRIENDLY_ID,
                        PROJECTS.NAME))
                .from(APPROVALS)
                .join(JOBS).on(APPROVALS.JOB_ID.eq(JOBS.ID))
                .join(PROJECTS).on(JOBS.PROJECT_ID.eq(PROJECTS.ID))
                .join(PROJECT_MEMBERS).on(PROJECT_MEMBERS.PROJECT_ID.eq(PROJECTS.ID))
                .where(PROJECT_MEMBERS.USER_ID.eq(callerId))
                .and(PROJECT_MEMBERS.ROLE.in(ProjectMemberRole.OWNER.name(), ProjectMemberRole.ADMIN.name()))
                .and(APPROVALS.STATUS.eq(ApprovalStatus.PENDING.name()))
                .and(JOBS.DELETED_AT.isNull())
                .and(PROJECTS.DELETED_AT.isNull())
                .orderBy(APPROVALS.REQUESTED_AT.asc())
                .fetch()
                .map(this::toModelWithProject);
    }

    public int updateDecision(UUID approvalId, UUID approverId, ApprovalStatus status,
                              String comment, Instant decidedAt) {
        return dsl.update(APPROVALS)
                .set(APPROVALS.STATUS, status.name())
                .set(APPROVALS.APPROVER_ID, approverId)
                .set(APPROVALS.COMMENT, comment)
                .set(APPROVALS.DECIDED_AT, toLocalDateTime(decidedAt))
                .where(APPROVALS.ID.eq(approvalId))
                .and(APPROVALS.STATUS.eq(ApprovalStatus.PENDING.name()))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(APPROVALS).execute();
    }

    private ApprovalModel findById(UUID id) {
        return selectWithJob()
                .where(APPROVALS.ID.eq(id))
                .fetchSingle(this::toModel);
    }

    private SelectOnConditionStep<Record> selectWithJob() {
        return dsl.select(of(
                        APPROVALS.ID,
                        APPROVALS.JOB_ID,
                        APPROVALS.REQUESTER_ID,
                        APPROVALS.APPROVER_ID,
                        APPROVALS.DESCRIPTION,
                        APPROVALS.STATUS,
                        APPROVALS.COMMENT,
                        APPROVALS.REQUESTED_AT,
                        APPROVALS.DECIDED_AT,
                        JOB_TITLE))
                .from(APPROVALS)
                .join(JOBS).on(APPROVALS.JOB_ID.eq(JOBS.ID));
    }

    private ApprovalModel toModelWithProject(Record r) {
        return ApprovalModel.builder()
                .id(r.get(APPROVALS.ID))
                .jobId(r.get(APPROVALS.JOB_ID))
                .jobTitle(r.get(JOB_TITLE))
                .jobFriendlyId(r.get(JOBS.FRIENDLY_ID))
                .requesterId(r.get(APPROVALS.REQUESTER_ID))
                .approverId(r.get(APPROVALS.APPROVER_ID))
                .description(r.get(APPROVALS.DESCRIPTION))
                .status(ApprovalStatus.valueOf(r.get(APPROVALS.STATUS)))
                .comment(r.get(APPROVALS.COMMENT))
                .requestedAt(r.get(APPROVALS.REQUESTED_AT).toInstant(ZoneOffset.UTC))
                .decidedAt(toInstant(r.get(APPROVALS.DECIDED_AT)))
                .projectId(r.get(PROJECTS.ID))
                .projectFriendlyId(r.get(PROJECTS.FRIENDLY_ID))
                .projectName(r.get(PROJECTS.NAME))
                .build();
    }

    private ApprovalModel toModel(Record r) {
        return ApprovalModel.builder()
                .id(r.get(APPROVALS.ID))
                .jobId(r.get(APPROVALS.JOB_ID))
                .jobTitle(r.get(JOB_TITLE))
                .requesterId(r.get(APPROVALS.REQUESTER_ID))
                .approverId(r.get(APPROVALS.APPROVER_ID))
                .description(r.get(APPROVALS.DESCRIPTION))
                .status(ApprovalStatus.valueOf(r.get(APPROVALS.STATUS)))
                .comment(r.get(APPROVALS.COMMENT))
                .requestedAt(r.get(APPROVALS.REQUESTED_AT).toInstant(ZoneOffset.UTC))
                .decidedAt(toInstant(r.get(APPROVALS.DECIDED_AT)))
                .build();
    }
}
