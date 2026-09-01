package com.example.expensely_backend.service;

import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.TransactionType;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.CategoryRepository;
import com.example.expensely_backend.repository.ExpenseFilesRepository;
import com.example.expensely_backend.repository.TransactionRepository;
import com.example.expensely_backend.repository.TransactionRepositoryCustomImpl;
import com.example.expensely_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AllTimeChartRangeTest {

	@Test
	void expenseAllTimeStartsAtFirstExpenseMonth() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		Transaction firstExpense = transactionAt(LocalDateTime.of(2024, 8, 19, 14, 30));
		TransactionRepository repository = mock(TransactionRepository.class);
		TransactionRepositoryCustomImpl customRepository = mock(TransactionRepositoryCustomImpl.class);
		UserService userService = mock(UserService.class);
		ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);

		when(userService.GetActiveUserById(userId.toString())).thenReturn(user);
		when(exchangeRateService.getUsdToCurrencyRate("USD")).thenReturn(BigDecimal.ONE);
		when(repository.findFirstByUserIdAndTypeOrderByTransactionDateAsc(userId, TransactionType.EXPENSE))
				.thenReturn(firstExpense);
		when(customRepository.getMonthlyAmountFromTillTo(eq(userId), eq(TransactionType.EXPENSE), any(), any()))
				.thenReturn(new LinkedHashMap<>());

		ExpenseService service = new ExpenseService(
				repository,
				mock(CategoryService.class),
				userService,
				mock(BudgetService.class),
				customRepository,
				mock(ExpenseFilesRepository.class),
				mock(UserRepository.class),
				new ObjectMapper(),
				mock(CategoryRepository.class),
				mock(DbLogService.class),
				mock(RecurringExpenseService.class),
				Runnable::run,
				exchangeRateService);

		LinkedHashMap<String, Double> result =
				service.getMonthlyExpenseFromTillTo(userId.toString(), 99, globals.TimeFrame.ALL_TIME);

		ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(customRepository).getMonthlyAmountFromTillTo(
				eq(userId), eq(TransactionType.EXPENSE), startCaptor.capture(), any());
		assertEquals(LocalDateTime.of(2024, 8, 1, 0, 0), startCaptor.getValue());
		assertEquals("Aug/24", result.keySet().iterator().next());
	}

	@Test
	void incomeAllTimeStartsAtFirstIncomeMonth() {
		UUID userId = UUID.randomUUID();
		User user = user(userId);
		Transaction firstIncome = transactionAt(LocalDateTime.of(2023, 2, 7, 9, 15));
		TransactionRepository repository = mock(TransactionRepository.class);
		TransactionRepositoryCustomImpl customRepository = mock(TransactionRepositoryCustomImpl.class);
		UserService userService = mock(UserService.class);
		ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);

		when(userService.GetActiveUserById(userId.toString())).thenReturn(user);
		when(exchangeRateService.getUsdToCurrencyRate("USD")).thenReturn(BigDecimal.ONE);
		when(repository.findFirstByUserIdAndTypeOrderByTransactionDateAsc(userId, TransactionType.INCOME))
				.thenReturn(firstIncome);
		when(customRepository.getMonthlyAmountFromTillTo(eq(userId), eq(TransactionType.INCOME), any(), any()))
				.thenReturn(new LinkedHashMap<>());

		IncomeService service = new IncomeService(
				repository,
				mock(CategoryService.class),
				userService,
				customRepository,
				mock(CategoryRepository.class),
				Runnable::run,
				exchangeRateService);

		LinkedHashMap<String, Double> result =
				service.getMonthlyIncomeFromTillTo(userId.toString(), 99, globals.TimeFrame.ALL_TIME);

		ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(customRepository).getMonthlyAmountFromTillTo(
				eq(userId), eq(TransactionType.INCOME), startCaptor.capture(), any());
		assertEquals(LocalDateTime.of(2023, 2, 1, 0, 0), startCaptor.getValue());
		assertEquals("Feb/23", result.keySet().iterator().next());
	}

	private User user(UUID id) {
		User user = new User();
		user.setId(id);
		user.setCurrency("USD");
		return user;
	}

	private Transaction transactionAt(LocalDateTime date) {
		Transaction transaction = new Transaction();
		transaction.setTransactionDate(date);
		return transaction;
	}
}
