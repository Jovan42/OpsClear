package com.opsclear.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateAddonPriceRequest {

    @NotNull(message = "priceMonthly is required")
    @PositiveOrZero(message = "priceMonthly must not be negative")
    private Integer priceMonthly;

    @NotNull(message = "priceAnnual is required")
    @PositiveOrZero(message = "priceAnnual must not be negative")
    private Integer priceAnnual;
}
