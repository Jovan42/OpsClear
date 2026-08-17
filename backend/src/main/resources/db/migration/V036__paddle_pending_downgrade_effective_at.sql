-- JOB-198: the date a scheduled downgrade actually takes effect — sourced from
-- Paddle's real updateSubscriptionItems response (next_billed_at) at the moment the
-- downgrade is scheduled, not computed locally. Null unless pending_tier_id is set.

ALTER TABLE org_subscriptions
    ADD COLUMN paddle_pending_downgrade_effective_at TIMESTAMPTZ;
