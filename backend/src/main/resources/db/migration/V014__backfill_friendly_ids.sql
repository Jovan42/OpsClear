-- Backfill friendly IDs for all existing records that predate the sequence service.
-- Idempotent: only touches rows where friendly_id IS NULL.
-- Ordered by created_at ASC within each org so IDs reflect creation order.

-- Projects
WITH ranked AS (
    SELECT p.id,
           s.project_prefix,
           ROW_NUMBER() OVER (PARTITION BY p.organisation_id ORDER BY p.created_at) AS rn
    FROM   projects p
    JOIN   org_settings s ON s.org_id = p.organisation_id
    WHERE  p.friendly_id IS NULL
)
UPDATE projects p
SET    friendly_id = r.project_prefix || '-' || LPAD(r.rn::text, 3, '0')
FROM   ranked r
WHERE  p.id = r.id;

-- Jobs
WITH ranked AS (
    SELECT j.id,
           s.job_prefix,
           ROW_NUMBER() OVER (PARTITION BY proj.organisation_id ORDER BY j.created_at) AS rn
    FROM   jobs j
    JOIN   projects proj ON proj.id = j.project_id
    JOIN   org_settings s ON s.org_id = proj.organisation_id
    WHERE  j.friendly_id IS NULL
)
UPDATE jobs j
SET    friendly_id = r.job_prefix || '-' || LPAD(r.rn::text, 3, '0')
FROM   ranked r
WHERE  j.id = r.id;

-- Milestones
WITH ranked AS (
    SELECT m.id,
           s.milestone_prefix,
           ROW_NUMBER() OVER (PARTITION BY proj.organisation_id ORDER BY m.created_at) AS rn
    FROM   milestones m
    JOIN   projects proj ON proj.id = m.project_id
    JOIN   org_settings s ON s.org_id = proj.organisation_id
    WHERE  m.friendly_id IS NULL
)
UPDATE milestones m
SET    friendly_id = r.milestone_prefix || '-' || LPAD(r.rn::text, 3, '0')
FROM   ranked r
WHERE  m.id = r.id;

-- Sync org_sequences.last_value to the highest assigned sequence number per org.
-- Counts ALL records (including soft-deleted) so the next-ID counter stays correct.
UPDATE org_sequences os
SET    last_value = COALESCE((
    SELECT COUNT(*)
    FROM   projects p
    WHERE  p.organisation_id = os.org_id
), 0)
WHERE  os.entity_type = 'PROJECT';

UPDATE org_sequences os
SET    last_value = COALESCE((
    SELECT COUNT(*)
    FROM   jobs j
    JOIN   projects p ON p.id = j.project_id
    WHERE  p.organisation_id = os.org_id
), 0)
WHERE  os.entity_type = 'JOB';

UPDATE org_sequences os
SET    last_value = COALESCE((
    SELECT COUNT(*)
    FROM   milestones m
    JOIN   projects p ON p.id = m.project_id
    WHERE  p.organisation_id = os.org_id
), 0)
WHERE  os.entity_type = 'MILESTONE';
