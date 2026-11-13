CREATE TABLE recurring_expense_notifications (
    id UUID PRIMARY KEY,
    recurring_expense_id UUID NOT NULL,
    days_before INTEGER NOT NULL,
    email_enabled BOOLEAN NOT NULL DEFAULT false,
    in_app_enabled BOOLEAN NOT NULL DEFAULT false,
    last_email_notified_occurrence DATE,
    last_in_app_notified_occurrence DATE,
    CONSTRAINT fk_recurring_expense_notifications_expense
        FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expenses(id) ON DELETE CASCADE,
    CONSTRAINT uk_recurring_expense_notification_offset
        UNIQUE (recurring_expense_id, days_before),
    CONSTRAINT chk_recurring_expense_notification_days
        CHECK (days_before BETWEEN 1 AND 365)
);

CREATE INDEX idx_recurring_expense_notifications_expense
    ON recurring_expense_notifications(recurring_expense_id);
