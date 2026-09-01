-- job_templates_friendly_id_idx (V017) was mistakenly a globally UNIQUE index.
-- Every other friendly-ID-bearing table (jobs, projects, milestones) uses the
-- identical-looking index WITHOUT the UNIQUE keyword, because friendly IDs are
-- generated per-org (FriendlyIdService.nextFriendlyId resets its counter per org,
-- and the prefix itself is per-org-configurable) and are only ever looked up
-- scoped to the caller's own org — never meant to be globally unique across orgs.
-- Two different orgs' first template both compute "TPL-001", and the global
-- uniqueness constraint rejected the second one with a raw 500.
DROP INDEX job_templates_friendly_id_idx;

CREATE INDEX job_templates_friendly_id_idx
    ON job_templates (UPPER(friendly_id))
    WHERE deleted_at IS NULL;
