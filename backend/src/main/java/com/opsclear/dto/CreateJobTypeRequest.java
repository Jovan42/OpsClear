package com.opsclear.dto;

import com.opsclear.model.JobTypeColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateJobTypeRequest {

    @NotBlank(message = "Type name is required")
    @Size(max = 100, message = "Type name must be at most 100 characters")
    private String name;

    @NotNull(message = "Color is required")
    private JobTypeColor color;
}
