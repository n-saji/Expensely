-- Alter logging table columns to TEXT to avoid VARCHAR(255) length constraints and improve compatibility with PgBouncer
ALTER TABLE function_logs ALTER COLUMN arguments TYPE TEXT;
ALTER TABLE function_logs ALTER COLUMN result TYPE TEXT;
ALTER TABLE function_logs ALTER COLUMN error_message TYPE TEXT;
ALTER TABLE function_logs ALTER COLUMN stack_trace TYPE TEXT;

ALTER TABLE api_request_logs ALTER COLUMN request_headers TYPE TEXT;
ALTER TABLE api_request_logs ALTER COLUMN response_headers TYPE TEXT;
ALTER TABLE api_request_logs ALTER COLUMN request_body TYPE TEXT;
ALTER TABLE api_request_logs ALTER COLUMN response_body TYPE TEXT;
ALTER TABLE api_request_logs ALTER COLUMN user_agent TYPE TEXT;
