package com.opsclear.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The single map of sign-up codes and what each one gives (ADR-0066). Codes are public campaign codes, not secrets.
 * The benefit is applied at organisation creation by the redemption job (JOB-443), which adds its accessor.
 */
public enum SignupCode {

    TRIAL(new TrialDays(30));

    private final SignupBenefit benefit;

    SignupCode(SignupBenefit benefit) {
        this.benefit = benefit;
    }

    /** Trims and upper-cases the code; empty for a missing or unknown one. */
    public static Optional<SignupCode> parse(String code) {
        return Optional.ofNullable(code)
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .flatMap(normalised -> Arrays.stream(values())
                        .filter(signupCode -> signupCode.name().equals(normalised))
                        .findFirst());
    }
}
