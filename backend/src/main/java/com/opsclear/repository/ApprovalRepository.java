package com.opsclear.repository;

import com.opsclear.model.ApprovalKind;
import com.opsclear.model.ApprovalModel;
import com.opsclear.model.ApprovalStatus;
import com.opsclear.model.PendingCompletionApprovalModel;
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
import static com.opsclear.generated.jooq.Tables.USERS;
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

    /** Inserts a pending manual approval. */
    public ApprovalModel insert(UUID jobId, UUID requesterId, String description) {
        return insert(jobId, requesterId, description, ApprovalKind.MANUAL);
    }

    /**
     * Inserts a pending approval of {@code kind}. A second pending {@code COMPLETION} approval on the same job violates
     * {@code uq_approvals_one_pending_completion} and throws {@link org.springframework.dao.DuplicateKeyException}.
     */
    public ApprovalModel insert(UUID jobId, UUID requesterId, String description, ApprovalKind kind) {
        UUID id = dsl.insertInto(APPROVALS)
                .set(APPROVALS.JOB_ID, jobId)
                .set(APPROVALS.REQUESTER_ID, requesterId)
                .set(APPROVALS.DESCRIPTION, description)
                .set(APPROVALS.KIND, kind.name())
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

    /** The job's pending completion approval (at most one, ADR-0052), with the requester's name. */
    public Optional<PendingCompletionApprovalModel> findPendingCompletionByJobId(UUID jobId) {
        return findPendingCompletionByJobIds(List.of(jobId)).stream().findFirst();
    }

    /** Pending completion approvals for {@code jobIds}, at most one per job, in one query (job lists). */
    public List<PendingCompletionApprovalModel> findPendingCompletionByJobIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        return dsl.select(APPROVALS.ID, APPROVALS.JOB_ID, APPROVALS.REQUESTER_ID, USERS.NAME, APPROVALS.REQUESTED_AT,
                        APPROVALS.DESCRIPTION)
                .from(APPROVALS)
                .leftJoin(USERS).on(APPROVALS.REQUESTER_ID.eq(USERS.ID))
                .where(APPROVALS.JOB_ID.in(jobIds))
                .and(APPROVALS.KIND.eq(ApprovalKind.COMPLETION.name()))
                .and(APPROVALS.STATUS.eq(ApprovalStatus.PENDING.name()))
                .fetch(r -> PendingCompletionApprovalModel.builder()
                        .id(r.get(APPROVALS.ID))
                        .jobId(r.get(APPROVALS.JOB_ID))
                        .requesterId(r.get(APPROVALS.REQUESTER_ID))
                        .requesterName(r.get(USERS.NAME))
                        .requestedAt(toInstant(r.get(APPROVALS.REQUESTED_AT)))
                        .message(r.get(APPROVALS.DESCRIPTION))
                        .build());
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
                        APPROVALS.KIND,
                        APPROVALS.COMPLETION_APPLIED,
                        APPROVALS.COMPLETION_NOT_APPLIED_REASON,
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

    /**
     * Cancels completion approval {@code approvalId} if it's still pending (ADR-0052 withdrawal). The canceller is
     * recorded as the approver and {@code cancelledAt} as the decision time.
     *
     * @return the number of rows updated: 0 if it's no longer pending (or isn't a completion approval)
     */
    public int cancelPendingCompletion(UUID approvalId, UUID cancelledBy, String comment, Instant cancelledAt) {
        return dsl.update(APPROVALS)
                .set(APPROVALS.STATUS, ApprovalStatus.CANCELLED.name())
                .set(APPROVALS.APPROVER_ID, cancelledBy)
                .set(APPROVALS.COMMENT, comment)
                .set(APPROVALS.DECIDED_AT, toLocalDateTime(cancelledAt))
                .where(APPROVALS.ID.eq(approvalId))
                .and(APPROVALS.KIND.eq(ApprovalKind.COMPLETION.name()))
                .and(APPROVALS.STATUS.eq(ApprovalStatus.PENDING.name()))
                .execute();
    }

    /**
     * Cancels the job's pending completion approval, if it has one (ADR-0052 auto-cancel on direct completion or
     * deletion), recording {@code cancelledBy} as the approver.
     *
     * @return the cancelled approval, or empty if none was pending
     */
    public Optional<ApprovalModel> cancelPendingCompletionByJobId(UUID jobId, UUID cancelledBy, String comment,
                                                                  Instant cancelledAt) {
        return dsl.update(APPROVALS)
                .set(APPROVALS.STATUS, ApprovalStatus.CANCELLED.name())
                .set(APPROVALS.APPROVER_ID, cancelledBy)
                .set(APPROVALS.COMMENT, comment)
                .set(APPROVALS.DECIDED_AT, toLocalDateTime(cancelledAt))
                .where(APPROVALS.JOB_ID.eq(jobId))
                .and(APPROVALS.KIND.eq(ApprovalKind.COMPLETION.name()))
                .and(APPROVALS.STATUS.eq(ApprovalStatus.PENDING.name()))
                .returning(APPROVALS.ID)
                .fetchOptional()
                .map(r -> findById(r.getId()));
    }

    /** Records the outcome of an approved completion approval (ADR-0052); {@code reason} is null when applied. */
    public void updateCompletionOutcome(UUID approvalId, boolean applied, String reason) {
        dsl.update(APPROVALS)
                .set(APPROVALS.COMPLETION_APPLIED, applied)
                .set(APPROVALS.COMPLETION_NOT_APPLIED_REASON, reason)
                .where(APPROVALS.ID.eq(approvalId))
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
                        APPROVALS.KIND,
                        APPROVALS.COMPLETION_APPLIED,
                        APPROVALS.COMPLETION_NOT_APPLIED_REASON,
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
                .kind(ApprovalKind.valueOf(r.get(APPROVALS.KIND)))
                .completionApplied(r.get(APPROVALS.COMPLETION_APPLIED))
                .completionNotAppliedReason(r.get(APPROVALS.COMPLETION_NOT_APPLIED_REASON))
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
                .kind(ApprovalKind.valueOf(r.get(APPROVALS.KIND)))
                .completionApplied(r.get(APPROVALS.COMPLETION_APPLIED))
                .completionNotAppliedReason(r.get(APPROVALS.COMPLETION_NOT_APPLIED_REASON))
                .build();
    }
}
