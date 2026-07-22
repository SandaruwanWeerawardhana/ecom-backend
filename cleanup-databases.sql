-- ============================================
-- DATABASE CLEANUP AND RECREATION SCRIPT
-- Run this script to fix the database mess
-- ============================================

-- Step 1: Drop the existing messy database
DROP DATABASE IF EXISTS beyos_auth_db;
DROP DATABASE IF EXISTS beyos_customers_db;
DROP DATABASE IF EXISTS beyos_admin_db;

-- Step 2: Create three separate databases
CREATE DATABASE beyos_auth_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE beyos_customers_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE beyos_admin_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Verification
SHOW DATABASES LIKE 'beyos%';

-- Now restart your application and Flyway will create the tables in the correct databases

