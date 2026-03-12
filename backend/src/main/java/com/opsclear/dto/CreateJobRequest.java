package com.opsclear.dto;

import com.opsclear.model.JobPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateJobRequest {

    @NotBlank(message = "Job title is required")
    @Size(max = 255, message = "Job title must be at most 255 characters")
    private String title;

    private String description;

    @Size(max = 255, message = "Client must be at most 255 characters")
    private String client;

    private UUID assignedTo;

    private Instant deadline;

    private JobPriority priority;
}
