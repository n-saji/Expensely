package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.TransactionType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class TransactionResponse {

	@Getter
	private final UUID id;
	@Getter
	private final BigDecimal amount;
	@Getter
	private final String description;
	@Getter
	private final LocalDateTime transactionDate;
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
	@Getter
	private final TransactionType type;

	public TransactionResponse(
			Transaction transaction,
			String displayCurrency,
			BigDecimal displayAmount
	) {
		this.id = transaction.getId();
		this.amount = transaction.getAmount();
		this.description = transaction.getDescription();
		this.transactionDate = transaction.getTransactionDate();
		this.categoryId = transaction.getCategory().getId().toString();
		this.categoryName = transaction.getCategory().getName();
		this.userId = transaction.getUser().getId().toString();
		this.currency = transaction.getCurrency();
		this.baseCurrencyAmount = transaction.getBaseCurrencyAmount();
		this.displayAmount = displayAmount;
		this.displayCurrency = displayCurrency;
		this.receiptUrl = transaction.getReceiptUrl();
		this.type = transaction.getType();
	}
}
