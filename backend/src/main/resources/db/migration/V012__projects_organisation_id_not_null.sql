-- Tighten projects.organisation_id to NOT NULL.
-- All existing projects have been backfilled by V011.
-- New projects always receive organisation_id from the caller's org membership (ProjectService).
ALTER TABLE projects ALTER COLUMN organisation_id SET NOT NULL;
