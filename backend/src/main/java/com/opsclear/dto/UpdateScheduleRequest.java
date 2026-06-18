package com.opsclear.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class UpdateScheduleRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotNull(message = "Template is required")
    private UUID templateId;

    @NotBlank(message = "Cron expression is required")
    @Size(max = 100, message = "Cron expression must be at most 100 characters")
    private String cronExpression;

    @NotBlank(message = "Timezone is required")
    @Size(max = 60, message = "Timezone must be at most 60 characters")
    private String timezone;

    private Instant pausedUntil;

    private Instant expiresAt;

    @NotNull(message = "Assignees list is required")
    private List<UUID> assigneeIds;
}
