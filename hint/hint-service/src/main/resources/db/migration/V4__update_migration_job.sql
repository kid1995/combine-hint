ALTER TABLE migration_job DROP COLUMN last_merged_point;

ALTER TABLE migration_job ADD COLUMN type text NOT NULL DEFAULT 'UNKNOWN';
