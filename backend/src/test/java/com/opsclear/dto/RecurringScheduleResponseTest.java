package com.opsclear.dto;

import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RecurringScheduleResponse")
class RecurringScheduleResponseTest {

    @Test
    @DisplayName("from — returns EXPIRED when expiresAt is before now")
    void from_shouldReturnExpired_whenExpiresAtIsBeforeNow() {
        RecurringSchedulesRecord record = buildRecord();
        record.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        RecurringScheduleResponse result = RecurringScheduleResponse.from(record, List.of(), "Template");

        assertThat(result.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("from — returns ACTIVE when pausedUntil is in the past")
    void from_shouldReturnActive_whenPausedUntilIsInThePast() {
        RecurringSchedulesRecord record = buildRecord();
        record.setPausedUntil(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        RecurringScheduleResponse result = RecurringScheduleResponse.from(record, List.of(), "Template");

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("from — defaults currentRotationIndex to 0 when null")
    void from_shouldDefaultRotationIndex_whenCurrentRotationIndexIsNull() {
        RecurringSchedulesRecord record = buildRecord();
        record.setCurrentRotationIndex(null);

        RecurringScheduleResponse result = RecurringScheduleResponse.from(record, List.of(), "Template");

        assertThat(result.getCurrentRotationIndex()).isEqualTo(0);
    }

    private RecurringSchedulesRecord buildRecord() {
        RecurringSchedulesRecord r = new RecurringSchedulesRecord();
        r.setId(UUID.randomUUID());
        r.setProjectId(UUID.randomUUID());
        r.setTemplateId(UUID.randomUUID());
        r.setName("Test Schedule");
        r.setCronExpression("0 0 9 * * MON");
        r.setTimezone("UTC");
        r.setCurrentRotationIndex(0);
        r.setNextRunAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
        r.setCreatedBy(UUID.randomUUID());
        r.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        r.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return r;
    }
}
