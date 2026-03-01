CREATE TABLE approvals (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id       UUID         NOT NULL REFERENCES jobs(id),
    requester_id UUID         NOT NULL REFERENCES users(id),
    approver_id  UUID         REFERENCES users(id),
    description  VARCHAR(500) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    comment      VARCHAR(1000),
    requested_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at   TIMESTAMP,

    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_approvals_job_id ON approvals(job_id);
CREATE INDEX idx_approvals_status ON approvals(status) WHERE status = 'PENDING';
