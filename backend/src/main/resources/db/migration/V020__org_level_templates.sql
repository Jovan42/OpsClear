-- project_id becomes nullable to allow org-scoped templates (project_id = NULL, org_id set)
ALTER TABLE job_templates ALTER COLUMN project_id DROP NOT NULL;

ALTER TABLE job_templates
    ADD COLUMN org_id UUID REFERENCES organisations(id) ON DELETE CASCADE;

-- exactly one of project_id / org_id must be set
ALTER TABLE job_templates
    ADD CONSTRAINT job_templates_scope_check
    CHECK ((project_id IS NULL) <> (org_id IS NULL));

CREATE INDEX job_templates_org_id_idx
    ON job_templates (org_id)
    WHERE deleted_at IS NULL;
