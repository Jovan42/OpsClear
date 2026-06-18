package com.opsclear.repository;

import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.opsclear.generated.jooq.Tables.RECURRING_SCHEDULES;

@Repository
@RequiredArgsConstructor
public class RecurringScheduleRepository {

    private final DSLContext dsl;

    public List<RecurringSchedulesRecord> findByProjectId(UUID projectId) {
        return dsl.selectFrom(RECURRING_SCHEDULES)
                .where(RECURRING_SCHEDULES.PROJECT_ID.eq(projectId))
                .and(RECURRING_SCHEDULES.DELETED_AT.isNull())
                .orderBy(RECURRING_SCHEDULES.CREATED_AT.desc())
                .fetch();
    }

    public Optional<RecurringSchedulesRecord> findById(UUID id) {
        return dsl.selectFrom(RECURRING_SCHEDULES)
                .where(RECURRING_SCHEDULES.ID.eq(id))
                .and(RECURRING_SCHEDULES.DELETED_AT.isNull())
                .fetchOptional();
    }

    public List<RecurringSchedulesRecord> findByTemplateIdAndActive(UUID templateId) {
        return dsl.selectFrom(RECURRING_SCHEDULES)
                .where(RECURRING_SCHEDULES.TEMPLATE_ID.eq(templateId))
                .and(RECURRING_SCHEDULES.PAUSED_UNTIL.isNull())
                .and(RECURRING_SCHEDULES.DELETED_AT.isNull())
                .fetch();
    }

    public List<RecurringSchedulesRecord> findDue(Instant now) {
        OffsetDateTime odt = toOffsetDateTime(now);
        return dsl.selectFrom(RECURRING_SCHEDULES)
                .where(RECURRING_SCHEDULES.DELETED_AT.isNull())
                .and(RECURRING_SCHEDULES.PAUSED_UNTIL.isNull()
                        .or(RECURRING_SCHEDULES.PAUSED_UNTIL.le(odt)))
                .and(RECURRING_SCHEDULES.NEXT_RUN_AT.le(odt))
                .and(RECURRING_SCHEDULES.EXPIRES_AT.isNull()
                        .or(RECURRING_SCHEDULES.EXPIRES_AT.gt(odt)))
                .fetch();
    }

    public RecurringSchedulesRecord insert(RecurringSchedulesRecord record) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID id = dsl.insertInto(RECURRING_SCHEDULES)
                .set(RECURRING_SCHEDULES.PROJECT_ID, record.getProjectId())
                .set(RECURRING_SCHEDULES.TEMPLATE_ID, record.getTemplateId())
                .set(RECURRING_SCHEDULES.NAME, record.getName())
                .set(RECURRING_SCHEDULES.CRON_EXPRESSION, record.getCronExpression())
                .set(RECURRING_SCHEDULES.TIMEZONE, record.getTimezone())
                .set(RECURRING_SCHEDULES.PAUSED_UNTIL, record.getPausedUntil())
                .set(RECURRING_SCHEDULES.EXPIRES_AT, record.getExpiresAt())
                .set(RECURRING_SCHEDULES.CURRENT_ROTATION_INDEX, 0)
                .set(RECURRING_SCHEDULES.NEXT_RUN_AT, record.getNextRunAt())
                .set(RECURRING_SCHEDULES.CREATED_BY, record.getCreatedBy())
                .set(RECURRING_SCHEDULES.CREATED_AT, now)
                .set(RECURRING_SCHEDULES.UPDATED_AT, now)
                .returning(RECURRING_SCHEDULES.ID)
                .fetchSingle()
                .getId();
        return findById(id).orElseThrow();
    }

    public RecurringSchedulesRecord update(RecurringSchedulesRecord record) {
        dsl.update(RECURRING_SCHEDULES)
                .set(RECURRING_SCHEDULES.NAME, record.getName())
                .set(RECURRING_SCHEDULES.TEMPLATE_ID, record.getTemplateId())
                .set(RECURRING_SCHEDULES.CRON_EXPRESSION, record.getCronExpression())
                .set(RECURRING_SCHEDULES.TIMEZONE, record.getTimezone())
                .set(RECURRING_SCHEDULES.PAUSED_UNTIL, record.getPausedUntil())
                .set(RECURRING_SCHEDULES.EXPIRES_AT, record.getExpiresAt())
                .set(RECURRING_SCHEDULES.NEXT_RUN_AT, record.getNextRunAt())
                .set(RECURRING_SCHEDULES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(RECURRING_SCHEDULES.ID.eq(record.getId()))
                .execute();
        return findById(record.getId()).orElseThrow();
    }

    public void updateAfterRun(UUID id, Instant nextRunAt, Instant lastRunAt, int newRotationIndex) {
        dsl.update(RECURRING_SCHEDULES)
                .set(RECURRING_SCHEDULES.NEXT_RUN_AT, toOffsetDateTime(nextRunAt))
                .set(RECURRING_SCHEDULES.LAST_RUN_AT, toOffsetDateTime(lastRunAt))
                .set(RECURRING_SCHEDULES.CURRENT_ROTATION_INDEX, newRotationIndex)
                .set(RECURRING_SCHEDULES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(RECURRING_SCHEDULES.ID.eq(id))
                .execute();
    }

    public void delete(UUID id) {
        dsl.update(RECURRING_SCHEDULES)
                .set(RECURRING_SCHEDULES.DELETED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .where(RECURRING_SCHEDULES.ID.eq(id))
                .execute();
    }

    public void deleteAll() {
        dsl.deleteFrom(RECURRING_SCHEDULES).execute();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
