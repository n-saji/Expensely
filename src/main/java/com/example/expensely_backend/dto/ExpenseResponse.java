package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Expense;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class ExpenseResponse {

	@Getter
	private final UUID id;
	@Getter
	private final BigDecimal amount;
	@Getter
	private final String description;
	@Getter
	private final LocalDateTime expenseDate;
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

	@Getter
	private final String receiptUrl;

	public ExpenseResponse(
			Expense expense,
			String displayCurrency,
			BigDecimal displayAmount
	) {
		this.id = expense.getId();
		this.amount = expense.getAmount();
		this.description = expense.getDescription();
		this.expenseDate = expense.getExpenseDate();
		this.categoryId = expense.getCategory().getId().toString();
		this.categoryName = expense.getCategory().getName();
		this.userId = expense.getUser().getId().toString();
		this.currency = expense.getCurrency();
		this.baseCurrencyAmount = expense.getBaseCurrencyAmount();
		this.displayAmount = displayAmount;
		this.displayCurrency = displayCurrency;
		this.receiptUrl = expense.getReceiptUrl();
	}


}
