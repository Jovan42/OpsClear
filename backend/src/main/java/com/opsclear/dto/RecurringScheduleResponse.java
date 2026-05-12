package com.opsclear.dto;

import com.opsclear.generated.jooq.tables.records.RecurringSchedulesRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringScheduleResponse {

    private static final Instant INDEFINITE_SENTINEL = Instant.parse("9999-01-01T00:00:00Z");

    private UUID id;
    private UUID projectId;
    private UUID templateId;
    private String templateName;
    private String name;
    private String cronExpression;
    private String timezone;
    private Instant pausedUntil;
    private Instant expiresAt;
    private Instant nextRunAt;
    private Instant lastRunAt;
    private int currentRotationIndex;
    private List<ScheduleAssigneeResponse> assignees;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public static RecurringScheduleResponse from(
            RecurringSchedulesRecord schedule,
            List<ScheduleAssigneeResponse> assignees,
            String templateName) {
        Instant now = Instant.now();
        Instant pausedUntil = toInstant(schedule.getPausedUntil());
        Instant expiresAt = toInstant(schedule.getExpiresAt());
        String status = computeStatus(pausedUntil, expiresAt, assignees, now);

        return RecurringScheduleResponse.builder()
                .id(schedule.getId())
                .projectId(schedule.getProjectId())
                .templateId(schedule.getTemplateId())
                .templateName(templateName)
                .name(schedule.getName())
                .cronExpression(schedule.getCronExpression())
                .timezone(schedule.getTimezone())
                .pausedUntil(pausedUntil)
                .expiresAt(expiresAt)
                .nextRunAt(toInstant(schedule.getNextRunAt()))
                .lastRunAt(toInstant(schedule.getLastRunAt()))
                .currentRotationIndex(schedule.getCurrentRotationIndex() != null
                        ? schedule.getCurrentRotationIndex() : 0)
                .assignees(assignees)
                .status(status)
                .createdAt(toInstant(schedule.getCreatedAt()))
                .updatedAt(toInstant(schedule.getUpdatedAt()))
                .build();
    }

    private static String computeStatus(
            Instant pausedUntil, Instant expiresAt,
            List<ScheduleAssigneeResponse> assignees, Instant now) {
        if (expiresAt != null && expiresAt.isBefore(now)) {
            return "EXPIRED";
        }
        if (assignees.isEmpty() && pausedUntil != null
                && pausedUntil.equals(INDEFINITE_SENTINEL)) {
            return "PAUSED_NO_ASSIGNEES";
        }
        if (pausedUntil != null && pausedUntil.isAfter(now)) {
            return "PAUSED";
        }
        return "ACTIVE";
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
