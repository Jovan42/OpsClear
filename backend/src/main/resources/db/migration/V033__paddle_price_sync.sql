-- JOB-176: wire super-admin tier/addon pricing to Paddle Prices (archive-and-recreate
-- on every price change; a Product is created once per tier/addon and reused).
--
-- Paddle's Prices API does not support RSD as a unit_price.currency_code (verified
-- against developer.paddle.com — its supported list is ~50 currencies, no RSD), so
-- the reference currency for anything priced through Paddle has to change to one
-- Paddle actually accepts. EUR was chosen; existing RSD amounts below are converted
-- at an approximate long-run rate (~117.5 RSD/EUR) and rounded to a charm-price
-- ladder (ending in 4 or 9), preserving the original table's relative structure and
-- its "2 months free" annual/monthly ratio. These are a computed starting point, not
-- a live FX quote — they need a business sanity-check before go-live.

ALTER TABLE subscription_tiers
    ALTER COLUMN currency SET DEFAULT 'EUR',
    ADD COLUMN paddle_product_id      TEXT,
    ADD COLUMN paddle_price_id_monthly TEXT,
    ADD COLUMN paddle_price_id_annual  TEXT;

ALTER TABLE subscription_addons
    ADD COLUMN paddle_product_id      TEXT,
    ADD COLUMN paddle_price_id_monthly TEXT,
    ADD COLUMN paddle_price_id_annual  TEXT;

UPDATE subscription_tiers SET currency = 'EUR';

-- Tiers: matched by (max_members, max_projects) — display_order 1-35 in V016.
UPDATE subscription_tiers SET price_monthly = 24,  price_annual = 20  WHERE max_members = 5  AND max_projects = 3;
UPDATE subscription_tiers SET price_monthly = 34,  price_annual = 28  WHERE max_members = 5  AND max_projects = 5;
UPDATE subscription_tiers SET price_monthly = 44,  price_annual = 37  WHERE max_members = 5  AND max_projects = 10;
UPDATE subscription_tiers SET price_monthly = 64,  price_annual = 53  WHERE max_members = 5  AND max_projects = 20;
UPDATE subscription_tiers SET price_monthly = 84,  price_annual = 70  WHERE max_members = 5  AND max_projects IS NULL;
UPDATE subscription_tiers SET price_monthly = 44,  price_annual = 37  WHERE max_members = 10 AND max_projects = 3;
UPDATE subscription_tiers SET price_monthly = 49,  price_annual = 41  WHERE max_members = 10 AND max_projects = 5;
UPDATE subscription_tiers SET price_monthly = 64,  price_annual = 53  WHERE max_members = 10 AND max_projects = 10;
UPDATE subscription_tiers SET price_monthly = 79,  price_annual = 66  WHERE max_members = 10 AND max_projects = 20;
UPDATE subscription_tiers SET price_monthly = 99,  price_annual = 82  WHERE max_members = 10 AND max_projects IS NULL;
UPDATE subscription_tiers SET price_monthly = 59,  price_annual = 49  WHERE max_members = 15 AND max_projects = 3;
UPDATE subscription_tiers SET price_monthly = 69,  price_annual = 58  WHERE max_members = 15 AND max_projects = 5;
UPDATE subscription_tiers SET price_monthly = 79,  price_annual = 66  WHERE max_members = 15 AND max_projects = 10;
UPDATE subscription_tiers SET price_monthly = 99,  price_annual = 82  WHERE max_members = 15 AND max_projects = 20;
UPDATE subscription_tiers SET price_monthly = 119, price_annual = 99  WHERE max_members = 15 AND max_projects IS NULL;
UPDATE subscription_tiers SET price_monthly = 74,  price_annual = 62  WHERE max_members = 20 AND max_projects = 3;
UPDATE subscription_tiers SET price_monthly = 84,  price_annual = 70  WHERE max_members = 20 AND max_projects = 5;
UPDATE subscription_tiers SET price_monthly = 99,  price_annual = 82  WHERE max_members = 20 AND max_projects = 10;
UPDATE subscription_tiers SET price_monthly = 114, price_annual = 95  WHERE max_members = 20 AND max_projects = 20;
UPDATE subscription_tiers SET price_monthly = 134, price_annual = 112 WHERE max_members = 20 AND max_projects IS NULL;
UPDATE subscription_tiers SET price_monthly = 109, price_annual = 91  WHERE max_members = 30 AND max_projects = 3;
UPDATE subscription_tiers SET price_monthly = 119, price_annual = 99  WHERE max_members = 30 AND max_projects = 5;
UPDATE subscription_tiers SET price_monthly = 129, price_annual = 108 WHERE max_members = 30 AND max_projects = 10;
UPDATE subscription_tiers SET price_monthly = 149, price_annual = 124 WHERE max_members = 30 AND max_projects = 20;
UPDATE subscription_tiers SET price_monthly = 169, price_annual = 141 WHERE max_members = 30 AND max_projects IS NULL;
UPDATE subscription_tiers SET price_monthly = 144, price_annual = 120 WHERE max_members = 40 AND max_projects = 3;
UPDATE subscription_tiers SET price_monthly = 154, price_annual = 128 WHERE max_members = 40 AND max_projects = 5;
UPDATE subscription_tiers SET price_monthly = 164, price_annual = 137 WHERE max_members = 40 AND max_projects = 10;
UPDATE subscription_tiers SET price_monthly = 184, price_annual = 153 WHERE max_members = 40 AND max_projects = 20;
UPDATE subscription_tiers SET price_monthly = 204, price_annual = 170 WHERE max_members = 40 AND max_projects IS NULL;
UPDATE subscription_tiers SET price_monthly = 179, price_annual = 149 WHERE max_members = 50 AND max_projects = 3;
UPDATE subscription_tiers SET price_monthly = 184, price_annual = 153 WHERE max_members = 50 AND max_projects = 5;
UPDATE subscription_tiers SET price_monthly = 199, price_annual = 166 WHERE max_members = 50 AND max_projects = 10;
UPDATE subscription_tiers SET price_monthly = 214, price_annual = 178 WHERE max_members = 50 AND max_projects = 20;
UPDATE subscription_tiers SET price_monthly = 239, price_annual = 199 WHERE max_members = 50 AND max_projects IS NULL;

-- Add-ons: matched by key.
UPDATE subscription_addons SET price_monthly = 9,  price_annual = 8  WHERE key = 'DASHBOARD';
UPDATE subscription_addons SET price_monthly = 14, price_annual = 12 WHERE key = 'APPROVALS';
UPDATE subscription_addons SET price_monthly = 9,  price_annual = 8  WHERE key = 'NOTES';
UPDATE subscription_addons SET price_monthly = 9,  price_annual = 8  WHERE key = 'JOB_STATUS_HISTORY';
UPDATE subscription_addons SET price_monthly = 14, price_annual = 12 WHERE key = 'MILESTONES';
UPDATE subscription_addons SET price_monthly = 14, price_annual = 12 WHERE key = 'JOB_RELATIONSHIPS';
UPDATE subscription_addons SET price_monthly = 19, price_annual = 16 WHERE key = 'API_KEYS';
UPDATE subscription_addons SET price_monthly = 9,  price_annual = 8  WHERE key = 'JOB_TEMPLATES';
UPDATE subscription_addons SET price_monthly = 14, price_annual = 12 WHERE key = 'RECURRING_SCHEDULING';
UPDATE subscription_addons SET price_monthly = 4,  price_annual = 3  WHERE key = 'JOB_LINKS';
UPDATE subscription_addons SET price_monthly = 14, price_annual = 12 WHERE key = 'JOB_TYPES';
