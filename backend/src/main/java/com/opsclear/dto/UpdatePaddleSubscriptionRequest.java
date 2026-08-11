package com.opsclear.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePaddleSubscriptionRequest {

    @NotNull(message = "Tier is required")
    private UUID tierId;

    private Set<UUID> addonIds;
}
