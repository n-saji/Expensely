-- V1: Create reminders, reminder_notifications, and reminder_snooze tables

CREATE TABLE reminders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category_id UUID NOT NULL,
    amount DECIMAL(19, 2),
    currency VARCHAR(10),
    due_at TIMESTAMP NOT NULL,
    repeat_type VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_reminders_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reminders_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE CASCADE
);

CREATE TABLE reminder_notifications (
    id UUID PRIMARY KEY,
    reminder_id UUID NOT NULL,
    notification_offset VARCHAR(50) NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    delivery_type VARCHAR(50) NOT NULL,
    error_message TEXT,
    CONSTRAINT fk_notifications_reminder FOREIGN KEY (reminder_id) REFERENCES reminders(id) ON DELETE CASCADE
);

CREATE TABLE reminder_snooze (
    id UUID PRIMARY KEY,
    reminder_id UUID NOT NULL,
    snoozed_until TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_snooze_reminder FOREIGN KEY (reminder_id) REFERENCES reminders(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_reminders_user ON reminders(user_id);
CREATE INDEX idx_reminders_due_at ON reminders(due_at);
CREATE INDEX idx_reminder_notifications_scheduled_at ON reminder_notifications(scheduled_at);
CREATE INDEX idx_reminder_notifications_status ON reminder_notifications(status);
