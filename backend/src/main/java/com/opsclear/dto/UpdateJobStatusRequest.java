package com.opsclear.dto;

import com.opsclear.model.JobStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateJobStatusRequest {

    @NotNull(message = "Status is required")
    private JobStatus status;

    // Required when status == BLOCKED; ignored for all other transitions
    private String reason;

    // ADR-0052: optional message for a completion request on a gated project; ignored otherwise
    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    private String message;
}
