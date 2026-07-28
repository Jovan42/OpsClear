-- JOB-158: job types (JOB-152 through JOB-157) shipped complete — schema, backend CRUD/filtering/
-- dashboard aggregate, frontend management UI/badges/filter/dashboard widget, and template
-- default-type integration. Activate the addon for sale.
UPDATE subscription_addons SET available = TRUE WHERE key = 'JOB_TYPES';
