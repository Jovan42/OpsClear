package com.opsclear.model;

/**
 * What an organisation can do right now (ADR-0067), derived by {@code OrgAccessService} and evaluated in this order.
 */
public enum OrgAccessState {
    /** {@code org_subscriptions.is_internal}: every add-on, no limits, never locked. */
    INTERNAL,
    /** Real Paddle billing ({@code hasRealBilling}), {@code PAST_DUE} included: the tier's limits and add-ons. */
    PAID,
    /** {@code trial_ends_at} in the future: every add-on, the trial limits. */
    TRIAL,
    /** Anything else: the trial is over, a cancelled subscription has run out, or the org never had a trial. */
    LOCKED
}
