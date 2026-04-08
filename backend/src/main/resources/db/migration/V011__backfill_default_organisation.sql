-- Backfill organisation_id on users and projects.
-- If one org already exists, use it. If none exists, create a default one.
-- users.organisation_id stays nullable (no constraint added here).
-- projects.organisation_id is tightened to NOT NULL in the next migration.

DO $$
DECLARE
    target_org_id UUID;
    org_count     INT;
BEGIN
    SELECT COUNT(*) INTO org_count FROM organisations WHERE deleted_at IS NULL;

    IF org_count = 0 THEN
        -- No org exists — create a default one
        INSERT INTO organisations (id, name, slug, created_at)
        VALUES (gen_random_uuid(), 'Default', 'DEF', NOW())
        RETURNING id INTO target_org_id;

        -- Add all users as OWNER members of the default org
        INSERT INTO organisation_members (organisation_id, user_id, org_role, joined_at)
        SELECT target_org_id, id, 'OWNER', NOW()
        FROM users
        ON CONFLICT DO NOTHING;

        -- Set organisation_id on all users and projects
        UPDATE users    SET organisation_id = target_org_id WHERE organisation_id IS NULL;
        UPDATE projects SET organisation_id = target_org_id WHERE organisation_id IS NULL;

    ELSIF org_count = 1 THEN
        -- One org exists — use it for any unassigned records
        SELECT id INTO target_org_id FROM organisations WHERE deleted_at IS NULL;

        UPDATE users    SET organisation_id = target_org_id WHERE organisation_id IS NULL;
        UPDATE projects SET organisation_id = target_org_id WHERE organisation_id IS NULL;

    ELSE
        -- Multiple orgs: assign each project to its owner's org where possible
        UPDATE projects p
        SET organisation_id = (
            SELECT om.organisation_id
            FROM organisation_members om
            WHERE om.user_id = p.owner_id
            ORDER BY om.joined_at
            FETCH FIRST 1 ROW ONLY
        )
        WHERE p.organisation_id IS NULL;
    END IF;
END $$;
