package com.opsclear.dto;

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
    private Integer amount;

    @NotBlank(message = "Reason must not be blank")
    @Size(max = 1000, message = "Reason must not exceed 1000 characters")
    private String reason;

    private UUID submissionId;
}
