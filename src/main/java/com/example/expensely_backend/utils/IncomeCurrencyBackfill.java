package com.example.expensely_backend.utils;

import com.example.expensely_backend.model.Income;
import com.example.expensely_backend.repository.IncomeRepository;
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
public class IncomeCurrencyBackfill {
	private static final Logger logger = LoggerFactory.getLogger(IncomeCurrencyBackfill.class);
	private static final String BASE_CURRENCY = "USD";

	private final IncomeRepository incomeRepository;
	private final ExchangeRateService exchangeRateService;
	private final DataSource dataSource;

	public IncomeCurrencyBackfill(IncomeRepository incomeRepository,
	                              ExchangeRateService exchangeRateService,
	                              DataSource dataSource) {
		this.incomeRepository = incomeRepository;
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
		if (!hasColumn("incomes", "currency") || !hasColumn("incomes", "base_currency_amount")
				|| !hasColumn("incomes", "exchange_rate") || !hasColumn("incomes", "base_currency")) {
			logger.info("Skipping income currency backfill; schema columns not found yet.");
			return;
		}

		List<Income> incomes = incomeRepository.findIncomesMissingCurrencySnapshot();
		BigDecimal usdRate = exchangeRateService.getUsdToCurrencyRate(BASE_CURRENCY);

		if (!incomes.isEmpty()) {
			for (Income income : incomes) {
				income.setCurrency(BASE_CURRENCY);
				income.setBaseCurrency(BASE_CURRENCY);
				income.setExchangeRate(usdRate);
				if (income.getAmount() != null) {
					income.setBaseCurrencyAmount(income.getAmount().setScale(2, RoundingMode.HALF_UP));
				}
			}
			incomeRepository.saveAll(incomes);
			logger.info("Backfilled {} incomes with USD currency defaults.", incomes.size());
		}
	}
}

