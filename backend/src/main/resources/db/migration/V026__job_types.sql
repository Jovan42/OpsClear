-- ADR-0042: Job types — per-project classification with filtering and dashboard stats

-- Fixed, curated swatch set for type color — enforced at the schema level so an
-- eleventh option (or a raw hex string) can't slip in via a direct API call, only
-- through the deliberate app-level UI. Adding a new color later requires a proper
-- migration (ALTER TYPE ... ADD VALUE), an accepted tradeoff for that guarantee.
CREATE TYPE job_type_color AS ENUM (
    'RED', 'ORANGE', 'AMBER', 'GREEN', 'TEAL', 'BLUE', 'INDIGO', 'PURPLE', 'PINK', 'GRAY'
);

CREATE TABLE job_types (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID           NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name          VARCHAR(100)   NOT NULL,
    color         job_type_color NOT NULL,
    display_order INT            NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_types_project ON job_types (project_id);

COMMENT ON TABLE job_types IS 'Per-project job classification labels (ADR-0042) — name + color, no behavior beyond display.';

-- No deleted_at here — unlike most entities, job_types relies on ON DELETE SET NULL
-- (below) plus a service-layer delete guard rather than soft-delete: the guard
-- rejects the delete while anything still references the type, and the FK
-- constraint is only a safety net for a path that somehow bypasses it.
ALTER TABLE jobs
    ADD COLUMN type_id UUID REFERENCES job_types(id) ON DELETE SET NULL;

-- Coming-soon: available flipped to TRUE in a follow-up migration once ready to sell
-- (see V018/V025 for the JOB_TEMPLATES/RECURRING_SCHEDULING precedent this follows).
INSERT INTO subscription_addons (key, name, price_monthly, price_annual, available, display_order) VALUES
  ('JOB_TYPES', 'Job types', 1490, 1242, FALSE, 11);
