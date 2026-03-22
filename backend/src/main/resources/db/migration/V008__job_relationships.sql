CREATE TABLE job_relationships (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    target_job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL CHECK (type IN ('BLOCKED_BY', 'RELATED_TO', 'DUPLICATES')),
    created_by    UUID NOT NULL REFERENCES users(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT no_self_reference CHECK (source_job_id != target_job_id),
    CONSTRAINT unique_relationship UNIQUE (source_job_id, target_job_id, type)
);
