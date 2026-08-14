
-- ADR-0044 / JOB-197: track Paddle's scheduled_change so we can (a) prevent a
-- second cancel attempt on a subscription that already has one scheduled, and
-- (b) show the real cancellation date instead of generic wording. Null unless a
-- cancellation is currently scheduled — synced from Paddle webhook events
-- (JOB-174), never computed locally, same as subscription_status.

ALTER TABLE org_subscriptions
    ADD COLUMN paddle_scheduled_cancellation_at TIMESTAMPTZ;

COMMENT ON COLUMN org_subscriptions.paddle_scheduled_cancellation_at IS
    'From Paddle''s scheduled_change.effective_at when scheduled_change.action = cancel. Null when no cancellation is scheduled.';
