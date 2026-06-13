package com.example.expensely_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@Builder
@NoArgsConstructor
public class CategoryDeps {
	private int budgetCount;
	private int expenseCount;
	private int incomeCount;
	private int recurringExpenseCount;
}
