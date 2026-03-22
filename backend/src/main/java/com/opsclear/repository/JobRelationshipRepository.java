package com.opsclear.repository;

import com.opsclear.model.JobRelationshipModel;
import com.opsclear.model.JobRelationshipType;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOB_RELATIONSHIPS;

@Repository
@RequiredArgsConstructor
public class JobRelationshipRepository {

    private final DSLContext dsl;

    private static Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }

    public List<JobRelationshipModel> findByJobId(UUID jobId) {
        return dsl.select()
                .from(JOB_RELATIONSHIPS)
                .where(JOB_RELATIONSHIPS.SOURCE_JOB_ID.eq(jobId)
                        .or(JOB_RELATIONSHIPS.TARGET_JOB_ID.eq(jobId)))
                .fetch()
                .map(this::toModel);
    }

    public Optional<JobRelationshipModel> findById(UUID id) {
        return dsl.select()
                .from(JOB_RELATIONSHIPS)
                .where(JOB_RELATIONSHIPS.ID.eq(id))
                .fetchOptional()
                .map(this::toModel);
    }

    public boolean existsBySourceJobIdAndTargetJobIdAndType(UUID sourceJobId, UUID targetJobId,
                                                             JobRelationshipType type) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(JOB_RELATIONSHIPS)
                        .where(JOB_RELATIONSHIPS.SOURCE_JOB_ID.eq(sourceJobId))
                        .and(JOB_RELATIONSHIPS.TARGET_JOB_ID.eq(targetJobId))
                        .and(JOB_RELATIONSHIPS.TYPE.eq(type.name())));
    }

    public JobRelationshipModel save(JobRelationshipModel model) {
        UUID id = dsl.insertInto(JOB_RELATIONSHIPS)
                .set(JOB_RELATIONSHIPS.SOURCE_JOB_ID, model.getSourceJobId())
                .set(JOB_RELATIONSHIPS.TARGET_JOB_ID, model.getTargetJobId())
                .set(JOB_RELATIONSHIPS.TYPE, model.getType().name())
                .set(JOB_RELATIONSHIPS.CREATED_BY, model.getCreatedBy())
                .returning(JOB_RELATIONSHIPS.ID)
                .fetchSingle()
                .getId();
        return findById(id).orElseThrow();
    }

    public void deleteById(UUID id) {
        dsl.deleteFrom(JOB_RELATIONSHIPS)
                .where(JOB_RELATIONSHIPS.ID.eq(id))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(JOB_RELATIONSHIPS).execute();
    }

    private JobRelationshipModel toModel(Record r) {
        return JobRelationshipModel.builder()
                .id(r.get(JOB_RELATIONSHIPS.ID))
                .sourceJobId(r.get(JOB_RELATIONSHIPS.SOURCE_JOB_ID))
                .targetJobId(r.get(JOB_RELATIONSHIPS.TARGET_JOB_ID))
                .type(JobRelationshipType.valueOf(r.get(JOB_RELATIONSHIPS.TYPE)))
                .createdBy(r.get(JOB_RELATIONSHIPS.CREATED_BY))
                .createdAt(toInstant(r.get(JOB_RELATIONSHIPS.CREATED_AT)))
                .build();
    }
}
