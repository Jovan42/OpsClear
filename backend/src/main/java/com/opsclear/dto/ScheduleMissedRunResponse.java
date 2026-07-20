package com.opsclear.dto;

import com.opsclear.generated.jooq.tables.records.ScheduleMissedRunsRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleMissedRunResponse {

    private UUID id;
    private UUID scheduleId;
    private Instant expectedAt;
    private Instant recordedAt;

    public static ScheduleMissedRunResponse from(ScheduleMissedRunsRecord r) {
        return builder()
                .id(r.getId())
                .scheduleId(r.getScheduleId())
                .expectedAt(r.getExpectedAt().toInstant())
                .recordedAt(r.getRecordedAt().toInstant())
                .build();
    }
}
