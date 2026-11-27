package com.opsclear.model;

/**
 * What a sign-up code gives when it's redeemed at organisation creation (ADR-0066). A new benefit type is a new record
 * here plus its case in the redemption switch.
 */
public sealed interface SignupBenefit permits TrialDays {
}
