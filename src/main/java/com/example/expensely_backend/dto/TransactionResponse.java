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
	@Getter private final String accountId;
	@Getter private final String destinationAccountId;
	@Getter private final String debtId;
	@Getter private final String savingsGoalId;
	@Getter private final String investmentAccountId;
	@Getter private final BigDecimal principalAmount;
	@Getter private final BigDecimal interestAmount;
	@Getter private final String notes;
	@Getter private final String counterpartyName;

	public TransactionResponse(
			Transaction transaction,
			String displayCurrency,
			BigDecimal displayAmount
	) {
		this.id = transaction.getId();
		this.amount = transaction.getAmount();
		this.description = transaction.getDescription();
		this.transactionDate = transaction.getTransactionDate();
		this.categoryId = transaction.getCategory() == null ? null : transaction.getCategory().getId().toString();
		this.categoryName = transaction.getCategory() == null ? null : transaction.getCategory().getName();
		this.userId = transaction.getUser().getId().toString();
		this.currency = transaction.getCurrency();
		this.baseCurrencyAmount = transaction.getBaseCurrencyAmount();
		this.displayAmount = displayAmount;
		this.displayCurrency = displayCurrency;
		this.receiptUrl = transaction.getReceiptUrl();
		this.type = transaction.getType();
		this.accountId = transaction.getAccount() == null ? null : transaction.getAccount().getId().toString();
		this.destinationAccountId = transaction.getDestinationAccount() == null ? null : transaction.getDestinationAccount().getId().toString();
		this.debtId = transaction.getDebt() == null ? null : transaction.getDebt().getId().toString();
		this.savingsGoalId = transaction.getSavingsGoal() == null ? null : transaction.getSavingsGoal().getId().toString();
		this.investmentAccountId = transaction.getInvestmentAccount() == null ? null : transaction.getInvestmentAccount().getId().toString();
		this.principalAmount = transaction.getPrincipalAmount();
		this.interestAmount = transaction.getInterestAmount();
		this.notes = transaction.getNotes();
		this.counterpartyName = transaction.getDebt()!=null ? transaction.getDebt().getName() :
			transaction.getSavingsGoal()!=null ? transaction.getSavingsGoal().getName() :
			transaction.getInvestmentAccount()!=null ? transaction.getInvestmentAccount().getName() :
			transaction.getDestinationAccount()!=null ? transaction.getDestinationAccount().getName() : null;
	}
}
