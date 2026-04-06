package com.opsclear.repository;

import com.opsclear.model.JobStatusHistoryModel;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOB_STATUS_HISTORY;
import static com.opsclear.generated.jooq.Tables.USERS;

@Repository
@RequiredArgsConstructor
public class JobStatusHistoryRepository {

    private final DSLContext dsl;

    public void insert(UUID jobId, String changedFrom, String changedTo, UUID changedBy, String blockReason) {
        dsl.insertInto(JOB_STATUS_HISTORY)
                .set(JOB_STATUS_HISTORY.JOB_ID, jobId)
                .set(JOB_STATUS_HISTORY.CHANGED_FROM, changedFrom)
                .set(JOB_STATUS_HISTORY.CHANGED_TO, changedTo)
                .set(JOB_STATUS_HISTORY.CHANGED_BY, changedBy)
                .set(JOB_STATUS_HISTORY.BLOCK_REASON, blockReason)
                .execute();
    }

    public List<JobStatusHistoryModel> findByJobId(UUID jobId) {
        return dsl.select(
                        JOB_STATUS_HISTORY.ID,
                        JOB_STATUS_HISTORY.JOB_ID,
                        JOB_STATUS_HISTORY.CHANGED_FROM,
                        JOB_STATUS_HISTORY.CHANGED_TO,
                        JOB_STATUS_HISTORY.CHANGED_BY,
                        JOB_STATUS_HISTORY.CHANGED_AT,
                        JOB_STATUS_HISTORY.BLOCK_REASON,
                        USERS.NAME.as("changed_by_name"))
                .from(JOB_STATUS_HISTORY)
                .leftJoin(USERS).on(JOB_STATUS_HISTORY.CHANGED_BY.eq(USERS.ID))
                .where(JOB_STATUS_HISTORY.JOB_ID.eq(jobId))
                .orderBy(JOB_STATUS_HISTORY.CHANGED_AT.asc())
                .fetch()
                .map(this::toModel);
    }

    public void deleteAll() {
        dsl.deleteFrom(JOB_STATUS_HISTORY).execute();
    }

    private JobStatusHistoryModel toModel(Record r) {
        LocalDateTime ldt = r.get(JOB_STATUS_HISTORY.CHANGED_AT);
        return JobStatusHistoryModel.builder()
                .id(r.get(JOB_STATUS_HISTORY.ID))
                .jobId(r.get(JOB_STATUS_HISTORY.JOB_ID))
                .changedFrom(r.get(JOB_STATUS_HISTORY.CHANGED_FROM))
                .changedTo(r.get(JOB_STATUS_HISTORY.CHANGED_TO))
                .changedBy(r.get(JOB_STATUS_HISTORY.CHANGED_BY))
                .changedByName(r.get("changed_by_name", String.class))
                .changedAt(ldt != null ? ldt.toInstant(ZoneOffset.UTC) : null)
                .blockReason(r.get(JOB_STATUS_HISTORY.BLOCK_REASON))
                .build();
    }
}
