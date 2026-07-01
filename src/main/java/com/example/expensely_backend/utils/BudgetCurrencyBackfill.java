package com.example.expensely_backend.utils;

import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.repository.BudgetRepository;
import com.example.expensely_backend.service.ExchangeRateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;

@Component
public class BudgetCurrencyBackfill {
	private static final Logger logger = LoggerFactory.getLogger(BudgetCurrencyBackfill.class);
	private static final String BASE_CURRENCY = "USD";

	private final BudgetRepository budgetRepository;
	private final ExchangeRateService exchangeRateService;
	private final DataSource dataSource;

	public BudgetCurrencyBackfill(BudgetRepository budgetRepository, ExchangeRateService exchangeRateService, DataSource dataSource) {
		this.budgetRepository = budgetRepository;
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
		if (!hasColumn("budgets", "currency") || !hasColumn("budgets",
				"base_currency_amount") ||
				!hasColumn("budgets", "exchange_rate")) {
			logger.info("Skipping budgets currency backfill; schema columns not found yet.");
			return;
		}
		List<Budget> budgets =
				budgetRepository.findBudgetMissingCurrencySnapshot();

		BigDecimal usdRate = exchangeRateService.getUsdToCurrencyRate(BASE_CURRENCY);
		if (!budgets.isEmpty()) {
			for (Budget b : budgets) {
				b.setCurrency(BASE_CURRENCY);
				b.setExchangeRate(usdRate);
				if (b.getAmountLimit() != null) {
					b.setBaseCurrencyAmount(b.getAmountLimit().setScale(2,
							RoundingMode.HALF_UP));
				}
			}
			budgetRepository.saveAll(budgets);
			logger.info("Backfilled {} budgets with USD currency defaults.",
					budgets.size());
		}

	}
}
