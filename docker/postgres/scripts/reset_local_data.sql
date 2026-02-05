-- ============================================
-- OpsClear - Reset Local Data
-- ============================================
-- WARNING: This will DELETE ALL DATA!
-- Only use in development environment!

-- Truncate all tables (add new tables here as they are created)
TRUNCATE users RESTART IDENTITY CASCADE;

-- Verify
SELECT 'Local data reset complete' as status;
