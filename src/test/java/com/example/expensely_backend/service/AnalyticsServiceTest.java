package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MonthlyAnalyticsResponse;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {

	private ExpenseRepository expenseRepository;
	private IncomeRepository incomeRepository;
	private BudgetRepository budgetRepository;
	private UserService userService;
	private ExchangeRateService exchangeRateService;
	private AnalyticsService analyticsService;

	private User testUser;
	private UUID testUserId;

	@BeforeEach
	void setUp() {
		expenseRepository = mock(ExpenseRepository.class);
		incomeRepository = mock(IncomeRepository.class);
		budgetRepository = mock(BudgetRepository.class);
		userService = mock(UserService.class);
		exchangeRateService = mock(ExchangeRateService.class);

		analyticsService = new AnalyticsService(
				expenseRepository,
				incomeRepository,
				budgetRepository,
				userService,
				exchangeRateService
		);

		testUserId = UUID.randomUUID();
		testUser = new User();
		testUser.setId(testUserId);
		testUser.setEmail("user@example.com");
		testUser.setCurrency("EUR"); // Test non-USD preferred currency
	}

	@Test
	void getMonthlyAnalyticsExpenseSuccess() {
		// Mock UserService
		when(userService.GetActiveUserById(testUserId.toString())).thenReturn(testUser);

		// Mock ExchangeRateService
		// Let's assume 1 USD = 0.9 EUR
		when(exchangeRateService.getUsdToCurrencyRate("EUR")).thenReturn(BigDecimal.valueOf(0.9));

		// Setup categories
		Category catFood = new Category();
		catFood.setId(UUID.randomUUID());
		catFood.setName("Food");
		catFood.setColor("#FF5733");
		catFood.setIcon("FastFood");

		// Setup current month expenses
		Expense e1 = new Expense();
		e1.setId(UUID.randomUUID());
		e1.setUser(testUser);
		e1.setCategory(catFood);
		e1.setAmount(BigDecimal.valueOf(100));
		e1.setCurrency("USD");
		e1.setBaseCurrencyAmount(BigDecimal.valueOf(100)); // USD
		e1.setExpenseDate(LocalDateTime.of(2026, 6, 15, 12, 0));

		Expense e2 = new Expense();
		e2.setId(UUID.randomUUID());
		e2.setUser(testUser);
		e2.setCategory(catFood);
		e2.setAmount(BigDecimal.valueOf(50));
		e2.setCurrency("USD");
		e2.setBaseCurrencyAmount(BigDecimal.valueOf(50)); // USD
		e2.setExpenseDate(LocalDateTime.of(2026, 6, 16, 15, 0));

		List<Expense> currentExpenses = Arrays.asList(e1, e2);
		when(expenseRepository.findByUserIdAndTimeFrameAsc(eq(testUserId), any(), any()))
				.thenReturn(currentExpenses) // For current month
				.thenReturn(Collections.emptyList()) // For prev month
				.thenReturn(Collections.emptyList()); // For last year

		// Setup historical data mocked response
		List<Object[]> histRaw = new ArrayList<>();
		histRaw.add(new Object[]{2025, 200.0, 3L});
		histRaw.add(new Object[]{2026, 150.0, 2L});
		when(expenseRepository.findHistoricalMonthlyDataExpense(testUserId, 6)).thenReturn(histRaw);

		// Setup budget data
		Budget budget = new Budget();
		budget.setId(UUID.randomUUID());
		budget.setUser(testUser);
		budget.setCategory(catFood);
		budget.setStartDate(LocalDate.of(2026, 6, 1));
		budget.setEndDate(LocalDate.of(2026, 6, 30));
		budget.setBaseCurrencyAmount(BigDecimal.valueOf(200)); // USD limit
		budget.setActive(true);

		when(budgetRepository.findActiveBudgetsByUserId(testUserId)).thenReturn(Collections.singletonList(budget));

		// Call service
		MonthlyAnalyticsResponse response = analyticsService.getMonthlyAnalytics(testUserId.toString(), 2026, 6, "expense");

		// Assertions
		assertNotNull(response);
		assertNotNull(response.getSummary());

		// 150 USD total = 135 EUR
		assertEquals(BigDecimal.valueOf(135.0).setScale(2), response.getSummary().getTotalAmount());
		assertEquals(2, response.getSummary().getTotalTransactions());
		assertEquals(BigDecimal.valueOf(67.5).setScale(2), response.getSummary().getAverageTransactionAmount());
		assertEquals(BigDecimal.valueOf(90.0).setScale(2), response.getSummary().getHighestTransactionAmount()); // 100 USD = 90 EUR
		assertEquals("2026-06-15", response.getSummary().getHighestSpendingEarningDay());
		assertEquals("2026-06-16", response.getSummary().getLowestSpendingEarningDay());

		// Category analytics verification
		assertNotNull(response.getCategoryAnalytics());
		assertEquals(1, response.getCategoryAnalytics().size());
		MonthlyAnalyticsResponse.CategoryAnalytics catAnalytic = response.getCategoryAnalytics().get(0);
		assertEquals("Food", catAnalytic.getCategoryName());
		assertEquals(BigDecimal.valueOf(135.0).setScale(2), catAnalytic.getTotalAmount());
		assertEquals(BigDecimal.valueOf(100.0).setScale(2), catAnalytic.getPercentageOfTotal());

		// Budget analytics verification
		assertNotNull(response.getBudgetAnalytics());
		// limit = 200 USD = 180 EUR
		assertEquals(BigDecimal.valueOf(180.0).setScale(2), response.getBudgetAnalytics().getBudgetAmount());
		// spent = 150 USD = 135 EUR
		assertEquals(BigDecimal.valueOf(135.0).setScale(2), response.getBudgetAnalytics().getAmountSpent());
		assertEquals(BigDecimal.valueOf(45.0).setScale(2), response.getBudgetAnalytics().getRemainingAmount());
		assertEquals(BigDecimal.valueOf(75.0).setScale(2), response.getBudgetAnalytics().getBudgetUtilizationPercentage());
		assertFalse(response.getBudgetAnalytics().isOverBudget());

		// Historical data verification
		assertNotNull(response.getMonthComparisons().getHistoricalData());
		assertEquals(2, response.getMonthComparisons().getHistoricalData().size());
		assertEquals(2025, response.getMonthComparisons().getHistoricalData().get(0).getYear());
		// 200 USD * 0.9 = 180 EUR
		assertEquals(BigDecimal.valueOf(180.0).setScale(2), response.getMonthComparisons().getHistoricalData().get(0).getTotalAmount());
	}

	@Test
	void getMonthlyAnalyticsUserNotFound() {
		when(userService.GetActiveUserById("nonexistent")).thenReturn(null);

		assertThrows(IllegalArgumentException.class, () ->
				analyticsService.getMonthlyAnalytics("nonexistent", 2026, 6, "expense")
		);
	}
}
