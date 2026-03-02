-- =============================================================================
-- V001__init_mvp.sql
-- Full schema for OpsClear MVP (squashed from V1–V8)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Users (synced from Keycloak on login; ID = Keycloak sub claim)
-- -----------------------------------------------------------------------------

CREATE TABLE users (
    id            UUID          PRIMARY KEY,
    email         VARCHAR(255)  NOT NULL,
    name          VARCHAR(255)  NOT NULL,
    avatar_url    VARCHAR(500),
    timezone      VARCHAR(50)   NOT NULL DEFAULT 'UTC',
    preferences   JSONB         NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,

    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_email ON users(email);

COMMENT ON TABLE  users                IS 'Users synced from Keycloak on login';
COMMENT ON COLUMN users.id             IS 'Same as Keycloak user ID (sub claim)';
COMMENT ON COLUMN users.preferences    IS 'User preferences as JSON (UI settings, etc.)';

-- -----------------------------------------------------------------------------
-- Projects (top-level tenant boundary)
-- -----------------------------------------------------------------------------

CREATE TABLE projects (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    owner_id    UUID          NOT NULL REFERENCES users(id),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at  TIMESTAMP,

    CONSTRAINT uk_projects_name_owner UNIQUE (name, owner_id)
);

CREATE INDEX idx_projects_owner   ON projects(owner_id);
CREATE INDEX idx_projects_deleted ON projects(deleted_at) WHERE deleted_at IS NULL;

COMMENT ON TABLE  projects            IS 'Projects - top-level tenant boundary for organizing work';
COMMENT ON COLUMN projects.owner_id   IS 'User who created the project';
COMMENT ON COLUMN projects.deleted_at IS 'Soft delete timestamp (NULL = active)';

-- -----------------------------------------------------------------------------
-- Project members (project-scoped roles)
-- -----------------------------------------------------------------------------

CREATE TABLE project_members (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID      NOT NULL REFERENCES projects(id),
    user_id    UUID      NOT NULL REFERENCES users(id),
    role       VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_project_member UNIQUE (project_id, user_id),
    CONSTRAINT chk_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'))
);

CREATE INDEX idx_project_members_project ON project_members(project_id);
CREATE INDEX idx_project_members_user    ON project_members(user_id);

COMMENT ON TABLE  project_members           IS 'Project membership — links users to projects with a role';
COMMENT ON COLUMN project_members.role      IS 'OWNER: full control; ADMIN: manage members/settings; MEMBER: work on assigned jobs';
COMMENT ON COLUMN project_members.joined_at IS 'When the user was added to the project';

-- -----------------------------------------------------------------------------
-- Project block reasons (per-project vocabulary; built organically)
-- -----------------------------------------------------------------------------

CREATE TABLE project_block_reasons (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID         NOT NULL REFERENCES projects(id),
    reason     VARCHAR(500) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT uq_block_reason_per_project UNIQUE (project_id, reason)
);

CREATE INDEX idx_block_reasons_project ON project_block_reasons(project_id);

COMMENT ON TABLE  project_block_reasons            IS 'Per-project vocabulary of block reasons used in the UI dropdown';
COMMENT ON COLUMN project_block_reasons.deleted_at IS 'Soft delete — hidden from dropdown but existing job references are preserved';

-- -----------------------------------------------------------------------------
-- Jobs (central entity — 4-status lifecycle)
-- -----------------------------------------------------------------------------

CREATE TABLE jobs (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       UUID         NOT NULL REFERENCES projects(id),
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    client           VARCHAR(255),
    assigned_to      UUID         REFERENCES users(id),
    deadline         TIMESTAMP,
    status           VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    created_by       UUID         NOT NULL REFERENCES users(id),
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMP,
    blocked_by       UUID         REFERENCES users(id),
    blocked_reason_id UUID        REFERENCES project_block_reasons(id),
    blocked_at       TIMESTAMP,

    CONSTRAINT chk_job_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'BLOCKED', 'COMPLETED'))
);

CREATE INDEX idx_jobs_project  ON jobs(project_id);
CREATE INDEX idx_jobs_assigned ON jobs(assigned_to);
CREATE INDEX idx_jobs_status   ON jobs(project_id, status) WHERE deleted_at IS NULL;

COMMENT ON TABLE  jobs                    IS 'Jobs - central unit of work within a project';
COMMENT ON COLUMN jobs.status             IS 'NEW: not started; IN_PROGRESS: active; BLOCKED: waiting; COMPLETED: done';
COMMENT ON COLUMN jobs.assigned_to        IS 'Responsible person (nullable = unassigned)';
COMMENT ON COLUMN jobs.client             IS 'Optional — who the job is for';
COMMENT ON COLUMN jobs.deleted_at         IS 'Soft delete timestamp (NULL = active)';
COMMENT ON COLUMN jobs.blocked_by         IS 'User who set the block (OWNER/ADMIN or assigned MEMBER)';
COMMENT ON COLUMN jobs.blocked_reason_id  IS 'FK to project_block_reasons — NULL when not blocked';
COMMENT ON COLUMN jobs.blocked_at         IS 'When the block was set — NULL when not blocked';

-- -----------------------------------------------------------------------------
-- Notes (immutable audit trail attached to jobs)
-- -----------------------------------------------------------------------------

CREATE TABLE notes (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id     UUID          NOT NULL REFERENCES jobs(id),
    author_id  UUID          NOT NULL REFERENCES users(id),
    content    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notes_job_id ON notes(job_id);

COMMENT ON TABLE  notes            IS 'Immutable audit notes attached to jobs — no update or delete paths exist';
COMMENT ON COLUMN notes.created_at IS 'Set by the database default; never passed by the application';

-- -----------------------------------------------------------------------------
-- Approvals (request and decision workflow)
-- -----------------------------------------------------------------------------

CREATE TABLE approvals (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id       UUID         NOT NULL REFERENCES jobs(id),
    requester_id UUID         NOT NULL REFERENCES users(id),
    approver_id  UUID         REFERENCES users(id),
    description  VARCHAR(500) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    comment      VARCHAR(1000),
    requested_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at   TIMESTAMP,

    CONSTRAINT chk_approval_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_approvals_job_id ON approvals(job_id);
CREATE INDEX idx_approvals_status ON approvals(status) WHERE status = 'PENDING';
