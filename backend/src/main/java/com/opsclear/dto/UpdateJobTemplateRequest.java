package com.opsclear.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class UpdateJobTemplateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 255)
    private String title;

    private String description;

    @Size(max = 255)
    private String client;

    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL")
    private String priority;

    @Pattern(regexp = "NONE|FIXED|ASK")
    private String assigneeMode;

    private UUID assigneeId;

    private UUID milestoneId;

    @Positive
    private Integer deadlineOffsetDays;
}
