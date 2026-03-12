ALTER TABLE jobs
    ADD COLUMN priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM'
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

CREATE INDEX idx_jobs_priority ON jobs(project_id, priority) WHERE deleted_at IS NULL;
