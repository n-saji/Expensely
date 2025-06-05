package com.example.expensely_backend.service;


import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Expense;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryService categoryService;
    private final UserService userService;

    public ExpenseService(ExpenseRepository expenseRepository,
                          CategoryService categoryService,
                          UserService userService) {
        this.expenseRepository = expenseRepository;
        this.categoryService = categoryService;
        this.userService = userService;
    }

    public void save(Expense expense) {
        if (expense.getCategory() == null || expense.getCategory().getId() == null) {
            throw new IllegalArgumentException("Category must be provided");
        }
        Category category =  categoryService.getCategoryById(expense.getCategory().getId().toString());
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        expense.setCategory(category);
        if (expense.getUser() == null || expense.getUser().getId() == null) {
            throw new IllegalArgumentException("User must be provided");
        }
        User user = userService.GetUserById(expense.getUser().getId().toString());
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        expense.setUser(user);
        expenseRepository.save(expense);
    }

    public Expense getExpenseById(String id) {
        return expenseRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new IllegalArgumentException("Expense not found"));
    }
    public void deleteExpenseById(String id) {
        try {
            if (!expenseRepository.existsById(UUID.fromString(id))) {
                throw new IllegalArgumentException("Expense not found");
            }
            expenseRepository.deleteById(UUID.fromString(id));
        } catch (Exception e) {
            throw new IllegalArgumentException("Error deleting expense: " + e.getMessage());
        }
    }
    public Iterable<Expense> getExpensesByUserId(String userId) {
        User user = userService.GetUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return expenseRepository.findByUserId(user.getId());
    }
    public List<Expense> getExpensesByCategoryId(String categoryId) {
        Category category = categoryService.getCategoryById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        return expenseRepository.findByCategoryId(category.getId());
    }
}
