-- Add blocking metadata columns to jobs table
-- Populated when a job transitions to BLOCKED; cleared on unblock.
-- blocked_reason_id references project_block_reasons for consistent reason text across jobs.

ALTER TABLE jobs
    ADD COLUMN blocked_by        UUID REFERENCES users(id),
    ADD COLUMN blocked_reason_id UUID REFERENCES project_block_reasons(id),
    ADD COLUMN blocked_at        TIMESTAMP;

COMMENT ON COLUMN jobs.blocked_by        IS 'User who set the block (OWNER/ADMIN or assigned MEMBER)';
COMMENT ON COLUMN jobs.blocked_reason_id IS 'FK to project_block_reasons — NULL when not blocked';
COMMENT ON COLUMN jobs.blocked_at        IS 'When the block was set — NULL when not blocked';
