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
import com.example.expensely_backend.utils.FormatDate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;


@Service
@RequiredArgsConstructor
public class BudgetService {

	private final CategoryRepository categoryRepository;
	private final UserService userService;
	private final TransactionRepository transactionRepository;
	private final AlertHandler alertHandler;
	private final BudgetRepository budgetRepository;
	private final DbLogService dbLogService;

	@Autowired
	private final ExchangeRateService exchangeRateService;

	private float calculatePercentage(BigDecimal amountSpent, BigDecimal amountLimit) {
		if (amountLimit == null || amountLimit.compareTo(BigDecimal.ZERO) <= 0) {
			return 0f;
		}
		BigDecimal safeSpent = amountSpent == null ? BigDecimal.ZERO : amountSpent;
		return safeSpent.multiply(BigDecimal.valueOf(100))
				.divide(amountLimit, 4, RoundingMode.HALF_UP)
				.floatValue();
	}

	private void syncThresholdFlags(Budget budget, float percentage) {
		budget.setThreshold50Crossed(percentage >= 50f);
		budget.setThreshold75Crossed(percentage >= 75f);
		budget.setThreshold100Crossed(percentage >= 100f);
	}

	private int resolveHighestNewlyCrossedThreshold(Budget budget, float percentage) {
		boolean crossed50 = percentage >= 50f;
		boolean crossed75 = percentage >= 75f;
		boolean crossed100 = percentage >= 100f;

		if (crossed100 && !budget.isThreshold100Crossed()) {
			return 100;
		}
		if (crossed75 && !budget.isThreshold75Crossed()) {
			return 75;
		}
		if (crossed50 && !budget.isThreshold50Crossed()) {
			return 50;
		}
		return 0;
	}

