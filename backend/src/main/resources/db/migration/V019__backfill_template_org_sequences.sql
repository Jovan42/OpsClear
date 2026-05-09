-- V017 added the TEMPLATE entity type but did not seed org_sequences for existing orgs.
-- This backfills the counter row so FriendlyIdService can generate TPL-NNN IDs.
INSERT INTO org_sequences (org_id, entity_type, last_value)
SELECT id, 'TEMPLATE', 0
FROM organisations
ON CONFLICT (org_id, entity_type) DO NOTHING;
