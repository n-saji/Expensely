package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Category;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpenseOverviewTest {

	@Test
	void includesAllMonthsCategoriesAndDaysWhenThereAreNoExpenses() {
		Category food = new Category();
		food.setName("Food");

		ExpenseOverview overview = new ExpenseOverview(
				List.of(),
				List.of(),
				List.of(),
				"test-user-id",
				new ArrayList<>(),
				List.of(food),
				List.of(),
				null,
				2,
				2024,
				List.of(),
				0.0,
				List.of());

		assertEquals(12, overview.getAmountByMonth().size());
		assertEquals(0.0, overview.getAmountByMonth().get("August"));
		assertEquals(12, overview.getMonthlyCategoryExpense().size());
		assertEquals(0.0, overview.getMonthlyCategoryExpense().get("August").get("Food"));
		assertEquals(29, overview.getOverTheDaysThisMonth().size());
		assertEquals(0.0, overview.getOverTheDaysThisMonth().get("29"));
	}
}
