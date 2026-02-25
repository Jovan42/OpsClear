-- Jobs table (central entity)
-- A Job is a unit of work within a Project, with a 4-status lifecycle

CREATE TABLE jobs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID NOT NULL REFERENCES projects(id),
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    client       VARCHAR(255),
    assigned_to  UUID REFERENCES users(id),
    deadline     TIMESTAMP,
    status       VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP,

    CONSTRAINT chk_job_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'))
);

CREATE INDEX idx_jobs_project  ON jobs(project_id);
CREATE INDEX idx_jobs_assigned ON jobs(assigned_to);
CREATE INDEX idx_jobs_status   ON jobs(project_id, status) WHERE deleted_at IS NULL;

COMMENT ON TABLE jobs IS 'Jobs - central unit of work within a project';
COMMENT ON COLUMN jobs.status IS 'NEW: not started; IN_PROGRESS: active; BLOCKED: waiting; COMPLETED: done';
COMMENT ON COLUMN jobs.assigned_to IS 'Responsible person (nullable = unassigned)';
COMMENT ON COLUMN jobs.client IS 'Optional — who the job is for';
COMMENT ON COLUMN jobs.deleted_at IS 'Soft delete timestamp (NULL = active)';
