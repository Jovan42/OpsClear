package com.opsclear.repository;

import com.opsclear.model.FriendlyIdEntityType;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOB_TEMPLATES;
import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.MILESTONES;
import static com.opsclear.generated.jooq.Tables.ORG_SEQUENCES;
import static com.opsclear.generated.jooq.Tables.ORG_SETTINGS;
import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static org.jooq.impl.DSL.upper;

@Repository
@RequiredArgsConstructor
public class FriendlyIdRepository {

    private final DSLContext dsl;

    /**
     * Atomically increments the sequence counter for the given org and entity type,
     * returning the new value. Safe under concurrent inserts — UPDATE is row-locked by Postgres.
     */
    public int incrementAndGet(UUID orgId, FriendlyIdEntityType entityType) {
        return dsl.update(ORG_SEQUENCES)
                .set(ORG_SEQUENCES.LAST_VALUE, ORG_SEQUENCES.LAST_VALUE.add(1))
                .where(ORG_SEQUENCES.ORG_ID.eq(orgId))
                .and(ORG_SEQUENCES.ENTITY_TYPE.eq(entityType.name()))
                .returning(ORG_SEQUENCES.LAST_VALUE)
                .fetchSingle()
                .getLastValue();
    }

    /**
     * Returns the configured prefix for the given entity type in this org.
     */
    public String getPrefix(UUID orgId, FriendlyIdEntityType entityType) {
        return dsl.select(prefixField(entityType))
                .from(ORG_SETTINGS)
                .where(ORG_SETTINGS.ORG_ID.eq(orgId))
                .fetchSingle(prefixField(entityType));
    }

    /**
     * Seeds org_settings (with defaults) and org_sequences (zeroed) for a newly created org.
     * Called inside the same transaction as org creation.
     */
    public void seedForOrg(UUID orgId) {
        dsl.insertInto(ORG_SETTINGS)
                .set(ORG_SETTINGS.ORG_ID, orgId)
                .onDuplicateKeyIgnore()
                .execute();

        for (FriendlyIdEntityType type : FriendlyIdEntityType.values()) {
            dsl.insertInto(ORG_SEQUENCES)
                    .set(ORG_SEQUENCES.ORG_ID, orgId)
                    .set(ORG_SEQUENCES.ENTITY_TYPE, type.name())
                    .set(ORG_SEQUENCES.LAST_VALUE, 0)
                    .onDuplicateKeyIgnore()
                    .execute();
        }
    }

    /**
     * Resolves a friendly ID to the project UUID, scoped to the given org.
     * Case-insensitive. Returns empty if not found or belongs to a different org.
     */
    public Optional<UUID> findProjectId(String friendlyId, UUID orgId) {
        return dsl.select(PROJECTS.ID)
                .from(PROJECTS)
                .where(upper(PROJECTS.FRIENDLY_ID).eq(friendlyId.toUpperCase()))
                .and(PROJECTS.ORGANISATION_ID.eq(orgId))
                .and(PROJECTS.DELETED_AT.isNull())
                .fetchOptional(PROJECTS.ID);
    }

    /**
     * Resolves a friendly ID to the job UUID, scoped to the given org via project join.
     * Case-insensitive. Returns empty if not found or belongs to a different org.
     */
    public Optional<UUID> findJobId(String friendlyId, UUID orgId) {
        return dsl.select(JOBS.ID)
                .from(JOBS)
                .join(PROJECTS).on(PROJECTS.ID.eq(JOBS.PROJECT_ID))
                .where(upper(JOBS.FRIENDLY_ID).eq(friendlyId.toUpperCase()))
                .and(PROJECTS.ORGANISATION_ID.eq(orgId))
                .and(JOBS.DELETED_AT.isNull())
                .fetchOptional(JOBS.ID);
    }

    /**
     * Resolves a friendly ID to the milestone UUID, scoped to the given org via project join.
     * Case-insensitive. Returns empty if not found or belongs to a different org.
     */
    public Optional<UUID> findMilestoneId(String friendlyId, UUID orgId) {
        return dsl.select(MILESTONES.ID)
                .from(MILESTONES)
                .join(PROJECTS).on(PROJECTS.ID.eq(MILESTONES.PROJECT_ID))
                .where(upper(MILESTONES.FRIENDLY_ID).eq(friendlyId.toUpperCase()))
                .and(PROJECTS.ORGANISATION_ID.eq(orgId))
                .and(MILESTONES.DELETED_AT.isNull())
                .fetchOptional(MILESTONES.ID);
    }

    /**
     * Resolves a friendly ID to the job template UUID, scoped to the given org via project join.
     * Case-insensitive. Returns empty if not found or belongs to a different org.
     */
    public Optional<UUID> findTemplateId(String friendlyId, UUID orgId) {
        return dsl.select(JOB_TEMPLATES.ID)
                .from(JOB_TEMPLATES)
                .join(PROJECTS).on(PROJECTS.ID.eq(JOB_TEMPLATES.PROJECT_ID))
                .where(upper(JOB_TEMPLATES.FRIENDLY_ID).eq(friendlyId.toUpperCase()))
                .and(PROJECTS.ORGANISATION_ID.eq(orgId))
                .and(JOB_TEMPLATES.DELETED_AT.isNull())
                .fetchOptional(JOB_TEMPLATES.ID);
    }

    private Field<String> prefixField(FriendlyIdEntityType type) {
        return switch (type) {
            case PROJECT -> ORG_SETTINGS.PROJECT_PREFIX;
            case JOB -> ORG_SETTINGS.JOB_PREFIX;
            case MILESTONE -> ORG_SETTINGS.MILESTONE_PREFIX;
            case TEMPLATE -> ORG_SETTINGS.TEMPLATE_PREFIX;
        };
    }
}
