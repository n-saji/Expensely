package com.example.expensely_backend.service;

import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.TransactionType;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.TransactionRepository;
import com.example.expensely_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TransactionServiceTest {

	@Mock
	private TransactionRepository transactionRepository;
	@Mock
	private CategoryService categoryService;
	@Mock
	private UserService userService;
	@Mock
	private UserRepository userRepository;
	@Mock
	private BudgetService budgetService;
	@Mock
	private DbLogService dbLogService;
	@Mock
	private ExchangeRateService exchangeRateService;

	@InjectMocks
	private TransactionService transactionService;

	private User user;
	private Category cat1;
	private Category cat2;
	private UUID cat1Id;
	private UUID cat2Id;
	private UUID userId;
	private LocalDateTime now;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);

		userId = UUID.randomUUID();
		user = new User();
		user.setId(userId);
		user.setCurrency("USD");

		cat1Id = UUID.randomUUID();
		cat1 = new Category();
		cat1.setId(cat1Id);
		cat1.setName("Food");

		cat2Id = UUID.randomUUID();
		cat2 = new Category();
		cat2.setId(cat2Id);
		cat2.setName("Transport");

		now = LocalDateTime.now();

		when(exchangeRateService.normalizeDisplayAmount(any())).thenAnswer(inv -> inv.getArgument(0));
		when(exchangeRateService.getUsdToCurrencyRate(any())).thenReturn(BigDecimal.ONE);
	}

	@Test
	void updateTransaction_CategoryChange_UpdatesOldAndNewBudgetsCorrectly() {
		Transaction oldT = new Transaction();
		oldT.setId(UUID.randomUUID());
		oldT.setUser(user);
		oldT.setCategory(cat1);
		oldT.setAmount(new BigDecimal("100.00"));
		oldT.setBaseCurrencyAmount(new BigDecimal("100.00"));
		oldT.setExchangeRate(BigDecimal.ONE);
		oldT.setCurrency("USD");
		oldT.setTransactionDate(now);
		oldT.setType(TransactionType.EXPENSE);

		when(transactionRepository.findById(oldT.getId())).thenReturn(Optional.of(oldT));
		when(categoryService.getCategoryById(cat2Id.toString())).thenReturn(cat2);
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Transaction updateReq = new Transaction();
		updateReq.setId(oldT.getId());
		updateReq.setCategory(cat2); // Changing to Category 2
		updateReq.setAmount(new BigDecimal("100.00")); // Amount unchanged

		transactionService.updateTransaction(updateReq);

		// Old budget for cat1 should be reduced by 100.00 (-100.00)
		verify(budgetService).updateBudgetAmountByUserIdAndCategoryId(
				eq(userId.toString()),
				eq(cat1Id.toString()),
				eq(new BigDecimal("100.00").negate()),
				eq(now)
		);

		// New budget for cat2 should be increased by full base amount 100.00 (not diff of 0)
		verify(budgetService).updateBudgetAmountByUserIdAndCategoryId(
				eq(userId.toString()),
				eq(cat2Id.toString()),
				eq(new BigDecimal("100.00")),
				eq(now)
		);
	}

	@Test
	void updateTransaction_SameCategoryAmountChange_UpdatesBudgetWithDiff() {
		Transaction oldT = new Transaction();
		oldT.setId(UUID.randomUUID());
		oldT.setUser(user);
		oldT.setCategory(cat1);
		oldT.setAmount(new BigDecimal("100.00"));
		oldT.setBaseCurrencyAmount(new BigDecimal("100.00"));
		oldT.setExchangeRate(BigDecimal.ONE);
		oldT.setCurrency("USD");
		oldT.setTransactionDate(now);
		oldT.setType(TransactionType.EXPENSE);

		when(transactionRepository.findById(oldT.getId())).thenReturn(Optional.of(oldT));
		when(categoryService.getCategoryById(cat1Id.toString())).thenReturn(cat1);
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Transaction updateReq = new Transaction();
		updateReq.setId(oldT.getId());
		updateReq.setCategory(cat1);
		updateReq.setAmount(new BigDecimal("150.00")); // Amount increased by 50

		transactionService.updateTransaction(updateReq);

		// Same category budget should be updated by diff (150.00 - 100.00 = 50.00)
		verify(budgetService, times(1)).updateBudgetAmountByUserIdAndCategoryId(
				eq(userId.toString()),
				eq(cat1Id.toString()),
				eq(new BigDecimal("50.00")),
				eq(now)
		);
	}

	@Test
	void updateTransaction_DateChange_UpdatesOldAndNewBudgetsCorrectly() {
		LocalDateTime oldDate = now;
		LocalDateTime newDate = now.plusDays(5);

		Transaction oldT = new Transaction();
		oldT.setId(UUID.randomUUID());
		oldT.setUser(user);
		oldT.setCategory(cat1);
		oldT.setAmount(new BigDecimal("100.00"));
		oldT.setBaseCurrencyAmount(new BigDecimal("100.00"));
		oldT.setExchangeRate(BigDecimal.ONE);
		oldT.setCurrency("USD");
		oldT.setTransactionDate(oldDate);
		oldT.setType(TransactionType.EXPENSE);

		when(transactionRepository.findById(oldT.getId())).thenReturn(Optional.of(oldT));
		when(categoryService.getCategoryById(cat1Id.toString())).thenReturn(cat1);
		when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Transaction updateReq = new Transaction();
		updateReq.setId(oldT.getId());
		updateReq.setTransactionDate(newDate); // Date changed

		transactionService.updateTransaction(updateReq);

		// Old budget for cat1 at oldDate should be reduced by 100.00 (-100.00)
		verify(budgetService).updateBudgetAmountByUserIdAndCategoryId(
				eq(userId.toString()),
				eq(cat1Id.toString()),
				eq(new BigDecimal("100.00").negate()),
				eq(oldDate)
		);

		// New budget for cat1 at newDate should be increased by full base amount 100.00
		verify(budgetService).updateBudgetAmountByUserIdAndCategoryId(
				eq(userId.toString()),
				eq(cat1Id.toString()),
				eq(new BigDecimal("100.00")),
				eq(newDate)
		);
	}
}
