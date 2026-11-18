package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.OrgSettingsRecord;
import com.opsclear.model.FriendlyIdEntityType;
import com.opsclear.model.OrgSettingsModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Select;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOB_TEMPLATES;
import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.MILESTONES;
import static com.opsclear.generated.jooq.Tables.ORG_SEQUENCES;
import static com.opsclear.generated.jooq.Tables.ORG_SETTINGS;
import static com.opsclear.generated.jooq.Tables.PROJECTS;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.upper;

@Repository
@RequiredArgsConstructor
public class OrgSettingsRepository {

    private final DSLContext dsl;

    // created_at / updated_at are NOT NULL, so no null handling is needed here
    private static Instant toInstant(LocalDateTime ldt) {
        return ldt.toInstant(ZoneOffset.UTC);
    }

    public Optional<OrgSettingsModel> findByOrgId(UUID orgId) {
        return dsl.selectFrom(ORG_SETTINGS)
                .where(ORG_SETTINGS.ORG_ID.eq(orgId))
                .fetchOptional()
                .map(this::toModel);
    }

    /**
     * Writes the four prefixes and sets updated_at. The row itself is seeded when the org is created.
     */
    public OrgSettingsModel update(OrgSettingsModel model) {
        dsl.update(ORG_SETTINGS)
                .set(ORG_SETTINGS.PROJECT_PREFIX, model.getProjectPrefix())
                .set(ORG_SETTINGS.JOB_PREFIX, model.getJobPrefix())
                .set(ORG_SETTINGS.MILESTONE_PREFIX, model.getMilestonePrefix())
                .set(ORG_SETTINGS.TEMPLATE_PREFIX, model.getTemplatePrefix())
                .set(ORG_SETTINGS.UPDATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(ORG_SETTINGS.ORG_ID.eq(model.getOrgId()))
                .execute();
        return toModel(dsl.selectFrom(ORG_SETTINGS)
                .where(ORG_SETTINGS.ORG_ID.eq(model.getOrgId()))
                .fetchSingle());
    }

    /**
     * Returns org_sequences.last_value per entity type for the org. A type without a row maps to 0.
     */
    public Map<FriendlyIdEntityType, Integer> findLastValues(UUID orgId) {
        Map<FriendlyIdEntityType, Integer> values = new EnumMap<>(FriendlyIdEntityType.class);
        for (FriendlyIdEntityType type : FriendlyIdEntityType.values()) {
            values.put(type, 0);
        }
        dsl.select(ORG_SEQUENCES.ENTITY_TYPE, ORG_SEQUENCES.LAST_VALUE)
                .from(ORG_SEQUENCES)
                .where(ORG_SEQUENCES.ORG_ID.eq(orgId))
                .and(ORG_SEQUENCES.ENTITY_TYPE.in(values.keySet().stream().map(Enum::name).toList()))
                .fetch()
                .forEach(r -> values.put(FriendlyIdEntityType.valueOf(r.value1()), r.value2()));
        return values;
    }

    /**
     * Returns one of the other three entity types whose non-deleted records in the org carry a
     * friendly ID starting with {@code PREFIX-}, or empty if none do. A single query: a UNION ALL of
     * one LIMIT 1 probe per other type, scoped to the org (projects by organisation_id, jobs and
     * milestones through their project, templates through their project or org).
     */
    public Optional<FriendlyIdEntityType> findOtherTypeUsingPrefix(
            UUID orgId, String prefix, FriendlyIdEntityType type) {
        String pattern = prefix.toUpperCase() + "-%";
        Select<Record1<String>> union = null;
        for (FriendlyIdEntityType other : FriendlyIdEntityType.values()) {
            if (other != type) {
                Select<Record1<String>> probe = usageProbe(other, orgId, pattern);
                union = union == null ? probe : union.unionAll(probe);
            }
        }
        return dsl.selectFrom(union.asTable("prefix_usage"))
                .limit(1)
                .fetchOptional(0, String.class)
                .map(FriendlyIdEntityType::valueOf);
    }

    private Select<Record1<String>> usageProbe(FriendlyIdEntityType type, UUID orgId, String pattern) {
        Field<String> label = inline(type.name()).as("entity_type");
        return switch (type) {
            case PROJECT -> dsl.select(label)
                    .from(PROJECTS)
                    .where(PROJECTS.ORGANISATION_ID.eq(orgId))
                    .and(PROJECTS.DELETED_AT.isNull())
                    .and(upper(PROJECTS.FRIENDLY_ID).like(pattern))
                    .limit(1);
            case JOB -> dsl.select(label)
                    .from(JOBS)
                    .join(PROJECTS).on(PROJECTS.ID.eq(JOBS.PROJECT_ID))
                    .where(PROJECTS.ORGANISATION_ID.eq(orgId))
                    .and(JOBS.DELETED_AT.isNull())
                    .and(upper(JOBS.FRIENDLY_ID).like(pattern))
                    .limit(1);
            case MILESTONE -> dsl.select(label)
                    .from(MILESTONES)
                    .join(PROJECTS).on(PROJECTS.ID.eq(MILESTONES.PROJECT_ID))
                    .where(PROJECTS.ORGANISATION_ID.eq(orgId))
                    .and(MILESTONES.DELETED_AT.isNull())
                    .and(upper(MILESTONES.FRIENDLY_ID).like(pattern))
                    .limit(1);
            case TEMPLATE -> dsl.select(label)
                    .from(JOB_TEMPLATES)
                    .leftJoin(PROJECTS).on(PROJECTS.ID.eq(JOB_TEMPLATES.PROJECT_ID))
                    .where(JOB_TEMPLATES.ORG_ID.eq(orgId).or(PROJECTS.ORGANISATION_ID.eq(orgId)))
                    .and(JOB_TEMPLATES.DELETED_AT.isNull())
                    .and(upper(JOB_TEMPLATES.FRIENDLY_ID).like(pattern))
                    .limit(1);
        };
    }

    private OrgSettingsModel toModel(OrgSettingsRecord r) {
        return OrgSettingsModel.builder()
                .orgId(r.getOrgId())
                .projectPrefix(r.getProjectPrefix())
                .jobPrefix(r.getJobPrefix())
                .milestonePrefix(r.getMilestonePrefix())
                .templatePrefix(r.getTemplatePrefix())
                .createdAt(toInstant(r.getCreatedAt()))
                .updatedAt(toInstant(r.getUpdatedAt()))
                .build();
    }
}
