-- Links are hard-deleted (no deleted_at), consistent with notes / job_relationships /
-- schedule_assignees — child rows tied 1:1 to a parent, cascade removes them for free.
CREATE TABLE job_links (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID        NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    url        TEXT        NOT NULL,
    label      VARCHAR(100),
    created_by UUID        NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX job_links_job_id_idx ON job_links (job_id);

CREATE TABLE project_links (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    url        TEXT        NOT NULL,
    label      VARCHAR(100),
    created_by UUID        NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX project_links_project_id_idx ON project_links (project_id);

COMMENT ON TABLE job_links     IS 'External resource links attached to a job (ADR-0035)';
COMMENT ON TABLE project_links IS 'External resource links attached to a project (ADR-0035)';

-- Coming-soon: available flipped to TRUE in a follow-up migration once ready to sell.
INSERT INTO subscription_addons (key, name, price_monthly, price_annual, available, display_order) VALUES
  ('JOB_LINKS', 'Job links', 490, 408, FALSE, 10);
