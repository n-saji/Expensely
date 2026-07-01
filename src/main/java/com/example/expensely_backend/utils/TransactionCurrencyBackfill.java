package com.example.expensely_backend.utils;

import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.TransactionType;
import com.example.expensely_backend.model.RecurringExpense;
import com.example.expensely_backend.repository.TransactionRepository;
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
public class TransactionCurrencyBackfill {
	private static final Logger logger = LoggerFactory.getLogger(TransactionCurrencyBackfill.class);
	private static final String BASE_CURRENCY = "USD";

	private final TransactionRepository transactionRepository;
	private final RecurringExpenseRepository recurringExpenseRepository;
	private final ExchangeRateService exchangeRateService;
	private final DataSource dataSource;

	public TransactionCurrencyBackfill(TransactionRepository transactionRepository,
	                                   RecurringExpenseRepository recurringExpenseRepository,
	                                   ExchangeRateService exchangeRateService,
	                                   DataSource dataSource) {
		this.transactionRepository = transactionRepository;
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
		if (!hasColumn("transactions", "currency") || !hasColumn("transactions", "base_currency_amount") ||
				!hasColumn("transactions", "exchange_rate") || !hasColumn("transactions", "base_currency")) {
			logger.info("Skipping transaction currency backfill; schema columns not found yet.");
			return;
		}

		backfillTransactions(TransactionType.EXPENSE);
		backfillTransactions(TransactionType.INCOME);
		backfillRecurringExpenses();
	}

	private void backfillTransactions(TransactionType type) {
		List<Transaction> transactions = transactionRepository.findTransactionsMissingCurrencySnapshot(type);
		BigDecimal usdRate = exchangeRateService.getUsdToCurrencyRate(BASE_CURRENCY);

		if (!transactions.isEmpty()) {
			for (Transaction transaction : transactions) {
				transaction.setCurrency(BASE_CURRENCY);
				transaction.setBaseCurrency(BASE_CURRENCY);
				transaction.setExchangeRate(usdRate);
				if (transaction.getAmount() != null) {
					transaction.setBaseCurrencyAmount(transaction.getAmount().setScale(2, RoundingMode.HALF_UP));
				}
			}
			transactionRepository.saveAll(transactions);
			logger.info("Backfilled {} {} transactions with USD currency defaults.", transactions.size(), type);
		}
	}

	private void backfillRecurringExpenses() {
		if (!hasColumn("recurring_expenses", "currency")) {
			logger.info("Skipping recurring expense currency backfill; schema column not found yet.");
			return;
		}

		List<RecurringExpense> recurringExpenses = recurringExpenseRepository.findByCurrencyIsNullOrCurrencyEquals("");
		if (!recurringExpenses.isEmpty()) {
			for (RecurringExpense recurringExpense : recurringExpenses) {
				recurringExpense.setCurrency(BASE_CURRENCY);
			}
			recurringExpenseRepository.saveAll(recurringExpenses);
			logger.info("Backfilled {} recurring expenses with USD currency defaults.", recurringExpenses.size());
		}
	}
}
