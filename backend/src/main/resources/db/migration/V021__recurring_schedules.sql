-- Recurring job scheduling: schedules fire cron-based jobs from a template
-- with optional round-robin assignee rotation.

CREATE TABLE recurring_schedules (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id             UUID         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    template_id            UUID         NOT NULL REFERENCES job_templates(id),
    name                   VARCHAR(100) NOT NULL,
    cron_expression        VARCHAR(100) NOT NULL,
    timezone               VARCHAR(60)  NOT NULL,
    paused_until           TIMESTAMPTZ,
    expires_at             TIMESTAMPTZ,
    current_rotation_index INT          NOT NULL DEFAULT 0,
    next_run_at            TIMESTAMPTZ  NOT NULL,
    last_run_at            TIMESTAMPTZ,
    created_by             UUID         NOT NULL REFERENCES users(id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  recurring_schedules                          IS 'Cron-driven schedules that materialise jobs from a template on a fixed cadence';
COMMENT ON COLUMN recurring_schedules.paused_until            IS 'NULL = active; future timestamp = timed pause (auto-resumes); 9999-01-01 sentinel = indefinitely paused';
COMMENT ON COLUMN recurring_schedules.current_rotation_index  IS 'Monotonically increasing; round-robin pick = assignees[index % count]';
COMMENT ON COLUMN recurring_schedules.expires_at              IS 'Exclusive upper bound — last fire is strictly before this instant; NULL = no expiry';

CREATE INDEX recurring_schedules_project_id_idx
    ON recurring_schedules (project_id);

-- Partial index to speed up the poller due-schedules query.
-- The predicate covers only the IS NULL half because now() is not IMMUTABLE
-- and cannot appear in an index predicate. The poller's full predicate
-- (paused_until IS NULL OR paused_until <= now()) is evaluated at query time;
-- the index still prunes all indefinitely-paused schedules (sentinel 9999-01-01).
CREATE INDEX recurring_schedules_due_idx
    ON recurring_schedules (next_run_at)
    WHERE paused_until IS NULL;

-- Ordered list of assignees iterated round-robin per schedule.
CREATE TABLE schedule_assignees (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id  UUID NOT NULL REFERENCES recurring_schedules(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id),
    "order"      INT  NOT NULL
);

COMMENT ON COLUMN schedule_assignees."order" IS 'Zero-based rotation order; round-robin pick uses current_rotation_index % count';

CREATE UNIQUE INDEX schedule_assignees_position_idx
    ON schedule_assignees (schedule_id, "order");

-- Track which schedule automatically created each job (nullable — NULL for manual jobs).
-- No ON DELETE action: jobs outlive the schedule that produced them.
ALTER TABLE jobs
    ADD COLUMN source_schedule_id UUID REFERENCES recurring_schedules(id);

COMMENT ON COLUMN jobs.source_schedule_id IS 'NULL for manually created jobs; set to the schedule that auto-created this job';
