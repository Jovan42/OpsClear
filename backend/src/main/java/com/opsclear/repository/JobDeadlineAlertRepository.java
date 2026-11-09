package com.opsclear.repository;

import com.opsclear.model.DeadlineAlertKind;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.JOB_DEADLINE_ALERTS;

/**
 * Which deadline alerts were already sent (ADR-0051), one row per job, kind and deadline value. Kept apart from the
 * notifications themselves, which are pruned, so a still-overdue job doesn't alert again; keyed on the deadline, so a
 * moved deadline does.
 */
@Repository
@RequiredArgsConstructor
public class JobDeadlineAlertRepository {

    private final DSLContext dsl;

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * Records the alert unless a row for this job, kind and deadline already exists ({@code ON CONFLICT DO NOTHING}),
     * so overlapping runs can't both record it. Returns whether this call inserted the row.
     */
    public boolean insertIfAbsent(UUID jobId, DeadlineAlertKind kind, Instant deadline) {
        return dsl.insertInto(JOB_DEADLINE_ALERTS)
                .set(JOB_DEADLINE_ALERTS.JOB_ID, jobId)
                .set(JOB_DEADLINE_ALERTS.KIND, kind.name())
                .set(JOB_DEADLINE_ALERTS.DEADLINE, toLocalDateTime(deadline))
                .set(JOB_DEADLINE_ALERTS.SENT_AT, LocalDateTime.now(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute() == 1;
    }

    public void deleteAll() {
        dsl.deleteFrom(JOB_DEADLINE_ALERTS).execute();
    }
}
