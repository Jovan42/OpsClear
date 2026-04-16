package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.MilestonesRecord;
import com.opsclear.model.MilestoneModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.MILESTONES;

@Repository
@RequiredArgsConstructor
public class MilestoneRepository {

    private final DSLContext dsl;

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }

    public List<MilestoneModel> findActiveByProjectId(UUID projectId) {
        return dsl.selectFrom(MILESTONES)
                .where(MILESTONES.PROJECT_ID.eq(projectId))
                .and(MILESTONES.DELETED_AT.isNull())
                .orderBy(MILESTONES.DEADLINE.asc().nullsLast(), MILESTONES.CREATED_AT.asc())
                .fetch()
                .map(this::toModel);
    }

    public Optional<MilestoneModel> findByIdAndDeletedAtIsNull(UUID id) {
        return dsl.selectFrom(MILESTONES)
                .where(MILESTONES.ID.eq(id))
                .and(MILESTONES.DELETED_AT.isNull())
                .fetchOptional()
                .map(this::toModel);
    }

    public MilestoneModel save(MilestoneModel milestone) {
        if (milestone.getId() == null) {
            UUID id = dsl.insertInto(MILESTONES)
                    .set(MILESTONES.FRIENDLY_ID, milestone.getFriendlyId())
                    .set(MILESTONES.PROJECT_ID, milestone.getProjectId())
                    .set(MILESTONES.NAME, milestone.getName())
                    .set(MILESTONES.DESCRIPTION, milestone.getDescription())
                    .set(MILESTONES.DEADLINE, milestone.getDeadline())
                    .set(MILESTONES.CREATED_AT, LocalDateTime.now(ZoneOffset.UTC))
                    .returning(MILESTONES.ID)
                    .fetchSingle()
                    .getId();
            return findByIdAndDeletedAtIsNull(id).orElseThrow();
        }
        dsl.update(MILESTONES)
                .set(MILESTONES.NAME, milestone.getName())
                .set(MILESTONES.DESCRIPTION, milestone.getDescription())
                .set(MILESTONES.DEADLINE, milestone.getDeadline())
                .where(MILESTONES.ID.eq(milestone.getId()))
                .execute();
        return findByIdAndDeletedAtIsNull(milestone.getId()).orElseThrow();
    }

    public void softDelete(UUID id) {
        dsl.update(MILESTONES)
                .set(MILESTONES.DELETED_AT, LocalDateTime.now(ZoneOffset.UTC))
                .where(MILESTONES.ID.eq(id))
                .execute();
        dsl.update(com.opsclear.generated.jooq.Tables.JOBS)
                .setNull(com.opsclear.generated.jooq.Tables.JOBS.MILESTONE_ID)
                .where(com.opsclear.generated.jooq.Tables.JOBS.MILESTONE_ID.eq(id))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(MILESTONES).execute();
    }

    private MilestoneModel toModel(MilestonesRecord r) {
        return MilestoneModel.builder()
                .id(r.getId())
                .friendlyId(r.getFriendlyId())
                .projectId(r.getProjectId())
                .name(r.getName())
                .description(r.getDescription())
                .deadline(r.getDeadline())
                .createdAt(toInstant(r.getCreatedAt()))
                .deletedAt(toInstant(r.getDeletedAt()))
                .build();
    }
}
