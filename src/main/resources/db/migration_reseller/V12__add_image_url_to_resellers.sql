-- V12__add_image_url_to_resellers.sql
-- Add image URL column to resellers table

ALTER TABLE resellers
    ADD COLUMN image_url VARCHAR(2500) NULL AFTER phone;

