package com.opsclear.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpsertSubscriptionRequest {

    @NotNull(message = "Tier ID is required")
    private UUID tierId;

    @NotBlank(message = "Billing cycle is required")
    @Pattern(regexp = "MONTHLY|ANNUAL", message = "Billing cycle must be MONTHLY or ANNUAL")
    private String billingCycle;

    private List<UUID> addonIds;
}
