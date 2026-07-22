-- JOB-131: audit found shipped-but-never-activated addons.
--
-- RECURRING_SCHEDULING shipped complete (JOB-111 through JOB-118, all COMPLETED) but never
-- got the follow-up activation migration that JOB_TEMPLATES got (V018).
--
-- JOB_LINKS is activated ahead of JOB-124 (project-level links nav dropdown) merging — job-level
-- links (JOB-121 through JOB-123) are complete and deployed; project-level links will follow shortly.
UPDATE subscription_addons SET available = TRUE WHERE key IN ('RECURRING_SCHEDULING', 'JOB_LINKS');
