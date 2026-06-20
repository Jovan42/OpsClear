-- One row per cron tick the poller skipped due to downtime.
-- Reviewed and actioned manually (materialize or dismiss) via the missed-runs API.
CREATE TABLE schedule_missed_runs (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    schedule_id  UUID        NOT NULL REFERENCES recurring_schedules(id) ON DELETE CASCADE,
    expected_at  TIMESTAMPTZ NOT NULL,
    recorded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX schedule_missed_runs_schedule_id_idx
    ON schedule_missed_runs (schedule_id);

COMMENT ON TABLE  schedule_missed_runs             IS 'One row per cron tick the poller skipped due to downtime; reviewed and actioned manually';
COMMENT ON COLUMN schedule_missed_runs.expected_at IS 'The instant the tick should have fired';
