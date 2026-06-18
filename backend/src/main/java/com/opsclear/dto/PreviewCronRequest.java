package com.opsclear.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PreviewCronRequest {

    @NotBlank(message = "Cron expression is required")
    private String cronExpression;

    @NotBlank(message = "Timezone is required")
    private String timezone;
}
