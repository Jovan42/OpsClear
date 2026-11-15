-- ADR-0053: per-project job-detail section visibility
ALTER TABLE projects
    ADD COLUMN hidden_job_sections VARCHAR(30)[] NOT NULL DEFAULT '{}',
    ADD CONSTRAINT chk_projects_hidden_job_sections CHECK (
        hidden_job_sections <@ ARRAY['NOTES','APPROVALS','RELATIONSHIPS','LINKS','STATUS_HISTORY']::VARCHAR(30)[]
    );
