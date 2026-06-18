-- Soft-delete for recurring_schedules.
-- Deleting a schedule with existing jobs previously violated the FK on jobs.source_schedule_id.
-- Soft delete avoids the constraint entirely: the row stays, jobs keep their reference,
-- and all active queries filter WHERE deleted_at IS NULL.
ALTER TABLE recurring_schedules
    ADD COLUMN deleted_at TIMESTAMPTZ;

COMMENT ON COLUMN recurring_schedules.deleted_at IS 'NULL = active; set on soft delete — jobs referencing this schedule are preserved';
