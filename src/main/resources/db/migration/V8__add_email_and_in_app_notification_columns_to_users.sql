-- V8: Add email_notifications_enabled and in_app_notifications_enabled to users table

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS in_app_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Backfill from existing notifications_enabled column if available
UPDATE users
SET in_app_notifications_enabled = notifications_enabled
WHERE notifications_enabled IS NOT NULL;
