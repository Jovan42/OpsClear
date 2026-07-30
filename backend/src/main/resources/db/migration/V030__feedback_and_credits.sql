-- ADR-0043: Super admin console — customer feedback submissions + credit ledger

CREATE TABLE feedback_submissions (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID         NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    submitted_by UUID         NOT NULL REFERENCES users(id),
    type         VARCHAR(10)  NOT NULL CHECK (type IN ('BUG', 'FEATURE', 'OTHER')),
    title        VARCHAR(255) NOT NULL,
    description  TEXT         NOT NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'REVIEWED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_feedback_submissions_org ON feedback_submissions (org_id);
CREATE INDEX idx_feedback_submissions_submitted_by ON feedback_submissions (submitted_by);
CREATE INDEX idx_feedback_submissions_status ON feedback_submissions (status) WHERE status = 'PENDING';

COMMENT ON TABLE feedback_submissions IS
    'Customer bug/feature/other feedback (ADR-0043) — reviewed by super admins, optionally rewarded with a credit.';

-- Append-only per-org credit ledger — no update/delete path, ever. Balance is the
-- ledger sum computed at read time, not a mutable column (ADR-0043).
CREATE TABLE org_credits (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID        NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    amount        INT         NOT NULL,
    reason        TEXT        NOT NULL,
    submission_id UUID        REFERENCES feedback_submissions(id),
    granted_by    UUID        NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_org_credits_org ON org_credits (org_id);

COMMENT ON TABLE org_credits IS
    'Append-only per-org credit ledger (ADR-0043) — balance is the sum of all rows, never a mutable column.';
