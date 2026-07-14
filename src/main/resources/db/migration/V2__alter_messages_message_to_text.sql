-- Alter message column to support longer JSON payloads for reminders
ALTER TABLE messages ALTER COLUMN message TYPE TEXT;
