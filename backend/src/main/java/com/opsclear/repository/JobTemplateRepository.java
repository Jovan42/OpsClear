package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.Users;
import com.opsclear.model.JobTemplateModel;
import com.opsclear.model.JobTemplateScope;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOB_TEMPLATES;
import static com.opsclear.generated.jooq.Tables.MILESTONES;
import static com.opsclear.generated.jooq.Tables.USERS;

@Repository
@RequiredArgsConstructor
public class JobTemplateRepository {

    private final DSLContext dsl;

    public List<JobTemplateModel> findActiveByProjectIdOrOrgId(UUID projectId, UUID orgId) {
        Users assigneeUsers = USERS.as("assignee_users");
        return dsl.select(
                        JOB_TEMPLATES.asterisk(),
                        assigneeUsers.NAME.as("assignee_name"),
                        MILESTONES.NAME.as("milestone_name"))
                .from(JOB_TEMPLATES)
                .leftJoin(assigneeUsers).on(assigneeUsers.ID.eq(JOB_TEMPLATES.ASSIGNEE_ID))
                .leftJoin(MILESTONES).on(MILESTONES.ID.eq(JOB_TEMPLATES.MILESTONE_ID)
                        .and(MILESTONES.DELETED_AT.isNull()))
                .where(JOB_TEMPLATES.PROJECT_ID.eq(projectId)
                        .or(orgId != null ? JOB_TEMPLATES.ORG_ID.eq(orgId) : org.jooq.impl.DSL.falseCondition()))
                .and(JOB_TEMPLATES.DELETED_AT.isNull())
                .orderBy(JOB_TEMPLATES.PROJECT_ID.isNull().asc(), JOB_TEMPLATES.NAME.asc())
                .fetch()
                .map(this::toModel);
    }

    public List<JobTemplateModel> findActiveByOrgId(UUID orgId) {
        Users assigneeUsers = USERS.as("assignee_users");
        return dsl.select(
                        JOB_TEMPLATES.asterisk(),
                        assigneeUsers.NAME.as("assignee_name"),
                        MILESTONES.NAME.as("milestone_name"))
                .from(JOB_TEMPLATES)
                .leftJoin(assigneeUsers).on(assigneeUsers.ID.eq(JOB_TEMPLATES.ASSIGNEE_ID))
                .leftJoin(MILESTONES).on(MILESTONES.ID.eq(JOB_TEMPLATES.MILESTONE_ID)
                        .and(MILESTONES.DELETED_AT.isNull()))
                .where(JOB_TEMPLATES.ORG_ID.eq(orgId))
                .and(JOB_TEMPLATES.DELETED_AT.isNull())
                .orderBy(JOB_TEMPLATES.NAME.asc())
                .fetch()
                .map(this::toModel);
    }

    public Optional<JobTemplateModel> findByIdAndDeletedAtIsNull(UUID id) {
        Users assigneeUsers = USERS.as("assignee_users");
        return dsl.select(
                        JOB_TEMPLATES.asterisk(),
                        assigneeUsers.NAME.as("assignee_name"),
                        MILESTONES.NAME.as("milestone_name"))
                .from(JOB_TEMPLATES)
                .leftJoin(assigneeUsers).on(assigneeUsers.ID.eq(JOB_TEMPLATES.ASSIGNEE_ID))
                .leftJoin(MILESTONES).on(MILESTONES.ID.eq(JOB_TEMPLATES.MILESTONE_ID)
                        .and(MILESTONES.DELETED_AT.isNull()))
                .where(JOB_TEMPLATES.ID.eq(id))
                .and(JOB_TEMPLATES.DELETED_AT.isNull())
                .fetchOptional()
                .map(this::toModel);
    }

