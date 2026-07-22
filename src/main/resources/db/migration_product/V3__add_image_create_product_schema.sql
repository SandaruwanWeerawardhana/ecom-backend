ALTER TABLE product_categories
    ADD COLUMN category_image LONGTEXT NULL COMMENT 'S3 object key or public/presigned URL for the category image';
