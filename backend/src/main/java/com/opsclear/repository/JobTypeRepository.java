package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.JobTypesRecord;
import com.opsclear.model.JobTypeColor;
import com.opsclear.model.JobTypeModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.JOB_TYPES;

@Repository
@RequiredArgsConstructor
public class JobTypeRepository {

    private final DSLContext dsl;

    private static Instant toInstant(OffsetDateTime odt) {
        return odt.toInstant();
    }

    public List<JobTypeModel> findByProjectId(UUID projectId) {
        return dsl.selectFrom(JOB_TYPES)
                .where(JOB_TYPES.PROJECT_ID.eq(projectId))
                .orderBy(JOB_TYPES.DISPLAY_ORDER.asc())
                .fetch()
                .map(this::toModel);
    }

    public Optional<JobTypeModel> findById(UUID id) {
        return dsl.selectFrom(JOB_TYPES)
                .where(JOB_TYPES.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    public int nextDisplayOrder(UUID projectId) {
        Integer max = dsl.select(DSL.max(JOB_TYPES.DISPLAY_ORDER))
                .from(JOB_TYPES)
                .where(JOB_TYPES.PROJECT_ID.eq(projectId))
                .fetchOne(0, Integer.class);
        return max != null ? max + 1 : 0;
    }

    public JobTypeModel save(JobTypeModel type) {
        com.opsclear.generated.jooq.enums.JobTypeColor jooqColor =
                com.opsclear.generated.jooq.enums.JobTypeColor.valueOf(type.getColor().name());

        if (type.getId() == null) {
            UUID id = dsl.insertInto(JOB_TYPES)
                    .set(JOB_TYPES.PROJECT_ID, type.getProjectId())
                    .set(JOB_TYPES.NAME, type.getName())
                    .set(JOB_TYPES.COLOR, jooqColor)
                    .set(JOB_TYPES.DISPLAY_ORDER, type.getDisplayOrder())
                    .returning(JOB_TYPES.ID)
                    .fetchSingle()
                    .getId();
            return findById(id).orElseThrow();
        }
        dsl.update(JOB_TYPES)
                .set(JOB_TYPES.NAME, type.getName())
                .set(JOB_TYPES.COLOR, jooqColor)
                .set(JOB_TYPES.DISPLAY_ORDER, type.getDisplayOrder())
                .set(JOB_TYPES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(JOB_TYPES.ID.eq(type.getId()))
                .execute();
        return findById(type.getId()).orElseThrow();
    }

    public void delete(UUID id) {
        dsl.deleteFrom(JOB_TYPES).where(JOB_TYPES.ID.eq(id)).execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(JOB_TYPES).execute();
    }

    public int countJobsReferencing(UUID typeId) {
        return dsl.fetchCount(
                dsl.selectFrom(JOBS)
                        .where(JOBS.TYPE_ID.eq(typeId))
                        .and(JOBS.DELETED_AT.isNull()));
    }

    private JobTypeModel toModel(JobTypesRecord r) {
        return JobTypeModel.builder()
                .id(r.getId())
                .projectId(r.getProjectId())
                .name(r.getName())
                .color(JobTypeColor.valueOf(r.getColor().name()))
                .displayOrder(r.getDisplayOrder())
                .createdAt(toInstant(r.getCreatedAt()))
                .updatedAt(toInstant(r.getUpdatedAt()))
                .build();
    }
}
