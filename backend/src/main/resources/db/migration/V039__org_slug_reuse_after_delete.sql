-- Replace broad unique constraint with a partial unique index scoped to active
-- (non-deleted) organisations, same pattern as V002 for projects.name+owner. Without
-- this, OrganisationService.requireSlugAvailable() (which only checks non-deleted
-- rows) disagreed with the DB constraint (global-unique): the app-level check said a
-- soft-deleted org's slug was free, then the INSERT hit this constraint and 500'd
-- instead of either succeeding or returning a clean 409 (JOB-238).
ALTER TABLE organisations DROP CONSTRAINT organisations_slug_key;

CREATE UNIQUE INDEX uk_organisations_slug_active
    ON organisations (slug)
    WHERE deleted_at IS NULL;
