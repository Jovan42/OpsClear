package com.opsclear.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrantCreditRequest {

    @NotNull(message = "Organisation is required")
    private UUID orgId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    // Floor to avoid granting a credit too small to be worth the Paddle sync overhead
    // (a one-time Discount object created and attached per grant, JOB-180).
    @Min(value = 5, message = "Amount must be at least 5")
    private Integer amount;

    @NotBlank(message = "Reason must not be blank")
    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

    private UUID submissionId;
}
