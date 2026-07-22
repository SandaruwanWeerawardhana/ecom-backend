-- ============================================
-- FLYWAY REPAIR SCRIPT
-- Run this script manually in MySQL to fix the failed migration
-- ============================================

USE beyos_auth_db;

-- Check current Flyway history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Delete the failed V4 migration entry
DELETE FROM flyway_schema_history WHERE version = '4';

-- Verify cleanup
SELECT * FROM flyway_schema_history ORDER BY installed_rank;

-- Now you can restart the application and V4 will run successfully

