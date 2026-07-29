-- Super-user flag for the restricted admin console (ADR-0043).
-- Distinct from org OWNER — crosses org boundaries entirely, narrowly held.
ALTER TABLE users
    ADD COLUMN super_user BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.super_user IS 'Gates the super-admin console (pricing config, feedback/credits) — not an org role, crosses org boundaries';
