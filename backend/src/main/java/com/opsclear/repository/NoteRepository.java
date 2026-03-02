package com.opsclear.repository;

import com.opsclear.model.NoteModel;
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
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.NOTES;
import static java.util.List.of;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.partitionBy;

@Repository
@RequiredArgsConstructor
public class NoteRepository {

    private static final Field<String> JOB_NAME = JOBS.TITLE.as("job_name");

    private final DSLContext dsl;

    public NoteModel insert(UUID jobId, UUID authorId, String content) {
        UUID id = dsl.insertInto(NOTES)
                .set(NOTES.JOB_ID, jobId)
                .set(NOTES.AUTHOR_ID, authorId)
                .set(NOTES.CONTENT, content)
                .returning(NOTES.ID)
                .fetchSingle()
                .getId();

        return findById(id);
    }

    public List<NoteModel> findByJobId(UUID jobId) {
        return selectWithJob()
                .where(NOTES.JOB_ID.eq(jobId))
                .orderBy(NOTES.CREATED_AT.asc())
                .fetch()
                .map(this::toModel);
    }

    public List<NoteModel> findByProjectId(UUID projectId) {
        Field<LocalDateTime> maxCreatedAt = max(NOTES.CREATED_AT).over(partitionBy(NOTES.JOB_ID));

        return selectWithJob()
                .where(JOBS.PROJECT_ID.eq(projectId))
                .orderBy(maxCreatedAt.desc(), NOTES.CREATED_AT.asc())
                .fetch()
                .map(this::toModel);
    }

    public void deleteAll() {
        dsl.deleteFrom(NOTES).execute();
    }

    private NoteModel findById(UUID id) {
        return selectWithJob()
                .where(NOTES.ID.eq(id))
                .fetchSingle(this::toModel);
    }

    private SelectOnConditionStep<Record> selectWithJob() {
        return dsl.select(of(NOTES.ID, NOTES.JOB_ID, NOTES.AUTHOR_ID, NOTES.CONTENT, NOTES.CREATED_AT, JOB_NAME))
                .from(NOTES)
                .join(JOBS).on(NOTES.JOB_ID.eq(JOBS.ID));
    }

    private NoteModel toModel(Record r) {
        return NoteModel.builder()
                .id(r.get(NOTES.ID))
                .jobId(r.get(NOTES.JOB_ID))
                .jobName(r.get(JOB_NAME))
                .authorId(r.get(NOTES.AUTHOR_ID))
                .content(r.get(NOTES.CONTENT))
                .createdAt(toInstant(r.get(NOTES.CREATED_AT)))
                .build();
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
