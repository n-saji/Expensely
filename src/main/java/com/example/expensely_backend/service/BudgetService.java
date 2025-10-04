package com.example.expensely_backend.service;

import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.BudgetRepository;
import com.example.expensely_backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserService userService;

    public BudgetService(BudgetRepository budgetRepository, UserService userService,
                         CategoryRepository categoryRepository)  {
        this.budgetRepository = budgetRepository;
        this.userService = userService;
        this.categoryRepository = categoryRepository;
    }

    public Budget save(Budget budget) {
        User user = userService.GetUserById(budget.getUser().getId().toString());
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
        if (budget.getId() != null) {
            if (!budgetRepository.existsById(budget.getId())) {
                throw new IllegalArgumentException("Budget not found");
            }
        }
        budget.setAmountSpent(budget.getAmountSpent() == null ?  BigDecimal.ZERO : budget.getAmountSpent());
        if (budget.getPeriod() == null) {
            throw new IllegalArgumentException("Budget period must not be null");
        }
        budget.setCreatedAt(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());
        budget.setUpdatedAt(new java.sql.Timestamp(new Date().getTime()).toLocalDateTime());

        return budgetRepository.save(budget);
    }

    public Budget findById(String id) {
        return budgetRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Budget not found"));
    }
    public void deleteById(String id) {
        try {
            Budget budget = budgetRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Budget not found"));
            budget.setActive(false);
            budgetRepository.save(budget);
        }catch (Exception e) {
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
        }catch (Exception e) {
            throw new IllegalArgumentException("Error retrieving budget for category ID: " + categoryId + " - " + e.getMessage());
        }
    }
    public List<Budget> getBudgetsByUserId(String userId) {
        User user = userService.GetUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return budgetRepository.findByUserId(user.getId());
    }

}
