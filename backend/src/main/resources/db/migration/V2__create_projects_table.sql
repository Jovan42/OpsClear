-- Projects table (top-level organizational unit)
-- All entities (jobs, notes, approvals) belong to a project

CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT uk_projects_name_owner UNIQUE (name, owner_id)
);

CREATE INDEX idx_projects_owner ON projects(owner_id);
CREATE INDEX idx_projects_deleted ON projects(deleted_at) WHERE deleted_at IS NULL;

COMMENT ON TABLE projects IS 'Projects - top-level tenant boundary for organizing work';
COMMENT ON COLUMN projects.owner_id IS 'User who created the project';
COMMENT ON COLUMN projects.deleted_at IS 'Soft delete timestamp (NULL = active)';
