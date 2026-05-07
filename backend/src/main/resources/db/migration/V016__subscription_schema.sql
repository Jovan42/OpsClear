-- Subscription tiers: one row per (max_members, max_projects) combination.
-- max_projects NULL = unlimited.
-- Annual prices = round(monthly * 10 / 12).
CREATE TABLE subscription_tiers (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    max_members   INT          NOT NULL,
    max_projects  INT,
    price_monthly INT          NOT NULL,
    price_annual  INT          NOT NULL,
    currency      VARCHAR(3)   NOT NULL DEFAULT 'RSD',
    display_order INT          NOT NULL
);

-- Add-ons: keyed by a stable string (used for feature gating).
CREATE TABLE subscription_addons (
    id            UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    key           VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    price_monthly INT          NOT NULL,
    price_annual  INT          NOT NULL,
    available     BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL
);

-- One subscription record per organisation.
CREATE TABLE org_subscriptions (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    org_id        UUID        NOT NULL UNIQUE REFERENCES organisations(id),
    tier_id       UUID        NOT NULL REFERENCES subscription_tiers(id),
    billing_cycle VARCHAR(10) NOT NULL CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL')),
    is_internal   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Active add-ons per org subscription.
CREATE TABLE org_subscription_addons (
    org_subscription_id UUID NOT NULL REFERENCES org_subscriptions(id) ON DELETE CASCADE,
    addon_id            UUID NOT NULL REFERENCES subscription_addons(id),
    PRIMARY KEY (org_subscription_id, addon_id)
);

-- ─── Seed: subscription tiers ─────────────────────────────────────────────────
-- Row-major: members outer, projects inner.
-- max_projects NULL = unlimited (displayed as "Unlimited" in UI).

INSERT INTO subscription_tiers (max_members, max_projects, price_monthly, price_annual, display_order) VALUES
  -- 5 members
  ( 5,  3,  2900,  2417,  1),
  ( 5,  5,  3900,  3250,  2),
  ( 5, 10,  5400,  4500,  3),
  ( 5, 20,  7400,  6167,  4),
  ( 5, NULL,  9900,  8250,  5),
  -- 10 members
  (10,  3,  4900,  4083,  6),
  (10,  5,  5900,  4917,  7),
  (10, 10,  7400,  6167,  8),
  (10, 20,  9400,  7833,  9),
  (10, NULL, 11900,  9917, 10),
  -- 15 members
  (15,  3,  6900,  5750, 11),
  (15,  5,  7900,  6583, 12),
  (15, 10,  9400,  7833, 13),
  (15, 20, 11400,  9500, 14),
  (15, NULL, 13900, 11583, 15),
  -- 20 members
  (20,  3,  8900,  7417, 16),
  (20,  5,  9900,  8250, 17),
  (20, 10, 11400,  9500, 18),
  (20, 20, 13400, 11167, 19),
  (20, NULL, 15900, 13250, 20),
  -- 30 members
  (30,  3, 12900, 10750, 21),
  (30,  5, 13900, 11583, 22),
  (30, 10, 15400, 12833, 23),
  (30, 20, 17400, 14500, 24),
  (30, NULL, 19900, 16583, 25),
  -- 40 members
  (40,  3, 16900, 14083, 26),
  (40,  5, 17900, 14917, 27),
  (40, 10, 19400, 16167, 28),
  (40, 20, 21400, 17833, 29),
  (40, NULL, 23900, 19917, 30),
  -- 50 members
  (50,  3, 20900, 17417, 31),
  (50,  5, 21900, 18250, 32),
  (50, 10, 23400, 19500, 33),
  (50, 20, 25400, 21167, 34),
  (50, NULL, 27900, 23250, 35);

-- ─── Seed: add-ons ───────────────────────────────────────────────────────────
-- available = FALSE for coming-soon items (not purchasable yet).

INSERT INTO subscription_addons (key, name, price_monthly, price_annual, available, display_order) VALUES
  ('DASHBOARD',            'Dashboard',            990,  825,  TRUE,  1),
  ('APPROVALS',            'Approvals',           1490, 1242,  TRUE,  2),
  ('NOTES',                'Notes',                990,  825,  TRUE,  3),
  ('JOB_STATUS_HISTORY',   'Job status history',   990,  825,  TRUE,  4),
  ('MILESTONES',           'Milestones',          1490, 1242,  TRUE,  5),
  ('JOB_RELATIONSHIPS',    'Job relationships',   1490, 1242,  TRUE,  6),
  ('API_KEYS',             'API keys',            1990, 1658,  TRUE,  7),
  ('JOB_TEMPLATES',        'Job templates',        990,  825, FALSE,  8),
  ('RECURRING_SCHEDULING', 'Recurring scheduling',1490, 1242, FALSE,  9);
