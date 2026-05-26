package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Income;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;
import java.math.BigDecimal;

public class IncomeResponse {

	@Getter
	private final UUID id;
	@Getter
	private final BigDecimal amount;
	@Getter
	private final String description;
	@Getter
	private final LocalDateTime incomeDate;
	@Getter
	private final String categoryId;
	@Getter
	private final String categoryName;
	@Getter
	private final String userId;
	@Getter
	private final String currency;
	@Getter
	private final BigDecimal baseCurrencyAmount;
	@Getter
	private final BigDecimal displayAmount;
	@Getter
	private final String displayCurrency;

	public IncomeResponse(Income income, String displayCurrency, BigDecimal displayAmount) {
		this.id = income.getId();
		this.amount = income.getAmount();
		this.description = income.getDescription();
		this.incomeDate = income.getIncomeDate();
		this.categoryId = income.getCategory().getId().toString();
		this.categoryName = income.getCategory().getName();
		this.userId = income.getUser().getId().toString();
		this.currency = income.getCurrency();
		this.baseCurrencyAmount = income.getBaseCurrencyAmount();
		this.displayAmount = displayAmount;
		this.displayCurrency = displayCurrency;
	}
}
