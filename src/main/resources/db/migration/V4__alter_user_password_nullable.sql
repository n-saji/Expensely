-- Flyway Migration V4: Allow NULL for password column in users table for Google OAuth2 users
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
