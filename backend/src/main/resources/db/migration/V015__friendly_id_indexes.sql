-- Indexes to support efficient friendly ID resolution in API routing.
-- Queries filter by UPPER(friendly_id) and scope to the caller's org.

-- Projects: org_id + friendly_id together — direct column on the table
CREATE INDEX idx_projects_friendly_id
    ON projects (organisation_id, UPPER(friendly_id))
    WHERE deleted_at IS NULL;

-- Jobs: friendly_id lookup only; org scoping comes via JOIN projects (project_id is indexed as FK)
CREATE INDEX idx_jobs_friendly_id
    ON jobs (UPPER(friendly_id))
    WHERE deleted_at IS NULL;

-- Milestones: same pattern as jobs
CREATE INDEX idx_milestones_friendly_id
    ON milestones (UPPER(friendly_id))
    WHERE deleted_at IS NULL;
