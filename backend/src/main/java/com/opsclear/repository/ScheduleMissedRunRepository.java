package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.ScheduleMissedRunsRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.SCHEDULE_MISSED_RUNS;

@Repository
@RequiredArgsConstructor
public class ScheduleMissedRunRepository {

    private final DSLContext dsl;

    public List<ScheduleMissedRunsRecord> findByScheduleId(UUID scheduleId) {
        return dsl.selectFrom(SCHEDULE_MISSED_RUNS)
                .where(SCHEDULE_MISSED_RUNS.SCHEDULE_ID.eq(scheduleId))
                .orderBy(SCHEDULE_MISSED_RUNS.EXPECTED_AT.asc())
                .fetch();
    }

    public ScheduleMissedRunsRecord insert(UUID scheduleId, Instant expectedAt) {
        UUID id = dsl.insertInto(SCHEDULE_MISSED_RUNS)
                .set(SCHEDULE_MISSED_RUNS.SCHEDULE_ID, scheduleId)
                .set(SCHEDULE_MISSED_RUNS.EXPECTED_AT, expectedAt.atOffset(ZoneOffset.UTC))
                .returning(SCHEDULE_MISSED_RUNS.ID)
                .fetchSingle()
                .getId();
        return dsl.selectFrom(SCHEDULE_MISSED_RUNS)
                .where(SCHEDULE_MISSED_RUNS.ID.eq(id))
                .fetchSingle();
    }

    public int deleteById(UUID id) {
        return dsl.deleteFrom(SCHEDULE_MISSED_RUNS)
                .where(SCHEDULE_MISSED_RUNS.ID.eq(id))
                .execute();
    }

    public void deleteAllForSchedule(UUID scheduleId) {
        dsl.deleteFrom(SCHEDULE_MISSED_RUNS)
                .where(SCHEDULE_MISSED_RUNS.SCHEDULE_ID.eq(scheduleId))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(SCHEDULE_MISSED_RUNS).execute();
    }
}
