-- V0: Create base tables required by the application

-- 1. users
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    country_code VARCHAR(255),
    phone VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL,
    currency VARCHAR(255) NOT NULL DEFAULT 'USD',
    theme VARCHAR(255) NOT NULL DEFAULT 'light',
    theme_color VARCHAR(255) NOT NULL DEFAULT 'teal',
    language VARCHAR(255) NOT NULL DEFAULT 'en',
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_admin BOOLEAN NOT NULL DEFAULT false,
    notifications_enabled BOOLEAN NOT NULL DEFAULT true,
    alerts_enabled BOOLEAN NOT NULL DEFAULT true,
    profile_pic_file_path VARCHAR(1000),
    is_oauth2_user BOOLEAN DEFAULT false,
    is_profile_complete BOOLEAN DEFAULT true,
    is_email_verified BOOLEAN DEFAULT false
);

-- 2. categories
CREATE TABLE categories (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    icon VARCHAR(255),
    color VARCHAR(255)
);

-- 3. budgets
CREATE TABLE budgets (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    category_id UUID REFERENCES categories(id),
    amount_limit NUMERIC(15, 2) NOT NULL,
    amount_spent NUMERIC(15, 2),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    base_currency_amount NUMERIC(19, 4),
    exchange_rate NUMERIC(19, 8),
    period VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    threshold_50_crossed BOOLEAN NOT NULL DEFAULT false,
    threshold_75_crossed BOOLEAN NOT NULL DEFAULT false,
    threshold_100_crossed BOOLEAN NOT NULL DEFAULT false
);

-- 4. transactions
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    category_id UUID REFERENCES categories(id),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    base_currency_amount NUMERIC(19, 4),
    base_currency VARCHAR(3),
    exchange_rate NUMERIC(19, 8),
    description VARCHAR(255),
    transaction_date TIMESTAMP,
    receipt_url VARCHAR(255),
    type VARCHAR(10) NOT NULL
);

CREATE INDEX idx_transaction_date_user_id_type ON transactions(user_id, type, transaction_date);

-- 5. recurring_expenses
CREATE TABLE recurring_expenses (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    category_id UUID REFERENCES categories(id),
    amount NUMERIC(19, 2),
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description VARCHAR(255),
    recurrence SMALLINT,
    next_occurrence DATE,
    active BOOLEAN NOT NULL,
    date DATE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- 6. messages
CREATE TABLE messages (
    id UUID PRIMARY KEY,
    user_id UUID,
    message TEXT,
    type SMALLINT,
    is_delivered BOOLEAN NOT NULL DEFAULT false,
    is_seen BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP
);

-- 7. email_otps
CREATE TABLE email_otps (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    otp_hash VARCHAR(255) NOT NULL,
    purpose VARCHAR(32) NOT NULL DEFAULT 'EMAIL_VERIFY',
    expires_at TIMESTAMP NOT NULL,
    last_sent_at TIMESTAMP NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    CONSTRAINT uq_email_otps_user_purpose UNIQUE (user_id, purpose)
);

-- 8. exchange_rates
CREATE TABLE exchange_rates (
    id BIGSERIAL PRIMARY KEY,
    base_currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    target_currency VARCHAR(3) NOT NULL,
    rate NUMERIC(19, 8) NOT NULL,
    fetched_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_exchange_rates_base_target UNIQUE (base_currency, target_currency)
);

-- 9. expired_tokens
CREATE TABLE expired_tokens (
    token VARCHAR(255) PRIMARY KEY,
    user_id UUID REFERENCES users(id)
);

-- 10. transaction_files
CREATE TABLE transaction_files (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(255) NOT NULL,
    transactions JSONB NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL
);

-- 11. expense_files
CREATE TABLE expense_files (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(255) NOT NULL,
    expenses JSONB NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL
);

-- 12. api_request_logs
CREATE TABLE api_request_logs (
    id UUID PRIMARY KEY,
    user_id UUID,
    request_id VARCHAR(255),
    method VARCHAR(255),
    path VARCHAR(255),
    query_string VARCHAR(255),
    ip_address VARCHAR(255),
    user_agent TEXT,
    status_code INTEGER,
    duration_ms BIGINT,
    request_headers TEXT,
    response_headers TEXT,
    request_body TEXT,
    response_body TEXT,
    created_at TIMESTAMP
);

CREATE INDEX idx_api_request_logs_created_at ON api_request_logs(created_at);
CREATE INDEX idx_api_request_logs_user_id ON api_request_logs(user_id);

-- 13. function_logs
CREATE TABLE function_logs (
    id UUID PRIMARY KEY,
    user_id UUID,
    request_id VARCHAR(255),
    layer VARCHAR(255),
    class_name VARCHAR(255),
    method_name VARCHAR(255),
    success BOOLEAN,
    duration_ms BIGINT,
    thread_name VARCHAR(255),
    arguments TEXT,
    result TEXT,
    error_message TEXT,
    stack_trace TEXT,
    created_at TIMESTAMP
);

CREATE INDEX idx_function_logs_created_at ON function_logs(created_at);
CREATE INDEX idx_function_logs_user_id ON function_logs(user_id);
CREATE INDEX idx_function_logs_request_id ON function_logs(request_id);
