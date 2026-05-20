package com.example.expensely_backend.utils;

import com.example.expensely_backend.model.Expense;
import com.example.expensely_backend.model.RecurringExpense;
import com.example.expensely_backend.repository.ExpenseRepository;
import com.example.expensely_backend.repository.RecurringExpenseRepository;
import com.example.expensely_backend.service.ExchangeRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class ExpenseCurrencyBackfill {
	private static final Logger logger = LoggerFactory.getLogger(ExpenseCurrencyBackfill.class);
	private static final String BASE_CURRENCY = "USD";

	private final ExpenseRepository expenseRepository;
	private final RecurringExpenseRepository recurringExpenseRepository;
	private final ExchangeRateService exchangeRateService;
	private final DataSource dataSource;

	public ExpenseCurrencyBackfill(ExpenseRepository expenseRepository, RecurringExpenseRepository recurringExpenseRepository, ExchangeRateService exchangeRateService, DataSource dataSource) {
		this.expenseRepository = expenseRepository;
		this.recurringExpenseRepository = recurringExpenseRepository;
		this.exchangeRateService = exchangeRateService;
		this.dataSource = dataSource;
	}

	private boolean hasColumn(String tableName, String columnName) {
		try (Connection connection = dataSource.getConnection();
		     ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
			return columns.next();
		} catch (Exception e) {
			logger.warn("Failed to inspect column {} on {}: {}", columnName, tableName, e.getMessage());
			return false;
		}
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void backfillMissingCurrencySnapshots() {
		if (!hasColumn("expenses", "currency") || !hasColumn("expenses", "base_currency_amount") ||
				!hasColumn("expenses", "exchange_rate") || !hasColumn("expenses", "base_currency")) {
			logger.info("Skipping expense currency backfill; schema columns not found yet.");
			return;
		}
		if (!hasColumn("recurring_expenses", "currency")) {
			logger.info("Skipping recurring expense currency backfill; schema column not found yet.");
		}
		List<Expense> expenses = expenseRepository.findExpensesMissingCurrencySnapshot();
		List<RecurringExpense> recurringExpenses = hasColumn("recurring_expenses", "currency")
				? recurringExpenseRepository.findByCurrencyIsNullOrCurrencyEquals("")
				: List.of();
		BigDecimal usdRate = exchangeRateService.getUsdToCurrencyRate(BASE_CURRENCY);
		if (!expenses.isEmpty()) {
			for (Expense expense : expenses) {
				expense.setCurrency(BASE_CURRENCY);
				expense.setBaseCurrency(BASE_CURRENCY);
				expense.setExchangeRate(usdRate);
				if (expense.getAmount() != null) {
					expense.setBaseCurrencyAmount(expense.getAmount().setScale(2, RoundingMode.HALF_UP));
				}
			}
			expenseRepository.saveAll(expenses);
			logger.info("Backfilled {} expenses with USD currency defaults.", expenses.size());
		}
		if (!recurringExpenses.isEmpty()) {
			for (RecurringExpense recurringExpense : recurringExpenses) {
				recurringExpense.setCurrency(BASE_CURRENCY);
			}
			recurringExpenseRepository.saveAll(recurringExpenses);
			logger.info("Backfilled {} recurring expenses with USD currency defaults.", recurringExpenses.size());
		}
	}
}
