package com.opsclear.repository;

import com.opsclear.model.ProjectDirectoryEntryModel;
import com.opsclear.model.ProjectModel;
import com.opsclear.model.ProjectStatus;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static org.jooq.impl.DSL.count;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static com.opsclear.generated.jooq.Tables.PROJECT_MEMBERS;
import static com.opsclear.generated.jooq.Tables.USERS;
import static java.util.List.of;

@Repository
@RequiredArgsConstructor
public class ProjectRepository {

    private static final Field<String> OWNER_NAME = USERS.NAME.as("owner_name");

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    public Optional<ProjectModel> findByIdAndDeletedAtIsNull(UUID id) {
        return selectWithOwner()
                .where(PROJECTS.ID.eq(id))
                .and(PROJECTS.DELETED_AT.isNull())
                .fetchOptional()
                .map(this::toModel);
    }

    public List<ProjectModel> findByOwnerIdAndDeletedAtIsNull(UUID ownerId) {
        return selectWithOwner()
                .where(PROJECTS.OWNER_ID.eq(ownerId))
                .and(PROJECTS.DELETED_AT.isNull())
                .fetch()
                .map(this::toModel);
    }

    public List<ProjectModel> findByMemberIdAndStatusAndDeletedAtIsNull(UUID userId, ProjectStatus status) {
        var query = dsl.select(of(
                        PROJECTS.ID,
                        PROJECTS.FRIENDLY_ID,
                        PROJECTS.NAME,
                        PROJECTS.DESCRIPTION,
                        PROJECTS.OWNER_ID,
                        OWNER_NAME,
                        PROJECTS.ORGANISATION_ID,
                        PROJECTS.STATUS,
                        PROJECTS.CREATED_AT,
                        PROJECTS.UPDATED_AT,
                        PROJECTS.DELETED_AT))
                .from(PROJECTS)
                .join(USERS).on(PROJECTS.OWNER_ID.eq(USERS.ID))
                .join(PROJECT_MEMBERS).on(PROJECT_MEMBERS.PROJECT_ID.eq(PROJECTS.ID))
                .where(PROJECT_MEMBERS.USER_ID.eq(userId))
                .and(PROJECTS.DELETED_AT.isNull());

        if (status != null) {
            query = query.and(PROJECTS.STATUS.eq(status.name()));
        }

        return query.fetch().map(this::toModel);
    }

    public List<ProjectDirectoryEntryModel> findDirectoryByOrgId(UUID orgId) {
        Field<Integer> memberCount = count(PROJECT_MEMBERS.USER_ID).as("member_count");
        return dsl.select(PROJECTS.ID, PROJECTS.FRIENDLY_ID, PROJECTS.NAME, PROJECTS.OWNER_ID,
                        OWNER_NAME, PROJECTS.STATUS, memberCount)
                .from(PROJECTS)
                .join(USERS).on(PROJECTS.OWNER_ID.eq(USERS.ID))
                .leftJoin(PROJECT_MEMBERS).on(PROJECT_MEMBERS.PROJECT_ID.eq(PROJECTS.ID))
                .where(PROJECTS.ORGANISATION_ID.eq(orgId))
                .and(PROJECTS.DELETED_AT.isNull())
                .groupBy(PROJECTS.ID, PROJECTS.FRIENDLY_ID, PROJECTS.NAME, PROJECTS.OWNER_ID,
                        OWNER_NAME, PROJECTS.STATUS)
                .orderBy(memberCount.asc())
                .fetch()
                .map(r -> ProjectDirectoryEntryModel.builder()
                        .id(r.get(PROJECTS.ID))
                        .friendlyId(r.get(PROJECTS.FRIENDLY_ID))
                        .name(r.get(PROJECTS.NAME))
                        .ownerId(r.get(PROJECTS.OWNER_ID))
                        .ownerName(r.get(OWNER_NAME))
                        .status(ProjectStatus.valueOf(r.get(PROJECTS.STATUS)))
                        .memberCount(r.get(memberCount))
                        .build());
    }

