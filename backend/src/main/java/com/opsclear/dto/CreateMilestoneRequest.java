package com.opsclear.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMilestoneRequest {

    @NotBlank(message = "Milestone name is required")
    @Size(max = 100, message = "Milestone name must be at most 100 characters")
    private String name;

    private String description;

    private LocalDate deadline;
}
