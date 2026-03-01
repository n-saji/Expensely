package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.handler.AlertHandler;
import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Expense;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.BudgetRepository;
import com.example.expensely_backend.repository.CategoryRepository;
import com.example.expensely_backend.repository.ExpenseRepository;
import com.example.expensely_backend.utils.FormatDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class BudgetService {

	private final CategoryRepository categoryRepository;
	private final UserService userService;
	private final ExpenseRepository expenseRepository;
	private final AlertHandler alertHandler;
	private final BudgetRepository budgetRepository;

	private void validateBudget(Budget budget) {
		if (budget.getUser() == null)
			throw new IllegalArgumentException("User must not be null");
		if (budget.getAmountLimit() == null || budget.getAmountLimit().compareTo(BigDecimal.ZERO) <= 0)
			throw new IllegalArgumentException("Budget limit must be positive");
		if (budget.getStartDate() == null || budget.getEndDate() == null)
			throw new IllegalArgumentException("Start and end date must not be null");
		if (budget.getStartDate().isAfter(budget.getEndDate()))
			throw new IllegalArgumentException("Start date must be before end date");
		if (budget.getPeriod() == null)
			throw new IllegalArgumentException("Budget period must not be null");
	}

	@Transactional
	public Budget save(Budget budget) {
		User user = userService.GetActiveUserById(budget.getUser().getId().toString());
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		budget.setUser(user);
		Optional<Category> categoryOP = categoryRepository.findById(budget.getCategory().getId());
		if (categoryOP.isEmpty()) {
			throw new IllegalArgumentException("Category not found");
		}
		Category category = categoryOP.get();
		budget.setCategory(category);
		//check if any active budget for a user and a category exists
		if (budgetRepository.existsByUserIdAndCategoryIdAndIsActiveTrue(budget.getUser().getId(), budget.getCategory().getId()) && budget.isActive()) {
			throw new IllegalArgumentException("A budget already exists for this user and category");
		}


		try {
			validateBudget(budget);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error validating budget: " + e.getMessage());
		}

		budget.setAmountSpent(budget.getAmountSpent() == null ? BigDecimal.ZERO : budget.getAmountSpent());
		budget.setCreatedAt(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
		budget.setUpdatedAt(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());

//        update budget by fetching all expense for this category and user
		BigDecimal total_amount = budget.getAmountSpent();
		List<Expense> expense = expenseRepository.findByUserIdAndTimeFrameAsc(user.getId(), FormatDate.formatStartDate(budget.getStartDate().atStartOfDay(), true), FormatDate.formatEndDate(budget.getEndDate().atStartOfDay()));
		for (Expense exp : expense) {
			if (exp.getCategory().getId().equals(budget.getCategory().getId())) {
				total_amount = total_amount.add(exp.getAmount());
			}
		}
		budget.setAmountSpent(total_amount);
		return budgetRepository.save(budget);
	}

	public Budget findById(String id) {
		return budgetRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Budget not found"));
	}

	public void deleteByIdHard(String id) {
		try {
			Budget budget = budgetRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Budget not found"));
//            budget.setActive(false);
//            budget.setUpdatedAt(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
			budgetRepository.delete(budget);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error deleting budget: " + e.getMessage());
		}
	}

	public List<Budget> findAll() {
		List<Budget> budgets = budgetRepository.findAll();
		if (budgets.isEmpty()) {
			throw new IllegalArgumentException("No budgets found");
		}
		return budgets;
	}

	public List<Budget> getBudgetByCategoryId(String categoryId) {
		UUID categoryUUID = UUID.fromString(categoryId);
		try {
			return budgetRepository.findByCategoryId(categoryUUID);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error retrieving budget for category ID: " + categoryId + " - " + e.getMessage());
		}
	}

	public List<Budget> getBudgetsByUserId(String userId) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		return budgetRepository.findByUserId(user.getId());
	}

	public Budget updateBudget(Budget budget) {

		if (budget.getUser() == null) {
			throw new IllegalArgumentException("User must not be null");
		}
		if (!budgetRepository.existsById(budget.getId())) {
			throw new IllegalArgumentException("Budget not found");
		}
		if (budget.getAmountLimit() == null || budget.getAmountLimit().intValue() <= 0) {
			throw new IllegalArgumentException("Budget limit must not be null");
		}
		if (budget.getStartDate() == null) {
			throw new IllegalArgumentException("Budget start date must not be null");
		}
		if (budget.getEndDate() == null) {
			throw new IllegalArgumentException("Budget end date must not be null");
		}
		if (budget.getStartDate().isAfter(budget.getEndDate())) {
			throw new IllegalArgumentException("Budget start date must be before end date");
		}
		if (budget.getPeriod() == null) {
			throw new IllegalArgumentException("Budget period must not be null");
		}
		try {

			budget.setUpdatedAt(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
			BigDecimal total_amount = budget.getAmountSpent();

			List<Expense> expense = expenseRepository.findByUserIdAndTimeFrameAsc(budget.getUser().getId(), FormatDate.formatStartDate(budget.getStartDate().atStartOfDay(), true), FormatDate.formatEndDate(budget.getEndDate().atStartOfDay()));
			for (Expense exp : expense) {
				if (exp.getCategory().getId().equals(budget.getCategory().getId())) {

					total_amount = total_amount.add(exp.getAmount());
				}
			}
			budget.setAmountSpent(total_amount);

			return budgetRepository.save(budget);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
		}
	}

	public Budget findByUserIdAndCategoryId(String user_id, String category_id) {
		return budgetRepository.findByUserIdAndCategoryId(UUID.fromString(user_id), UUID.fromString(category_id));
	}

	public void updateBudgetAmountByUserIdAndCategoryId(String user_id, String category_id, BigDecimal amount, LocalDateTime date) {
		if (date == null) {
			throw new IllegalArgumentException("Date must not be null");
		}
		Budget budget = findByUserIdAndCategoryId(user_id, category_id);
		if (budget == null) {
			return;
		}

		if (date.toLocalDate().isBefore(budget.getStartDate()) || date.toLocalDate().isAfter(budget.getEndDate())) {
			return;
		}

		budget.setAmountSpent(budget.getAmountSpent().add(amount));
		budget.setUpdatedAt(LocalDateTime.now());
		try {
			budgetRepository.save(budget);
		} catch (Exception e) {

			throw new IllegalArgumentException("Error updating budget amount: " + e.getMessage());
		}

//        calculate percentage used and send msg
		float percentage = (budget.getAmountSpent().floatValue() / budget.getAmountLimit().floatValue()) * 100;
		// Calculate once, format as an integer for the message
		int displayPercent = (int) percentage;
		String category = budget.getCategory().getName();
		String text = "";
		globals.MessageType type = globals.MessageType.ALERT;

		if (percentage >= 70 && percentage < 100) {
			text = String.format("Heads up! You've used %d%% of your %s budget.", displayPercent, category);
			type = globals.MessageType.ALERT;
		} else if (percentage >= 100) {
			text = String.format("You've exceeded your %s budget limit.", category);
			type = globals.MessageType.ERROR;
		}

// Only send if a message was actually set
		if (!text.isEmpty()) {
			MessageDTO msg = new MessageDTO();
			msg.setMessage(text);
			msg.setSender(globals.SERVER_SENDER);
			msg.setType(type);

			try {
				alertHandler.sendAlert(budget.getUser().getId(), msg);
			} catch (Exception e) {
				// Use a logger in production instead of System.out
				System.out.println("Failed to send budget alert: " + e.getMessage());
			}
		}

	}

}
