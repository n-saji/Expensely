-- V5: Add has_transactions column to users table

ALTER TABLE users ADD COLUMN has_transactions BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill existing users who already have transactions in transactions table
UPDATE users
SET has_transactions = TRUE
WHERE id IN (
    SELECT DISTINCT user_id FROM transactions WHERE user_id IS NOT NULL
);
