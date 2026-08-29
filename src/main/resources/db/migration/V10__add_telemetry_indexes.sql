-- V10: Indexes to support admin telemetry aggregation/search queries

CREATE INDEX idx_api_request_logs_path ON api_request_logs (path);
CREATE INDEX idx_api_request_logs_status_code ON api_request_logs (status_code);
CREATE INDEX idx_api_request_logs_created_at_status ON api_request_logs (created_at, status_code);

CREATE INDEX idx_function_logs_success ON function_logs (success);
CREATE INDEX idx_function_logs_class_method ON function_logs (class_name, method_name);
