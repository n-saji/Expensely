-- V9: Create exchange_rate_history table for storing time-series exchange rate snapshots

CREATE TABLE exchange_rate_history (
    id BIGSERIAL PRIMARY KEY,
    base_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    target_currency VARCHAR(3) NOT NULL,
    rate NUMERIC(19, 8) NOT NULL,
    recorded_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_exchange_rate_history_pair_recorded_at
    ON exchange_rate_history (base_currency, target_currency, recorded_at);
