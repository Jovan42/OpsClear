-- ADR-0044: Paddle identity + subscription status on the org subscription record.
-- All nullable — internal orgs (is_internal = TRUE) never get a Paddle identity at
-- all, and an org that hasn't completed the Paddle checkout flow yet (JOB-173) has
-- none of these set either.

ALTER TABLE org_subscriptions
    ADD COLUMN paddle_customer_id     TEXT,
    ADD COLUMN paddle_subscription_id TEXT,
    ADD COLUMN subscription_status    VARCHAR(10)
        CHECK (subscription_status IN ('ACTIVE', 'PAST_DUE', 'CANCELED'));

COMMENT ON COLUMN org_subscriptions.subscription_status IS
    'Synced from Paddle webhook events (JOB-174), never computed locally. Null until the org has a Paddle subscription.';
