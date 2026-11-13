package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
		name = "recurring_expense_notifications",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_recurring_expense_notification_offset",
				columnNames = {"recurring_expense_id", "days_before"}
		)
)
@Getter
@Setter
public class RecurringExpenseNotification {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recurring_expense_id", nullable = false)
	private RecurringExpense recurringExpense;

	@Column(name = "days_before", nullable = false)
	private int daysBefore;

	@Column(name = "email_enabled", nullable = false)
	private boolean emailEnabled;

	@Column(name = "in_app_enabled", nullable = false)
	private boolean inAppEnabled;

	@Column(name = "last_email_notified_occurrence")
	private LocalDate lastEmailNotifiedOccurrence;

	@Column(name = "last_in_app_notified_occurrence")
	private LocalDate lastInAppNotifiedOccurrence;
}
