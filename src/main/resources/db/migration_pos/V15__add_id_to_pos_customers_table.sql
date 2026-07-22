-- ============================================
-- Migration: V15__add_id_to_pos_customers_table.sql
-- Purpose: Add a surrogate numeric `id` primary key to `pos_customers` while
--          preserving existing `uuid` as a unique identifier.
-- Notes: This migration assumes there are no foreign keys referencing
--        the UUID primary key in other tables. If other tables reference
--        `pos_customers(uuid)` as a foreign key, you'll need to update
--        those constraints to reference `id` or keep uuid as PK.
-- ============================================

USE beyos_pos;

ALTER TABLE pos_customers
  DROP PRIMARY KEY,
  ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT FIRST,
  ADD PRIMARY KEY (id),
  ADD UNIQUE KEY ux_pos_customers_uuid (uuid);

-- End of migration

