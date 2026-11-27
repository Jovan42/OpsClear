package com.opsclear.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * The email sign-up lists the backend accepts (ADR-0066). The database stores the wire value as free text, so a new
 * campaign or newsletter is a new constant here and no migration.
 */
public enum SignupList {

    EARLY_ACCESS("early-access");

    private final String value;

    SignupList(String value) {
        this.value = value;
    }

    /** The value used in the API and stored in {@code email_signups.list}. */
    public String getValue() {
        return value;
    }

    /** The list with this exact wire value, or empty for an unknown or missing one. */
    public static Optional<SignupList> fromValue(String value) {
        return Arrays.stream(values())
                .filter(list -> list.value.equals(value))
                .findFirst();
    }
}
