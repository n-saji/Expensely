package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.CategoryDeps;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.TransactionType;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.BudgetRepository;
import com.example.expensely_backend.repository.CategoryRepository;
import com.example.expensely_backend.repository.RecurringExpenseRepository;
import com.example.expensely_backend.repository.ReminderRepository;
import com.example.expensely_backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	@Mock
	private CategoryRepository categoryRepository;
	@Mock
	private UserService userService;
	@Mock
	private TransactionRepository transactionRepository;
	@Mock
	private RecurringExpenseRepository recurringExpenseRepository;
	@Mock
	private BudgetRepository budgetRepository;
	@Mock
	private ReminderRepository reminderRepository;

	private CategoryService categoryService;

	@BeforeEach
	void setUp() {
		categoryService = new CategoryService(categoryRepository, userService);
		ReflectionTestUtils.setField(categoryService, "transactionRepository", transactionRepository);
		ReflectionTestUtils.setField(categoryService, "recurringExpenseRepository", recurringExpenseRepository);
		ReflectionTestUtils.setField(categoryService, "budgetRepository", budgetRepository);
		ReflectionTestUtils.setField(categoryService, "reminderRepository", reminderRepository);
	}

	@Test
	void getCategoryDependenciesForUserIncludesIncomeTransactions() {
		UUID userId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();
		User user = new User();
		user.setId(userId);

		when(userService.GetActiveUserById(userId.toString())).thenReturn(user);
		when(transactionRepository.findByCategoryIdAndUserIdAndType(categoryId, userId, TransactionType.EXPENSE))
				.thenReturn(List.of(new Transaction()));
		when(transactionRepository.findByCategoryIdAndUserIdAndType(categoryId, userId, TransactionType.INCOME))
				.thenReturn(List.of(new Transaction(), new Transaction(), new Transaction()));
		when(recurringExpenseRepository.findByCategoryIdAndUserId(categoryId, userId)).thenReturn(List.of());
		when(budgetRepository.findActiveBudgetByUserIdAndCategoryIdForUpdate(userId, categoryId)).thenReturn(null);
		when(reminderRepository.countByCategoryIdAndUserIdAndDeletedAtIsNull(categoryId, userId)).thenReturn(0L);

		CategoryDeps dependencies = categoryService.getCategoryDependenciesForUser(
				userId.toString(),
				categoryId.toString()
		);

		assertEquals(1, dependencies.getExpenseCount());
		assertEquals(3, dependencies.getIncomeCount());
	}

	@Test
	void updateCategoryRejectsTypeChanges() {
		UUID categoryId = UUID.randomUUID();
		Category existingCategory = new Category();
		existingCategory.setId(categoryId);
		existingCategory.setType("expense");
		Category update = new Category();
		update.setType("income");

		when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(existingCategory));

		IllegalArgumentException exception = assertThrows(
				IllegalArgumentException.class,
				() -> categoryService.updateCategory(categoryId.toString(), update)
		);

		assertEquals("Category type cannot be changed after creation", exception.getMessage());
		verify(categoryRepository, never()).save(existingCategory);
	}

	@Test
	void deleteCategoryRemovesTransactionsBeforeItsBudget() {
		UUID userId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();

		categoryService.deleteCategoryById(categoryId.toString(), userId.toString());

		var deletionOrder = inOrder(transactionRepository, budgetRepository);
		deletionOrder.verify(transactionRepository)
				.deleteByUserIdAndCategoryIdAndType(userId, categoryId, TransactionType.EXPENSE);
		deletionOrder.verify(transactionRepository)
				.deleteByUserIdAndCategoryIdAndType(userId, categoryId, TransactionType.INCOME);
		deletionOrder.verify(budgetRepository).deleteByUserIdAndCategoryId(userId, categoryId);
		verify(categoryRepository).deleteById(categoryId);
	}
}
