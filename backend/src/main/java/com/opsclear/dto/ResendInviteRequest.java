package com.opsclear.dto;

import jakarta.validation.constraints.Pattern;
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
public class ResendInviteRequest {

    // Language of the invite email: the inviter's UI language (ADR-0059). Optional, defaults to en.
    @Pattern(regexp = "en|sr", message = "Language must be en or sr")
    private String language;
}
