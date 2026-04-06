CREATE TABLE job_status_history (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id       UUID         NOT NULL REFERENCES jobs(id),
    changed_from VARCHAR(20),
    changed_to   VARCHAR(20)  NOT NULL,
    changed_by   UUID         REFERENCES users(id),
    changed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    block_reason VARCHAR(500)
);

CREATE INDEX idx_job_status_history_job_id ON job_status_history(job_id);
