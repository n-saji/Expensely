CREATE TABLE user_ai_refresh_state (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    relevant_period DATE NOT NULL,
    current_data_version BIGINT NOT NULL DEFAULT 1,
    processed_data_version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    active_generation_id UUID,
    last_generated_at TIMESTAMPTZ,
    next_eligible_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lease_token UUID,
    lease_expires_at TIMESTAMPTZ,
    last_error_category VARCHAR(64),
    last_prompt_tokens INTEGER,
    last_completion_tokens INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, relevant_period),
    CONSTRAINT chk_user_ai_refresh_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'NOT_AVAILABLE')
    ),
    CONSTRAINT chk_user_ai_versions CHECK (
        current_data_version >= 0 AND processed_data_version >= 0
    )
);

CREATE INDEX idx_user_ai_refresh_dispatch
    ON user_ai_refresh_state (next_eligible_at, updated_at, user_id)
    WHERE current_data_version > processed_data_version;

CREATE INDEX idx_user_ai_refresh_lease
    ON user_ai_refresh_state (lease_expires_at)
    WHERE lease_expires_at IS NOT NULL;

CREATE TABLE ai_insights (
    id UUID PRIMARY KEY,
    generation_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    insight_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT NOT NULL,
    recommendation TEXT,
    category VARCHAR(255),
    confidence VARCHAR(16) NOT NULL,
    supporting_metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    data_version BIGINT NOT NULL,
    generation_version INTEGER NOT NULL DEFAULT 1,
    model_version VARCHAR(128),
    display_order SMALLINT NOT NULL DEFAULT 0,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    CONSTRAINT chk_ai_insight_severity CHECK (severity IN ('INFO', 'POSITIVE', 'WARNING')),
    CONSTRAINT chk_ai_insight_confidence CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_ai_insight_period CHECK (period_end >= period_start),
    CONSTRAINT uq_ai_insight_generation_order UNIQUE (generation_id, display_order)
);

CREATE INDEX idx_ai_insights_active_lookup
    ON ai_insights (user_id, period_start, generation_id, display_order);

CREATE OR REPLACE FUNCTION mark_ai_insights_stale(p_user_id UUID, p_period DATE)
RETURNS VOID
LANGUAGE SQL
AS $$
    INSERT INTO user_ai_refresh_state (
        user_id, relevant_period, current_data_version, processed_data_version,
        status, next_eligible_at, updated_at
    )
    VALUES (
        p_user_id, DATE_TRUNC('month', p_period)::DATE, 1, 0,
        'PENDING', NOW(), NOW()
    )
    ON CONFLICT (user_id, relevant_period) DO UPDATE
    SET current_data_version = user_ai_refresh_state.current_data_version + 1,
        status = 'PENDING',
        next_eligible_at = LEAST(user_ai_refresh_state.next_eligible_at, NOW()),
        last_error_category = NULL,
        updated_at = NOW();
$$;

CREATE OR REPLACE FUNCTION invalidate_ai_from_transaction()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') AND OLD.user_id IS NOT NULL AND OLD.transaction_date IS NOT NULL THEN
        PERFORM mark_ai_insights_stale(OLD.user_id, OLD.transaction_date::DATE);
    END IF;
    IF TG_OP IN ('INSERT', 'UPDATE') AND NEW.user_id IS NOT NULL AND NEW.transaction_date IS NOT NULL THEN
        IF TG_OP = 'INSERT'
           OR OLD.user_id IS DISTINCT FROM NEW.user_id
           OR DATE_TRUNC('month', OLD.transaction_date) IS DISTINCT FROM DATE_TRUNC('month', NEW.transaction_date) THEN
            PERFORM mark_ai_insights_stale(NEW.user_id, NEW.transaction_date::DATE);
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_transactions_invalidate_ai
AFTER INSERT OR UPDATE OR DELETE ON transactions
FOR EACH ROW EXECUTE FUNCTION invalidate_ai_from_transaction();

CREATE OR REPLACE FUNCTION invalidate_current_ai_period()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    affected_user UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN
        affected_user := OLD.user_id;
    ELSE
        affected_user := NEW.user_id;
    END IF;
    IF affected_user IS NOT NULL THEN
        PERFORM mark_ai_insights_stale(affected_user, CURRENT_DATE);
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_budgets_invalidate_ai
AFTER INSERT OR DELETE OR UPDATE OF amount_limit, currency, base_currency_amount,
    period, start_date, end_date, is_active, category_id ON budgets
FOR EACH ROW EXECUTE FUNCTION invalidate_current_ai_period();

CREATE TRIGGER trg_categories_invalidate_ai
AFTER INSERT OR UPDATE OR DELETE ON categories
FOR EACH ROW EXECUTE FUNCTION invalidate_current_ai_period();

CREATE TRIGGER trg_recurring_expenses_invalidate_ai
AFTER INSERT OR UPDATE OR DELETE ON recurring_expenses
FOR EACH ROW EXECUTE FUNCTION invalidate_current_ai_period();

-- Seed the current period so existing active users do not have to edit a transaction
-- before the first scheduled analysis can discover them.
INSERT INTO user_ai_refresh_state (
    user_id, relevant_period, current_data_version, processed_data_version,
    status, next_eligible_at, updated_at
)
SELECT DISTINCT t.user_id, DATE_TRUNC('month', CURRENT_DATE)::DATE, 1, 0,
       'PENDING', NOW(), NOW()
FROM transactions t
JOIN users u ON u.id = t.user_id
WHERE u.is_active = TRUE
  AND t.transaction_date >= DATE_TRUNC('month', CURRENT_DATE)
  AND t.transaction_date < DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month'
ON CONFLICT (user_id, relevant_period) DO NOTHING;
