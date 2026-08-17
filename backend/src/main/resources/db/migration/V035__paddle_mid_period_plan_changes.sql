-- ADR-0044 / JOB-198: mid-period upgrade/downgrade policy. Upgrades apply
-- immediately (existing behavior); downgrades are deferred to the next
-- renewal so the org keeps what it already paid for until the period ends.
-- pending_tier_id/org_subscription_pending_addons hold the desired plan
-- until the webhook confirms the period actually rolled over.
-- paddle_current_period_starts_at tracks the last-known period boundary so
-- a rollover can be detected (a new starts_at later than the stored one).

ALTER TABLE org_subscriptions
    ADD COLUMN pending_tier_id UUID REFERENCES subscription_tiers(id),
    ADD COLUMN paddle_current_period_starts_at TIMESTAMPTZ;

CREATE TABLE org_subscription_pending_addons (
    org_subscription_id UUID NOT NULL REFERENCES org_subscriptions(id) ON DELETE CASCADE,
    addon_id            UUID NOT NULL REFERENCES subscription_addons(id),
    PRIMARY KEY (org_subscription_id, addon_id)
);
