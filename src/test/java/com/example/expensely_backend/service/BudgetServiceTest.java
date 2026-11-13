package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.handler.AlertHandler;
import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.TransactionType;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.BudgetRepository;
import com.example.expensely_backend.repository.CategoryRepository;
import com.example.expensely_backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

	@Mock private CategoryRepository categoryRepository;
	@Mock private UserService userService;
	@Mock private TransactionRepository transactionRepository;
	@Mock private AlertHandler alertHandler;
	@Mock private BudgetRepository budgetRepository;
	@Mock private DbLogService dbLogService;
	@Mock private ExchangeRateService exchangeRateService;

	private BudgetService budgetService;
	private User user;
	private Category category;

	@BeforeEach
	void setUp() {
		budgetService = new BudgetService(categoryRepository, userService, transactionRepository,
				alertHandler, budgetRepository, dbLogService, exchangeRateService);

		user = new User();
		user.setId(UUID.randomUUID());
		user.setCurrency("USD");
		user.setInAppNotificationsEnabled(true);

		category = new Category();
		category.setId(UUID.randomUUID());
		category.setName("Food");
		category.setUser(user);
	}

	@Test
	void saveWithExistingExpensesTriggersThresholdNotification() {
		LocalDate start = LocalDate.now().withDayOfMonth(1);
		LocalDate end = start.plusMonths(1).minusDays(1);
		Budget budget = budget(start, end, "100.00");

		Transaction existingExpense = new Transaction();
		existingExpense.setCategory(category);
		existingExpense.setType(TransactionType.EXPENSE);
		existingExpense.setBaseCurrencyAmount(new BigDecimal("80.00"));
		existingExpense.setTransactionDate(start.atStartOfDay());

		when(userService.GetActiveUserById(user.getId().toString())).thenReturn(user);
		when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
		when(budgetRepository.existsByUserIdAndCategoryIdAndIsActiveTrue(user.getId(), category.getId()))
				.thenReturn(false);
		when(transactionRepository.findByUserIdAndTypeAndTimeFrameAsc(
				eq(user.getId()), eq(TransactionType.EXPENSE), any(LocalDateTime.class), any(LocalDateTime.class)))
				.thenReturn(List.of(existingExpense));
		when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Budget saved = budgetService.save(user.getId().toString(), budget);

		assertTrue(saved.isThreshold50Crossed());
		assertTrue(saved.isThreshold75Crossed());
		assertFalse(saved.isThreshold100Crossed());
		ArgumentCaptor<MessageDTO> message = ArgumentCaptor.forClass(MessageDTO.class);
		verify(alertHandler).sendAlert(eq(user.getId()), message.capture());
		assertTrue(message.getValue().getMessage().contains("80%"));
	}

	@Test
	void deletingExpenseClearsThresholdFlagsUsedByBanner() {
		LocalDate start = LocalDate.now().withDayOfMonth(1);
		Budget budget = budget(start, start.plusMonths(1).minusDays(1), "100.00");
		budget.setAmountSpent(new BigDecimal("80.00"));
		budget.setThreshold50Crossed(true);
		budget.setThreshold75Crossed(true);

		when(budgetRepository.findActiveBudgetByUserIdAndCategoryIdForUpdate(user.getId(), category.getId()))
				.thenReturn(budget);
		when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

		budgetService.updateBudgetAmountByUserIdAndCategoryId(
				user.getId().toString(), category.getId().toString(), new BigDecimal("-50.00"), start.atStartOfDay());

		assertFalse(budget.isThreshold50Crossed());
		assertFalse(budget.isThreshold75Crossed());
		assertFalse(budget.isThreshold100Crossed());
		verify(alertHandler, never()).sendAlert(any(), any());
	}

	private Budget budget(LocalDate start, LocalDate end, String amountLimit) {
		Budget budget = new Budget();
		budget.setUser(user);
		budget.setCategory(category);
		budget.setAmountLimit(new BigDecimal(amountLimit));
		budget.setAmountSpent(BigDecimal.ZERO);
		budget.setCurrency(globals.BASE_CURRENCY);
		budget.setBaseCurrencyAmount(new BigDecimal(amountLimit));
		budget.setExchangeRate(BigDecimal.ONE);
		budget.setPeriod(globals.Period.MONTHLY);
		budget.setStartDate(start);
		budget.setEndDate(end);
		budget.setActive(true);
		return budget;
	}
}
