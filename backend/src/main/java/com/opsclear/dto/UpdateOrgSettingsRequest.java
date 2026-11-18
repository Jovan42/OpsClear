package com.opsclear.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * PATCH body for the org's friendly-ID prefixes (ADR-0060). Every field is optional; an omitted
 * field keeps its current value. Distinctness and the history check live in the service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOrgSettingsRequest {

    private static final String PREFIX_REGEX = "^\\s*[A-Za-z]{2,3}\\s*$";
    private static final String PREFIX_MESSAGE = "Prefix must be 2–3 letters A–Z";

    @Pattern(regexp = PREFIX_REGEX, message = PREFIX_MESSAGE)
    private String projectPrefix;

    @Pattern(regexp = PREFIX_REGEX, message = PREFIX_MESSAGE)
    private String jobPrefix;

    @Pattern(regexp = PREFIX_REGEX, message = PREFIX_MESSAGE)
    private String milestonePrefix;

    @Pattern(regexp = PREFIX_REGEX, message = PREFIX_MESSAGE)
    private String templatePrefix;
}
