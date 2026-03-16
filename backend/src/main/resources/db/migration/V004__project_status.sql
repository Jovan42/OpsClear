-- ADR-0021: Add project lifecycle status (ACTIVE / COMPLETED)
ALTER TABLE projects
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'COMPLETED'));

CREATE INDEX idx_projects_status ON projects (status) WHERE deleted_at IS NULL;
