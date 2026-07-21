package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.JobLinksRecord;
import com.opsclear.model.JobLinkModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOB_LINKS;

@Repository
@RequiredArgsConstructor
public class JobLinkRepository {

    private final DSLContext dsl;

    private static Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }

    public JobLinkModel save(JobLinkModel link) {
        if (link.getId() == null) {
            UUID id = dsl.insertInto(JOB_LINKS)
                    .set(JOB_LINKS.JOB_ID, link.getJobId())
                    .set(JOB_LINKS.URL, link.getUrl())
                    .set(JOB_LINKS.LABEL, link.getLabel())
                    .set(JOB_LINKS.CREATED_BY, link.getCreatedBy())
                    .returning(JOB_LINKS.ID)
                    .fetchSingle()
                    .getId();
            return findById(id).orElseThrow();
        }
        dsl.update(JOB_LINKS)
                .set(JOB_LINKS.URL, link.getUrl())
                .set(JOB_LINKS.LABEL, link.getLabel())
                .set(JOB_LINKS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(JOB_LINKS.ID.eq(link.getId()))
                .execute();
        return findById(link.getId()).orElseThrow();
    }

    public Optional<JobLinkModel> findById(UUID id) {
        return dsl.selectFrom(JOB_LINKS)
                .where(JOB_LINKS.ID.eq(id))
                .fetchOptional(this::toModel);
    }

    public List<JobLinkModel> findByJobId(UUID jobId) {
        return dsl.selectFrom(JOB_LINKS)
                .where(JOB_LINKS.JOB_ID.eq(jobId))
                .orderBy(JOB_LINKS.CREATED_AT.asc(), JOB_LINKS.ID.asc())
                .fetch()
                .map(this::toModel);
    }

    public List<JobLinkModel> findByJobIds(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(JOB_LINKS)
                .where(JOB_LINKS.JOB_ID.in(jobIds))
                .orderBy(JOB_LINKS.CREATED_AT.asc(), JOB_LINKS.ID.asc())
                .fetch()
                .map(this::toModel);
    }

    public void deleteById(UUID id) {
        dsl.deleteFrom(JOB_LINKS).where(JOB_LINKS.ID.eq(id)).execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(JOB_LINKS).execute();
    }

    private JobLinkModel toModel(JobLinksRecord r) {
        return JobLinkModel.builder()
                .id(r.getId())
                .jobId(r.getJobId())
                .url(r.getUrl())
                .label(r.getLabel())
                .createdBy(r.getCreatedBy())
                .createdAt(toInstant(r.getCreatedAt()))
                .updatedAt(toInstant(r.getUpdatedAt()))
                .build();
    }
}
