package com.opsclear.dto;

import com.opsclear.model.ApprovalStatus;
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
public class DecideApprovalRequest {

    @NotNull(message = "Status is required")
    private ApprovalStatus status;

    @Size(max = 1000, message = "Comment must not exceed 1000 characters")
    private String comment;
}
