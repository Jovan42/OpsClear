-- ADR-0021: Milestone grouping for jobs
CREATE TABLE milestones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID NOT NULL REFERENCES projects(id),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    deadline    DATE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP
);

CREATE INDEX idx_milestones_project ON milestones (project_id) WHERE deleted_at IS NULL;

ALTER TABLE jobs
    ADD COLUMN milestone_id UUID REFERENCES milestones(id) ON DELETE SET NULL;
