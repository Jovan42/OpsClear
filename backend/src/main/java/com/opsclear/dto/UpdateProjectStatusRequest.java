package com.opsclear.dto;

import com.opsclear.model.ProjectStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProjectStatusRequest {

    @NotNull
    private ProjectStatus status;
}
