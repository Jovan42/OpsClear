package com.opsclear.repository;

import com.opsclear.model.NoteModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOBS;
import static com.opsclear.generated.jooq.Tables.NOTES;
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
                .fetchOne()
                .getId();

        return dsl.selectFrom(NOTES)
                .where(NOTES.ID.eq(id))
                .fetchOne(r -> NoteModel.builder()
                        .id(r.getId())
                        .jobId(r.getJobId())
                        .authorId(r.getAuthorId())
                        .content(r.getContent())
                        .createdAt(toInstant(r.getCreatedAt()))
                        .build());
    }

    public List<NoteModel> findByJobId(UUID jobId) {
        return dsl.selectFrom(NOTES)
                .where(NOTES.JOB_ID.eq(jobId))
                .orderBy(NOTES.CREATED_AT.asc())
                .fetch()
                .map(r -> NoteModel.builder()
                        .id(r.getId())
                        .jobId(r.getJobId())
                        .authorId(r.getAuthorId())
                        .content(r.getContent())
                        .createdAt(toInstant(r.getCreatedAt()))
                        .build());
    }

    public List<NoteModel> findByProjectId(UUID projectId) {
        Field<LocalDateTime> maxCreatedAt = max(NOTES.CREATED_AT).over(partitionBy(NOTES.JOB_ID));

        return dsl.select(NOTES.ID, NOTES.JOB_ID, NOTES.AUTHOR_ID, NOTES.CONTENT, NOTES.CREATED_AT, JOB_NAME)
                .from(NOTES)
                .join(JOBS).on(NOTES.JOB_ID.eq(JOBS.ID))
                .where(JOBS.PROJECT_ID.eq(projectId))
                .orderBy(maxCreatedAt.desc(), NOTES.CREATED_AT.asc())
                .fetch()
                .map(r -> NoteModel.builder()
                        .id(r.get(NOTES.ID))
                        .jobId(r.get(NOTES.JOB_ID))
                        .jobName(r.get(JOB_NAME))
                        .authorId(r.get(NOTES.AUTHOR_ID))
                        .content(r.get(NOTES.CONTENT))
                        .createdAt(toInstant(r.get(NOTES.CREATED_AT)))
                        .build());
    }

    public void deleteAll() {
        dsl.deleteFrom(NOTES).execute();
    }

    private static Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null;
    }
}
