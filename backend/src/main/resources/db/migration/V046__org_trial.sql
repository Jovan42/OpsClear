-- JOB-444 (ADR-0067): per-org free trial columns.
-- trial_ends_at NULL = never had a trial (internal and paid orgs).
ALTER TABLE organisations
    ADD COLUMN trial_ends_at              TIMESTAMPTZ,
    ADD COLUMN trial_ending_email_sent_at TIMESTAMPTZ,
    ADD COLUMN trial_ended_email_sent_at  TIMESTAMPTZ;

-- Backfill mirrors OrgSubscriptionRepository.isInternal / hasRealBilling.
UPDATE organisations o
SET trial_ends_at = now() + interval '30 days'
WHERE o.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM org_subscriptions s
      WHERE s.org_id = o.id
        AND (s.is_internal
             OR (s.paddle_subscription_id IS NOT NULL
                 AND s.subscription_status IS NOT NULL
                 AND s.subscription_status <> 'CANCELED')));

CREATE INDEX idx_organisations_trial_ends_at ON organisations (trial_ends_at)
    WHERE trial_ends_at IS NOT NULL;
