-- ADR-0052: gated completion requiring approval

-- Project gate
ALTER TABLE projects
    ADD COLUMN require_completion_approval BOOLEAN NOT NULL DEFAULT false;

-- Approval kind and completion outcome
ALTER TABLE approvals
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN completion_applied BOOLEAN,
    ADD COLUMN completion_not_applied_reason VARCHAR(50),
    ADD CONSTRAINT chk_approval_kind CHECK (kind IN ('MANUAL', 'COMPLETION'));

-- Allow CANCELLED status
ALTER TABLE approvals DROP CONSTRAINT chk_approval_status;
ALTER TABLE approvals ADD CONSTRAINT chk_approval_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

-- At most one pending completion approval per job
CREATE UNIQUE INDEX uq_approvals_one_pending_completion
    ON approvals(job_id) WHERE kind = 'COMPLETION' AND status = 'PENDING';