	private void sendBudgetThresholdAlert(Budget budget, float percentage) {
		int displayPercent = (int) percentage;
		String category = budget.getCategory().getName();
		String text = "";
		globals.MessageType type = globals.MessageType.ALERT;

		if (percentage >= 50 && percentage <= 100) {
			text = String.format("Heads up! You've used %d%% of your %s budget.", displayPercent, category);
			type = globals.MessageType.ALERT;
		} else if (percentage > 100) {
			text = String.format("You've exceeded your %s budget limit.", category);
			type = globals.MessageType.ERROR;
		}

		if (text.isEmpty()) {
			return;
		}

		MessageDTO msg = new MessageDTO();
		msg.setMessage(text);
		msg.setSender(globals.SERVER_SENDER);
		msg.setType(type);

		try {
			alertHandler.sendAlert(budget.getUser().getId(), msg);
		} catch (Exception e) {
			dbLogService.logError("service", getClass().getName(), "updateBudgetAmountByUserIdAndCategoryId",
					"Failed to send budget alert: " + e.getMessage(), e);
		}
	}

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
	public Budget save(String userID, Budget budget) {
		User user = userService.GetActiveUserById(userID);
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

		if (budget.getCurrency() == null || budget.getCurrency().isBlank()) {
			budget.setCurrency(globals.BASE_CURRENCY);
			budget.setBaseCurrencyAmount(budget.getAmountLimit());
			budget.setExchangeRate(BigDecimal.ONE);
		}
		if (!budget.getCurrency().equals(globals.BASE_CURRENCY)) {
			BigDecimal excRateAmount =
					exchangeRateService.convertToUsd(budget.getAmountLimit(),
							budget.getCurrency());
			budget.setBaseCurrencyAmount(excRateAmount);
			BigDecimal excRate =
					exchangeRateService.getUsdToCurrencyRate(budget.getCurrency());
			budget.setExchangeRate(excRate);
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
		List<Transaction> expense = transactionRepository.findByUserIdAndTypeAndTimeFrameAsc(user.getId(), TransactionType.EXPENSE, FormatDate.formatStartDate(budget.getStartDate().atStartOfDay(), true), FormatDate.formatEndDate(budget.getEndDate().atStartOfDay()));
		for (Transaction exp : expense) {
			if (exp.getCategory().getId().equals(budget.getCategory().getId())) {
				total_amount = total_amount.add(exp.getBaseCurrencyAmount());
			}
		}
		budget.setAmountSpent(total_amount);
		syncThresholdFlags(budget,
				calculatePercentage(budget.getAmountSpent(), budget.getBaseCurrencyAmount()));
		return budgetRepository.save(budget);
	}

	public Budget findById(String id) {
		return budgetRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Budget not found"));
	}

	public void softDeleteById(String id) {
		try {
			Budget budget = budgetRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Budget not found"));
			budget.setActive(false);
			budget.setUpdatedAt(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
			budgetRepository.save(budget);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error deleting budget: " + e.getMessage());
		}
	}

	public List<Budget> findAll() {
		List<Budget> budgets = budgetRepository.findAllOrderByUtilizationDesc();
		if (budgets.isEmpty()) {
			throw new IllegalArgumentException("No budgets found");
		}
		return budgets;
	}


	public List<Budget> getBudgetsByUserId(String userId) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		List<Budget> res = budgetRepository.findActiveBudgetsByUserId(user.getId());

		return res.stream().peek(b -> {
			b.setUser(null);
			b.getCategory().setUser(null);
		}).peek(b -> {
			BigDecimal spentUsd =
					b.getAmountSpent() == null
							? BigDecimal.ZERO
							: b.getAmountSpent();

			BigDecimal rate =
					exchangeRateService
							.getUsdToCurrencyRate(
									b.getCurrency()
							);

			b.setAmountSpent(
					spentUsd
							.multiply(rate)
							.setScale(
									2,
									RoundingMode.HALF_UP
							)
			);
		}).toList();
	}

	public Budget updateBudget(String UserId, String budgetId, Budget budget) {
		UUID budgetUUID = UUID.fromString(budgetId);
		Budget existingBudget =
				budgetRepository.findById(budgetUUID).orElseThrow(() -> new IllegalArgumentException("Budget not found"));
		if (!existingBudget.getUser().getId().toString().equals(UserId)) {
			throw new IllegalArgumentException("Unauthorized to update this budget");
		}
		if (budget.getAmountLimit() != null && budget.getAmountLimit().compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("Budget limit must not be null");
		}
		if (budget.getStartDate() != null && budget.getEndDate() != null && budget.getStartDate().isAfter(budget.getEndDate())) {
			throw new IllegalArgumentException("Budget start date must be before end date");
		}
		if (budget.getPeriod() != null && budget.getPeriod().name().isEmpty()) {

			throw new IllegalArgumentException("Budget period must not be " +
					"empty");
		}
		if (budget.getCurrency() != null && budget.getCurrency().isBlank()) {
			throw new IllegalArgumentException("Currency must not be empty");
		}
		try {
			if (budget.getAmountLimit() != null && !budget.getAmountLimit().subtract(existingBudget.getAmountLimit()).equals(BigDecimal.ZERO)) {
				BigDecimal excRateAmount =
						exchangeRateService.convertToUsd(budget.getAmountLimit(),
								budget.getCurrency());
				existingBudget.setBaseCurrencyAmount(excRateAmount);
				BigDecimal excRate =
						exchangeRateService.getUsdToCurrencyRate(budget.getCurrency());
				existingBudget.setExchangeRate(excRate);
				existingBudget.setAmountLimit(budget.getAmountLimit());
			}
			if (budget.getPeriod() != null && (!budget.getPeriod().name().isEmpty()))
				existingBudget.setPeriod(budget.getPeriod());
			if (budget.getStartDate() != null)
				existingBudget.setStartDate(budget.getStartDate());
			if (budget.getEndDate() != null)
				existingBudget.setEndDate(budget.getEndDate());
			if (budget.getCurrency() != null)
				existingBudget.setCurrency(budget.getCurrency());
			existingBudget.setUpdatedAt(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
			syncThresholdFlags(existingBudget,
					calculatePercentage(existingBudget.getAmountSpent(),
							existingBudget.getBaseCurrencyAmount()));

			return budgetRepository.save(existingBudget);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
		}
	}


	@Transactional
	public void updateBudgetAmountByUserIdAndCategoryId(String user_id, String category_id, BigDecimal amount, LocalDateTime date) {
		if (date == null) {
			throw new IllegalArgumentException("Date must not be null");
		}
		if (amount == null) {
			throw new IllegalArgumentException("Amount must not be null");
		}
		Budget budget = budgetRepository.findActiveBudgetByUserIdAndCategoryIdForUpdate(UUID.fromString(user_id), UUID.fromString(category_id));
		if (budget == null) {
			return;
		}

		if (date.toLocalDate().isBefore(budget.getStartDate()) || date.toLocalDate().isAfter(budget.getEndDate())) {
			return;
		}
		BigDecimal currentSpent = budget.getAmountSpent() == null ? BigDecimal.ZERO : budget.getAmountSpent();
		BigDecimal nextSpent = currentSpent.add(amount);
		if (nextSpent.compareTo(BigDecimal.ZERO) >= 0) {
			budget.setAmountSpent(nextSpent);
		} else {
			budget.setAmountSpent(BigDecimal.ZERO);
		}

		float percentage = calculatePercentage(budget.getAmountSpent(),
				budget.getBaseCurrencyAmount());
		int highestNewlyCrossedThreshold = resolveHighestNewlyCrossedThreshold(budget, percentage);
		syncThresholdFlags(budget, percentage);

		budget.setUpdatedAt(LocalDateTime.now());
		try {
			budgetRepository.save(budget);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error updating budget amount: " + e.getMessage());
		}
		if (highestNewlyCrossedThreshold > 0) {
			sendBudgetThresholdAlert(budget, percentage);
		}

	}

	public List<Category> getCategoriesWithoutActiveBudget(String userId) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found");
		}
		List<UUID> activeCategoryBudgets =
				new ArrayList<>(budgetRepository.findActiveBudgetsByUserId(user.getId()).stream().map(b -> b.getCategory().getId()).toList());
		if (activeCategoryBudgets.isEmpty()) {
			activeCategoryBudgets.add(UUID.randomUUID()); // Add a dummy UUID to
			// prevent SQL error when list is empty
		}
		return categoryRepository.findByUserIdAndTypeAndIdNotIn(user.getId(),
				globals.TYPE_EXPENSE,
				activeCategoryBudgets
		).stream().peek(b -> b.setUser(null)).toList();
	}

}
