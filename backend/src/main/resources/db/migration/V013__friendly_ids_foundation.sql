-- org_settings: one row per org, stores configurable entity-type prefixes
CREATE TABLE org_settings (
    org_id           UUID        PRIMARY KEY REFERENCES organisations(id) ON DELETE CASCADE,
    job_prefix       VARCHAR(3)  NOT NULL DEFAULT 'JOB',
    project_prefix   VARCHAR(3)  NOT NULL DEFAULT 'PRJ',
    milestone_prefix VARCHAR(3)  NOT NULL DEFAULT 'MIL',
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- org_sequences: per-org, per-entity-type counter for friendly ID generation
CREATE TABLE org_sequences (
    org_id      UUID        NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    entity_type VARCHAR(20) NOT NULL,
    last_value  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (org_id, entity_type)
);

-- friendly_id columns — nullable until backfilled in V014
ALTER TABLE projects   ADD COLUMN friendly_id VARCHAR(20);
ALTER TABLE jobs       ADD COLUMN friendly_id VARCHAR(20);
ALTER TABLE milestones ADD COLUMN friendly_id VARCHAR(20);

-- Seed existing orgs with default settings and zero-value sequences
INSERT INTO org_settings (org_id)
SELECT id FROM organisations WHERE deleted_at IS NULL;

INSERT INTO org_sequences (org_id, entity_type, last_value)
SELECT id, 'PROJECT',   0 FROM organisations WHERE deleted_at IS NULL
UNION ALL
SELECT id, 'JOB',       0 FROM organisations WHERE deleted_at IS NULL
UNION ALL
SELECT id, 'MILESTONE', 0 FROM organisations WHERE deleted_at IS NULL;
