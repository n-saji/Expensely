package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.CategoryDeps;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Expense;
import com.example.expensely_backend.model.RecurringExpense;
import com.example.expensely_backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CategoryService {
	private static final Pattern ICON_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
	private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

	private final CategoryRepository categoryRepository;
	private final UserService userService;

	@Autowired
	private ExpenseRepository expenseRepository;
	@Autowired
	private IncomeRepository incomeRepository;
	@Autowired
	private RecurringExpenseRepository recurringExpenseRepository;
	@Autowired
	private BudgetRepository budgetRepository;


	public CategoryService(CategoryRepository categoryRepository, UserService userService
	) {
		this.categoryRepository = categoryRepository;
		this.userService = userService;

	}

	public Category save(Category category) {
		if (category.getUser() == null || category.getUser().getId() == null) {
			throw new IllegalArgumentException("User must be provided");
		}
		var user = userService.GetActiveUserById(category.getUser().getId().toString());
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		category.setUser(user);

		if (category.getIcon() == null || category.getIcon().isBlank()) {
			category.setIcon(globals.DEFAULT_CATEGORY_ICON);
		} else if (!ICON_PATTERN.matcher(category.getIcon()).matches()) {
			throw new IllegalArgumentException("Unsupported icon identifier");
		}

		if (category.getColor() == null || category.getColor().isBlank()) {
			category.setColor(globals.DEFAULT_CATEGORY_COLOR);
		} else if (!HEX_COLOR_PATTERN.matcher(category.getColor()).matches()) {
			throw new IllegalArgumentException("Color must be a hex value like #RRGGBB");
		}

		return categoryRepository.save(category);
	}

	public Category getCategoryById(String id) {
		return categoryRepository.findById(UUID.fromString(id))
				.orElseThrow(() -> new IllegalArgumentException("Category not found"));
	}

//    retaining category, no need to delete
//    public void deleteCategoryById(String id) {
//        try {
//           check if category referenced by expense / budget
//            if (categoryRepository.existsById(UUID.fromString(id))) {
//                if (!expenseService.getExpensesByCategoryId(id).isEmpty()) {
//                    throw new IllegalArgumentException("Cannot delete category, it is referenced by existing expenses");
//                }
//                if (!budgetService.getBudgetByCategoryId(id).isEmpty()) {
//                    throw new IllegalArgumentException("Cannot delete category, it is referenced by existing budgets");
//                }
//            }
//            categoryRepository.deleteByIdHard(UUID.fromString(id));
//        } catch (Exception e) {
//            throw new IllegalArgumentException("Error deleting category: " + e.getMessage());
//        }
//    }

	public Iterable<Category> getAllCategories() {
		var categories = categoryRepository.findAll();
		if (!categories.iterator().hasNext()) {
			throw new IllegalArgumentException("No categories found");
		}
		return categories;
	}

	public Iterable<Category> getCategoriesByUserId(String userId, String type) {
		var user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		List<Category> categories;
		if (type == null || type.isEmpty()) {
			categories = categoryRepository.findByUserId(user.getId());
		} else {
			categories = categoryRepository.findByUserAndType(user, type);
		}
		categories.forEach((category1) -> {
			category1.setUser(null);
		});
		return categories;
	}

	public void updateCategory(String c_id, Category category) {
		if (c_id == null || c_id.isEmpty()) {
			throw new IllegalArgumentException("Category ID must not be null");
		}
		var existingCategory = getCategoryById(c_id);
		if (category.getName() != null && !category.getName().isEmpty()) {
			existingCategory.setName(category.getName());
		}
		if (category.getType() != null && !category.getType().isEmpty()) {
			existingCategory.setType(category.getType());
		}
		if (category.getIcon() != null && !category.getIcon().isBlank()) {
			if (!ICON_PATTERN.matcher(category.getIcon()).matches()) {
				throw new IllegalArgumentException("Unsupported icon identifier");
			}
			existingCategory.setIcon(category.getIcon());
		}
		if (category.getColor() != null && !category.getColor().isBlank()) {
			if (!HEX_COLOR_PATTERN.matcher(category.getColor()).matches()) {
				throw new IllegalArgumentException("Color must be a hex value like #RRGGBB");
			}
			existingCategory.setColor(category.getColor());
		}
		try {
			categoryRepository.save(existingCategory);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error updating category: " + e.getMessage());
		}

	}

	public CategoryDeps getCategoryDependenciesForUser(String userId, String categoryId) {
		if (userId == null || userId.isEmpty() || categoryId == null || categoryId.isEmpty()) {
			throw new IllegalArgumentException("User ID and Category ID must not be null or empty");
		}
		var user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}

		List<Expense> expenses = expenseRepository.findByCategoryIdAndUserId(UUID.fromString(categoryId), user.getId());
		List<RecurringExpense> rExpenses =
				recurringExpenseRepository.findByCategoryIdAndUserId(UUID.fromString(categoryId), user.getId());
		Budget budgets =
				budgetRepository.findActiveBudgetByUserIdAndCategoryIdForUpdate(UUID.fromString(categoryId), user.getId());
		return CategoryDeps.builder()
				.expenseCount(expenses.size())
				.recurringExpenseCount(rExpenses.size())
				.budgetCount(budgets == null ? 0 : 1).build();

	}


}