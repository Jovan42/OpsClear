-- Project members table (project-scoped roles)
-- Links users to projects with a role (OWNER, ADMIN, MEMBER)

CREATE TABLE project_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_project_member UNIQUE (project_id, user_id),
    CONSTRAINT chk_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX idx_project_members_project ON project_members(project_id);
CREATE INDEX idx_project_members_user ON project_members(user_id);

COMMENT ON TABLE project_members IS 'Project membership — links users to projects with a role';
COMMENT ON COLUMN project_members.role IS 'OWNER: full control; ADMIN: manage members/settings; MEMBER: work on assigned jobs';
COMMENT ON COLUMN project_members.joined_at IS 'When the user was added to the project';
