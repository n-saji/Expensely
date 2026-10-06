package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Transaction;
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
			Transaction expense,
			String displayCurrency,
			BigDecimal displayAmount
	) {
		this.id = expense.getId();
		this.amount = expense.getAmount();
		this.description = expense.getDescription();
		this.expenseDate = expense.getTransactionDate();
		this.categoryId = expense.getCategory()==null?null:expense.getCategory().getId().toString();
		this.categoryName = expense.getCategory()==null?"Loan Interest":expense.getCategory().getName();
		this.userId = expense.getUser().getId().toString();
		this.currency = expense.getCurrency();
		this.baseCurrencyAmount = expense.getBaseCurrencyAmount();
		this.displayAmount = displayAmount;
		this.displayCurrency = displayCurrency;
		this.receiptUrl = expense.getReceiptUrl();
	}

	public ExpenseResponse(Transaction payment,String displayCurrency,BigDecimal displayRate,boolean interestProjection) {
		BigDecimal baseInterest=payment.getBaseCurrencyAmount().multiply(payment.getInterestAmount())
			.divide(payment.getAmount(),2,java.math.RoundingMode.HALF_UP);
		this.id=payment.getId();this.amount=payment.getInterestAmount();
		this.description=(payment.getDescription()==null?"Debt payment":payment.getDescription())+" interest";
		this.expenseDate=payment.getTransactionDate();
		this.categoryId=payment.getCategory()==null?null:payment.getCategory().getId().toString();
		this.categoryName=payment.getCategory()==null?"Loan Interest":payment.getCategory().getName();
		this.userId=payment.getUser().getId().toString();this.currency=payment.getCurrency();
		this.baseCurrencyAmount=baseInterest;
		this.displayAmount=baseInterest.multiply(displayRate).setScale(2,java.math.RoundingMode.HALF_UP);
		this.displayCurrency=displayCurrency;this.receiptUrl=null;
	}
}
