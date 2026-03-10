-- Replace broad unique constraint with a partial unique index scoped to active (non-deleted) projects.
-- This allows name reuse after a project is soft-deleted.
ALTER TABLE projects DROP CONSTRAINT uk_projects_name_owner;

CREATE UNIQUE INDEX uk_projects_name_owner_active
    ON projects (name, owner_id)
    WHERE deleted_at IS NULL;
