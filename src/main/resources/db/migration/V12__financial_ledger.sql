-- Additive migration: historical transactions deliberately retain NULL account links.
CREATE TABLE accounts (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(255) NOT NULL, type VARCHAR(32) NOT NULL,
  opening_balance NUMERIC(19,2) NOT NULL DEFAULT 0,
  currency VARCHAR(3) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE debts (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(255) NOT NULL, debt_type VARCHAR(32) NOT NULL,
  original_balance NUMERIC(19,2) NOT NULL, opening_balance NUMERIC(19,2) NOT NULL,
  interest_rate NUMERIC(8,4), minimum_payment NUMERIC(19,2),
  due_date DATE, lender VARCHAR(255), currency VARCHAR(3) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  linked_account_id UUID UNIQUE REFERENCES accounts(id)
);
CREATE TABLE savings_goals (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(255) NOT NULL, target_amount NUMERIC(19,2) NOT NULL,
  opening_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
  target_date DATE, linked_account_id UUID NOT NULL REFERENCES accounts(id),
  active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE investment_accounts (
  id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(255) NOT NULL, provider VARCHAR(255),
  account_type VARCHAR(32) NOT NULL,
  opening_contributions NUMERIC(19,2) NOT NULL DEFAULT 0,
  linked_account_id UUID NOT NULL UNIQUE REFERENCES accounts(id),
  active BOOLEAN NOT NULL DEFAULT TRUE
);
ALTER TABLE transactions ALTER COLUMN type TYPE VARCHAR(32);
ALTER TABLE transactions ADD COLUMN account_id UUID REFERENCES accounts(id);
ALTER TABLE transactions ADD COLUMN destination_account_id UUID REFERENCES accounts(id);
ALTER TABLE transactions ADD COLUMN debt_id UUID REFERENCES debts(id);
ALTER TABLE transactions ADD COLUMN savings_goal_id UUID REFERENCES savings_goals(id);
ALTER TABLE transactions ADD COLUMN investment_account_id UUID REFERENCES investment_accounts(id);
ALTER TABLE transactions ADD COLUMN principal_amount NUMERIC(19,2);
ALTER TABLE transactions ADD COLUMN interest_amount NUMERIC(19,2);
ALTER TABLE transactions ADD COLUMN notes VARCHAR(255);
CREATE INDEX idx_transactions_account ON transactions(account_id);
CREATE INDEX idx_transactions_destination ON transactions(destination_account_id);
CREATE INDEX idx_transactions_debt ON transactions(debt_id);
CREATE INDEX idx_transactions_goal ON transactions(savings_goal_id);
CREATE INDEX idx_transactions_investment ON transactions(investment_account_id);
