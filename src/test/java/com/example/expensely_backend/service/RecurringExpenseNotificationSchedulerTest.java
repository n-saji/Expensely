package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.RecurringExpense;
import com.example.expensely_backend.model.RecurringExpenseNotification;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.RecurringExpenseRepository;
import com.example.expensely_backend.utils.Mailgun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseNotificationSchedulerTest {
	@Mock private RecurringExpenseRepository recurringExpenseRepository;
	@Mock private WebSocketService webSocketService;
	@Mock private Mailgun mailgun;
	@Mock private DbLogService dbLogService;

	private RecurringExpenseNotificationScheduler scheduler;
	private RecurringExpense expense;
	private RecurringExpenseNotification notification;
	private LocalDate occurrence;

	@BeforeEach
	void setUp() {
		scheduler = new RecurringExpenseNotificationScheduler(
				recurringExpenseRepository, webSocketService, mailgun, dbLogService);
		occurrence = LocalDate.of(2026, 12, 15);

		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail("person@example.com");
		user.setEmailNotificationsEnabled(true);
		user.setInAppNotificationsEnabled(true);

		Category category = new Category();
		category.setId(UUID.randomUUID());
		category.setName("Rent");

		expense = new RecurringExpense();
		expense.setId(UUID.randomUUID());
		expense.setUser(user);
		expense.setCategory(category);
		expense.setDescription("Apartment rent");
		expense.setAmount(new BigDecimal("1200.00"));
		expense.setCurrency("USD");
		expense.setActive(true);
		expense.setNextOccurrence(occurrence);

		notification = new RecurringExpenseNotification();
		notification.setRecurringExpense(expense);
		notification.setDaysBefore(7);
		notification.setEmailEnabled(true);
		notification.setInAppEnabled(true);
		expense.setNotifications(new ArrayList<>(List.of(notification)));
	}

	@Test
	void sendsSelectedChannelsWhenOffsetBecomesDue() {
		LocalDate today = occurrence.minusDays(7);
		when(recurringExpenseRepository.findActiveWithNotifications(today)).thenReturn(List.of(expense));

		scheduler.processNotifications(today);

		ArgumentCaptor<MessageDTO> message = ArgumentCaptor.forClass(MessageDTO.class);
		verify(webSocketService).sendAlerts(eq(expense.getUser()), message.capture());
		verify(mailgun).sendSimpleMessage(
				eq("person@example.com"),
				eq("Upcoming recurring expense: Apartment rent"),
				any(String.class));
		assertEquals(occurrence, notification.getLastInAppNotifiedOccurrence());
		assertEquals(occurrence, notification.getLastEmailNotifiedOccurrence());
		verify(recurringExpenseRepository).saveAll(List.of(expense));
	}

	@Test
	void doesNotSendSameOffsetTwiceForOneOccurrence() {
		LocalDate today = occurrence.minusDays(1);
		notification.setLastEmailNotifiedOccurrence(occurrence);
		notification.setLastInAppNotifiedOccurrence(occurrence);
		when(recurringExpenseRepository.findActiveWithNotifications(today)).thenReturn(List.of(expense));

		scheduler.processNotifications(today);

		verify(webSocketService, never()).sendAlerts(any(), any());
		verify(mailgun, never()).sendSimpleMessage(any(), any(), any());
	}
}
