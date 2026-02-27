-- Project block reasons table
-- Per-project vocabulary of block reasons; built organically as jobs are blocked.
-- Jobs reference this table via FK so reason text is consistent across jobs.

CREATE TABLE project_block_reasons (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    reason     VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT uq_block_reason_per_project UNIQUE (project_id, reason)
);

CREATE INDEX idx_block_reasons_project ON project_block_reasons(project_id);

COMMENT ON TABLE project_block_reasons IS 'Per-project vocabulary of block reasons used in the UI dropdown';
COMMENT ON COLUMN project_block_reasons.deleted_at IS 'Soft delete — hidden from dropdown but existing job references are preserved';
