-- JOB-200: paddle_customer_id moves from org_subscriptions to organisations.
-- org_subscriptions.tier_id is NOT NULL, so a row there can never exist without
-- already committing to a real (paid) tier. Every tier/add-on has a real price, so
-- that row must only ever be created once a real Paddle payment is actually
-- confirmed (via webhook) — never staged for free. But creating the Paddle
-- customer needs to happen earlier, while the org is still just picking a plan.
-- organisations is the one thing that always exists for every org regardless of
-- billing state, so that's where the customer id belongs.
ALTER TABLE organisations ADD COLUMN paddle_customer_id TEXT;
ALTER TABLE org_subscriptions DROP COLUMN paddle_customer_id;
