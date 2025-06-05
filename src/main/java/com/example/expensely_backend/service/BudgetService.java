package com.example.expensely_backend.service;

import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.BudgetRepository;
import com.example.expensely_backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;

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
        return budgetRepository.save(budget);
    }

    public Budget findById(String id) {
        return budgetRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Budget not found"));
    }
    public void deleteById(String id) {
        try {
            budgetRepository.deleteById(UUID.fromString(id));
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
