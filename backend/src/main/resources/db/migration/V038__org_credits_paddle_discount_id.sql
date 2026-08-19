ALTER TABLE org_credits ADD COLUMN paddle_discount_id TEXT;

CREATE INDEX idx_org_credits_paddle_discount_id ON org_credits (paddle_discount_id)
    WHERE paddle_discount_id IS NOT NULL;

COMMENT ON COLUMN org_credits.paddle_discount_id IS
    'Set on a grant row once its one-time Paddle discount is created (JOB-180). When '
    'PaddleWebhookService detects that discount was actually consumed by a transaction, '
    'it inserts a matching negative-amount row with the same paddle_discount_id — the '
    'existence of that debit row is what makes the detach idempotent on webhook redelivery.';