    public boolean existsByNameAndOwnerIdAndDeletedAtIsNull(String name, UUID ownerId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(PROJECTS)
                        .where(PROJECTS.NAME.equalIgnoreCase(name))
                        .and(PROJECTS.OWNER_ID.eq(ownerId))
                        .and(PROJECTS.DELETED_AT.isNull()));
    }

    public boolean existsByNameAndOwnerIdAndIdNotAndDeletedAtIsNull(String name, UUID ownerId, UUID excludeId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(PROJECTS)
                        .where(PROJECTS.NAME.equalIgnoreCase(name))
                        .and(PROJECTS.OWNER_ID.eq(ownerId))
                        .and(PROJECTS.ID.notEqual(excludeId))
                        .and(PROJECTS.DELETED_AT.isNull()));
    }

    public int countOpenJobsByProjectId(UUID projectId) {
        return dsl.fetchCount(
                dsl.selectOne()
                        .from(JOBS)
                        .where(JOBS.PROJECT_ID.eq(projectId))
                        .and(JOBS.STATUS.in("IN_PROGRESS", "BLOCKED"))
                        .and(JOBS.DELETED_AT.isNull()));
    }

    public int countActiveByOrgId(UUID orgId) {
        return dsl.fetchCount(
                dsl.selectFrom(PROJECTS)
                        .where(PROJECTS.ORGANISATION_ID.eq(orgId))
                        .and(PROJECTS.STATUS.eq(ProjectStatus.ACTIVE.name()))
                        .and(PROJECTS.DELETED_AT.isNull()));
    }

    public Optional<ProjectModel> findById(UUID id) {
        return selectWithOwner()
                .where(PROJECTS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    public ProjectModel save(ProjectModel project) {
        if (project.getId() == null) {
            UUID id = dsl.insertInto(PROJECTS)
                    .set(PROJECTS.FRIENDLY_ID, project.getFriendlyId())
                    .set(PROJECTS.NAME, project.getName())
                    .set(PROJECTS.DESCRIPTION, project.getDescription())
                    .set(PROJECTS.OWNER_ID, project.getOwnerId())
                    .set(PROJECTS.ORGANISATION_ID, project.getOrganisationId())
                    .set(PROJECTS.STATUS, project.getStatus().name())
                    .set(PROJECTS.CREATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .set(PROJECTS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .returning(PROJECTS.ID)
                    .fetchSingle()
                    .getId();
            return findById(id).orElseThrow();
        }
        dsl.update(PROJECTS)
                .set(PROJECTS.NAME, project.getName())
                .set(PROJECTS.DESCRIPTION, project.getDescription())
                .set(PROJECTS.STATUS, project.getStatus().name())
                .set(PROJECTS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .set(PROJECTS.DELETED_AT, toLocalDateTime(project.getDeletedAt()))
                .where(PROJECTS.ID.eq(project.getId()))
                .execute();
        return findById(project.getId()).orElseThrow();
    }

    public void deleteAll() {
        dsl.deleteFrom(PROJECTS).execute();
    }

    private org.jooq.SelectOnConditionStep<Record> selectWithOwner() {
        return dsl.select(of(
                        PROJECTS.ID,
                        PROJECTS.FRIENDLY_ID,
                        PROJECTS.NAME,
                        PROJECTS.DESCRIPTION,
                        PROJECTS.OWNER_ID,
                        OWNER_NAME,
                        PROJECTS.ORGANISATION_ID,
                        PROJECTS.STATUS,
                        PROJECTS.CREATED_AT,
                        PROJECTS.UPDATED_AT,
                        PROJECTS.DELETED_AT))
                .from(PROJECTS)
                .join(USERS).on(PROJECTS.OWNER_ID.eq(USERS.ID));
    }

    private ProjectModel toModel(Record r) {
        return ProjectModel.builder()
                .id(r.get(PROJECTS.ID))
                .friendlyId(r.get(PROJECTS.FRIENDLY_ID))
                .name(r.get(PROJECTS.NAME))
                .description(r.get(PROJECTS.DESCRIPTION))
                .ownerId(r.get(PROJECTS.OWNER_ID))
                .ownerName(r.get(OWNER_NAME))
                .organisationId(r.get(PROJECTS.ORGANISATION_ID))
                .status(ProjectStatus.valueOf(r.get(PROJECTS.STATUS)))
                .createdAt(toInstant(r.get(PROJECTS.CREATED_AT)))
                .updatedAt(toInstant(r.get(PROJECTS.UPDATED_AT)))
                .deletedAt(toInstant(r.get(PROJECTS.DELETED_AT)))
                .build();
    }
}
