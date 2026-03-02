package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Income;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

public class IncomeResponse {

	@Getter
	private final UUID id;
	@Getter
	private final double amount;
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

	public IncomeResponse(Income income) {
		this.id = income.getId();
		this.amount = income.getAmount().doubleValue();
		this.description = income.getDescription();
		this.incomeDate = income.getIncomeDate();
		this.categoryId = income.getCategory().getId().toString();
		this.categoryName = income.getCategory().getName();
		this.userId = income.getUser().getId().toString();
		this.currency = income.getUser().getCurrency();
	}
}