    public JobTemplateModel save(JobTemplateModel template) {
        if (template.getId() == null) {
            UUID id = dsl.insertInto(JOB_TEMPLATES)
                    .set(JOB_TEMPLATES.FRIENDLY_ID, template.getFriendlyId())
                    .set(JOB_TEMPLATES.PROJECT_ID, template.getProjectId())
                    .set(JOB_TEMPLATES.ORG_ID, template.getOrgId())
                    .set(JOB_TEMPLATES.NAME, template.getName())
                    .set(JOB_TEMPLATES.TITLE, template.getTitle())
                    .set(JOB_TEMPLATES.DESCRIPTION, template.getDescription())
                    .set(JOB_TEMPLATES.CLIENT, template.getClient())
                    .set(JOB_TEMPLATES.PRIORITY, template.getPriority())
                    .set(JOB_TEMPLATES.ASSIGNEE_MODE, template.getAssigneeMode())
                    .set(JOB_TEMPLATES.ASSIGNEE_ID, template.getAssigneeId())
                    .set(JOB_TEMPLATES.MILESTONE_ID, template.getMilestoneId())
                    .set(JOB_TEMPLATES.DEADLINE_OFFSET_DAYS, template.getDeadlineOffsetDays())
                    .set(JOB_TEMPLATES.CREATED_BY, template.getCreatedBy())
                    .set(JOB_TEMPLATES.CREATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .set(JOB_TEMPLATES.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .returning(JOB_TEMPLATES.ID)
                    .fetchSingle()
                    .getId();
            return findByIdAndDeletedAtIsNull(id).orElseThrow();
        }
        dsl.update(JOB_TEMPLATES)
                .set(JOB_TEMPLATES.NAME, template.getName())
                .set(JOB_TEMPLATES.TITLE, template.getTitle())
                .set(JOB_TEMPLATES.DESCRIPTION, template.getDescription())
                .set(JOB_TEMPLATES.CLIENT, template.getClient())
                .set(JOB_TEMPLATES.PRIORITY, template.getPriority())
                .set(JOB_TEMPLATES.ASSIGNEE_MODE, template.getAssigneeMode())
                .set(JOB_TEMPLATES.ASSIGNEE_ID, template.getAssigneeId())
                .set(JOB_TEMPLATES.MILESTONE_ID, template.getMilestoneId())
                .set(JOB_TEMPLATES.DEADLINE_OFFSET_DAYS, template.getDeadlineOffsetDays())
                .set(JOB_TEMPLATES.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(JOB_TEMPLATES.ID.eq(template.getId()))
                .execute();
        return findByIdAndDeletedAtIsNull(template.getId()).orElseThrow();
    }

    public void softDelete(UUID id) {
        dsl.update(JOB_TEMPLATES)
                .set(JOB_TEMPLATES.DELETED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(JOB_TEMPLATES.ID.eq(id))
                .execute();
    }

    public void incrementOccurrenceCount(UUID id) {
        dsl.update(JOB_TEMPLATES)
                .set(JOB_TEMPLATES.OCCURRENCE_COUNT, JOB_TEMPLATES.OCCURRENCE_COUNT.add(1))
                .where(JOB_TEMPLATES.ID.eq(id))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(JOB_TEMPLATES).execute();
    }

    private JobTemplateModel toModel(Record r) {
        UUID projectId = r.get(JOB_TEMPLATES.PROJECT_ID);
        return JobTemplateModel.builder()
                .id(r.get(JOB_TEMPLATES.ID))
                .friendlyId(r.get(JOB_TEMPLATES.FRIENDLY_ID))
                .projectId(projectId)
                .orgId(r.get(JOB_TEMPLATES.ORG_ID))
                .scope(projectId != null ? JobTemplateScope.PROJECT : JobTemplateScope.ORG)
                .name(r.get(JOB_TEMPLATES.NAME))
                .title(r.get(JOB_TEMPLATES.TITLE))
                .description(r.get(JOB_TEMPLATES.DESCRIPTION))
                .client(r.get(JOB_TEMPLATES.CLIENT))
                .priority(r.get(JOB_TEMPLATES.PRIORITY))
                .assigneeMode(r.get(JOB_TEMPLATES.ASSIGNEE_MODE))
                .assigneeId(r.get(JOB_TEMPLATES.ASSIGNEE_ID))
                .assigneeName(r.get("assignee_name", String.class))
                .milestoneId(r.get(JOB_TEMPLATES.MILESTONE_ID))
                .milestoneName(r.get("milestone_name", String.class))
                .deadlineOffsetDays(r.get(JOB_TEMPLATES.DEADLINE_OFFSET_DAYS))
                .occurrenceCount(r.get(JOB_TEMPLATES.OCCURRENCE_COUNT))
                .createdBy(r.get(JOB_TEMPLATES.CREATED_BY))
                .createdAt(r.get(JOB_TEMPLATES.CREATED_AT))
                .updatedAt(r.get(JOB_TEMPLATES.UPDATED_AT))
                .deletedAt(r.get(JOB_TEMPLATES.DELETED_AT))
                .build();
    }
}
