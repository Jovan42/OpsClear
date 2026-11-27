package com.opsclear.model;

/** A trial of {@code totalDays} days in total, counted from organisation creation. */
public record TrialDays(int totalDays) implements SignupBenefit {
}
