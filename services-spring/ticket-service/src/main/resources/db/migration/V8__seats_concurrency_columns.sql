-- Add version column for optimistic locking
ALTER TABLE seats ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- Add fencing_token column for Redis fencing token verification at DB level
ALTER TABLE seats ADD COLUMN IF NOT EXISTS fencing_token BIGINT DEFAULT 0;

-- Add locked_by column to track who holds the lock
ALTER TABLE seats ADD COLUMN IF NOT EXISTS locked_by UUID;
