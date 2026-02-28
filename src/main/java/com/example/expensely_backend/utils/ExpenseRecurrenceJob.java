package com.example.expensely_backend.utils;

import com.example.expensely_backend.model.Expense;
import com.example.expensely_backend.model.RecurringExpense;
import com.example.expensely_backend.repository.ExpenseRepository;
import com.example.expensely_backend.repository.RecurringExpenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ExpenseRecurrenceJob {
	private static final Logger logger = LoggerFactory.getLogger(ExpenseRecurrenceJob.class);

	private final RecurringExpenseRepository recurringExpenseRepository;
	private final ExpenseRepository expenseRepository;

	public ExpenseRecurrenceJob(RecurringExpenseRepository recurringExpenseRepository, ExpenseRepository expenseRepository) {
		this.recurringExpenseRepository = recurringExpenseRepository;
		this.expenseRepository = expenseRepository;
	}

	/**
	 * Runs every day at 00:00 (midnight) server time.
	 * Scans active recurring expenses whose nextOccurrence == today, creates Expense rows,
	 * advances nextOccurrence according to recurrence, and saves changes.
	 */
	@Scheduled(cron = "0 0 0 * * *")
	@Transactional
	public void run() {
		LocalDate today = LocalDate.now();
		logger.info("Starting ExpenseRecurrenceJob for date={}", today);

		List<RecurringExpense> recurringExpenses = recurringExpenseRepository.findByActiveTrueAndNextOccurrence(today);

		int createdCount = 0;
		int skippedCount = 0;
		for (RecurringExpense rec : recurringExpenses) {
			if (rec == null) continue;

			try {
				// Create expense
				Expense expense = new Expense();
				expense.setUser(rec.getUser());
				expense.setCategory(rec.getCategory());
				expense.setAmount(rec.getAmount());
				expense.setDescription(rec.getDescription());
				expense.setExpenseDate(LocalDateTime.now());
				expenseRepository.save(expense);

				// Advance nextOccurrence according to recurrence
				switch (rec.getRecurrence()) {
					case DAILY -> rec.setNextOccurrence(today.plusDays(1));
					case WEEKLY -> rec.setNextOccurrence(today.plusWeeks(1));
					case MONTHLY -> rec.setNextOccurrence(today.plusMonths(1));
					case YEARLY -> rec.setNextOccurrence(today.plusYears(1));
					default ->
							throw new IllegalArgumentException("Unknown recurrence type: " + rec.getRecurrence());
				}

				recurringExpenseRepository.save(rec);
				createdCount++;
				logger.info("Created expense from recurring id={} expenseId={}", rec.getId(), expense.getId());
			} catch (Exception e) {
				logger.error("Failed to process recurring expense id={}: {}", rec.getId(), e.getMessage(), e);
			}
		}

		logger.info("ExpenseRecurrenceJob complete. created={}, skipped={}", createdCount, skippedCount);
	}

}
