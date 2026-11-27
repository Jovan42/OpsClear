package com.opsclear.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

/** Public email sign-up (ADR-0066). {@code website} is the honeypot: real visitors never fill it. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailSignupRequest {

    @NotBlank(message = "List is required")
    private String list;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    private String email;

    // @AssertTrue alone accepts null, so a missing consent needs @NotNull too
    @NotNull(message = "Consent is required")
    @AssertTrue(message = "Consent is required")
    private Boolean consent;

    @Pattern(regexp = "en|sr", message = "Locale must be en or sr")
    private String locale;

    @Size(max = 100, message = "utmSource must not exceed 100 characters")
    private String utmSource;

    @Size(max = 100, message = "utmMedium must not exceed 100 characters")
    private String utmMedium;

    @Size(max = 100, message = "utmCampaign must not exceed 100 characters")
    private String utmCampaign;

    @Size(max = 30, message = "Code must not exceed 30 characters")
    private String code;

    private String website;

    /**
     * Trimmed while binding, before {@code @Email} runs: it rejects surrounding spaces, which phone keyboards and
     * autofill often add. The case is kept as typed.
     */
    public void setEmail(String email) {
        this.email = StringUtils.trimWhitespace(email);
    }
}
