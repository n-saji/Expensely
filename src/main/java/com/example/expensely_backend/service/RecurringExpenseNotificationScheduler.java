package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.RecurringExpense;
import com.example.expensely_backend.model.RecurringExpenseNotification;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.RecurringExpenseRepository;
import com.example.expensely_backend.utils.Mailgun;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class RecurringExpenseNotificationScheduler {
	private final RecurringExpenseRepository recurringExpenseRepository;
	private final WebSocketService webSocketService;
	private final Mailgun mailgun;
	private final DbLogService dbLogService;

	public RecurringExpenseNotificationScheduler(
			RecurringExpenseRepository recurringExpenseRepository,
			WebSocketService webSocketService,
			Mailgun mailgun,
			DbLogService dbLogService) {
		this.recurringExpenseRepository = recurringExpenseRepository;
		this.webSocketService = webSocketService;
		this.mailgun = mailgun;
		this.dbLogService = dbLogService;
	}

	@Scheduled(cron = "0 0 9 * * *")
	@Transactional
	public void run() {
		processNotifications(LocalDate.now());
	}

	void processNotifications(LocalDate today) {
		List<RecurringExpense> expenses = recurringExpenseRepository.findActiveWithNotifications(today);
		for (RecurringExpense expense : expenses) {
			if (expense.getNextOccurrence() == null) {
				continue;
			}
			for (RecurringExpenseNotification notification : expense.getNotifications()) {
				LocalDate notificationDate = expense.getNextOccurrence().minusDays(notification.getDaysBefore());
				if (today.isBefore(notificationDate) || !today.isBefore(expense.getNextOccurrence())) {
					continue;
				}
				deliver(expense, notification);
			}
		}
		recurringExpenseRepository.saveAll(expenses);
	}

	private void deliver(RecurringExpense expense, RecurringExpenseNotification notification) {
		User user = expense.getUser();
		LocalDate occurrence = expense.getNextOccurrence();
		String timing = notification.getDaysBefore() == 1
				? "tomorrow"
				: "in " + notification.getDaysBefore() + " days";
		String message = String.format(
				"Recurring expense '%s' (%s %s) is due %s on %s.",
				expense.getDescription(),
				expense.getCurrency(),
				expense.getAmount().toPlainString(),
				timing,
				occurrence.format(DateTimeFormatter.ofPattern("MMM d, yyyy")));

		if (notification.isInAppEnabled() &&
				Boolean.TRUE.equals(user.getInAppNotificationsEnabled()) &&
				!occurrence.equals(notification.getLastInAppNotifiedOccurrence())) {
			try {
				MessageDTO alert = new MessageDTO();
				alert.setMessage(message);
				alert.setSender(globals.SERVER_SENDER);
				alert.setType(globals.MessageType.INFO);
				webSocketService.sendAlerts(user, alert);
				notification.setLastInAppNotifiedOccurrence(occurrence);
			} catch (Exception e) {
				dbLogService.logError("scheduler", getClass().getName(), "deliver",
						"Failed in-app recurring expense notification for " + expense.getId(), e);
			}
		}

		if (notification.isEmailEnabled() &&
				Boolean.TRUE.equals(user.getEmailNotificationsEnabled()) &&
				!occurrence.equals(notification.getLastEmailNotifiedOccurrence())) {
			try {
				mailgun.sendSimpleMessage(
						user.getEmail(),
						"Upcoming recurring expense: " + expense.getDescription(),
						message);
				notification.setLastEmailNotifiedOccurrence(occurrence);
			} catch (Exception e) {
				dbLogService.logError("scheduler", getClass().getName(), "deliver",
						"Failed email recurring expense notification for " + expense.getId(), e);
			}
		}
	}
}
