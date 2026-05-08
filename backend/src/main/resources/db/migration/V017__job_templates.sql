-- Job templates: reusable blueprints that pre-fill job fields.
-- Wildcard resolution ({{variable}}) happens client-side at creation time.
CREATE TABLE job_templates (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    friendly_id          VARCHAR(20)  NOT NULL,
    project_id           UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name                 VARCHAR(100) NOT NULL,
    title                VARCHAR(255),
    description          TEXT,
    client               VARCHAR(255),
    priority             VARCHAR(20),
    assignee_mode        VARCHAR(20)  NOT NULL DEFAULT 'NONE',
    assignee_id          UUID         REFERENCES users(id) ON DELETE SET NULL,
    milestone_id         UUID         REFERENCES milestones(id) ON DELETE SET NULL,
    deadline_offset_days INT,
    occurrence_count     INT          NOT NULL DEFAULT 0,
    created_by           UUID         NOT NULL REFERENCES users(id),
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at           TIMESTAMP,

    CONSTRAINT chk_template_assignee_mode CHECK (assignee_mode IN ('NONE', 'FIXED', 'ASK')),
    CONSTRAINT chk_template_priority      CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE UNIQUE INDEX job_templates_friendly_id_idx
    ON job_templates (UPPER(friendly_id))
    WHERE deleted_at IS NULL;

CREATE INDEX job_templates_project_id_idx
    ON job_templates (project_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE  job_templates                      IS 'Reusable job blueprints — pre-fill fields and support {{wildcard}} placeholders';
COMMENT ON COLUMN job_templates.assignee_mode        IS 'NONE: leave blank; FIXED: pre-fill assignee_id; ASK: leave blank and focus field';
COMMENT ON COLUMN job_templates.deadline_offset_days IS 'Deadline = creation date + N days; NULL = no deadline pre-fill';
COMMENT ON COLUMN job_templates.occurrence_count     IS 'Incremented atomically each time a job is created from this template';
COMMENT ON COLUMN job_templates.deleted_at           IS 'Soft delete timestamp (NULL = active)';

-- Add template_prefix to org_settings so orgs can customise the TPL- prefix.
ALTER TABLE org_settings
    ADD COLUMN template_prefix VARCHAR(3) NOT NULL DEFAULT 'TPL';

-- Enable JOB_TEMPLATES addon (was seeded as available = FALSE in V016).
UPDATE subscription_addons
SET available = TRUE
WHERE key = 'JOB_TEMPLATES';
