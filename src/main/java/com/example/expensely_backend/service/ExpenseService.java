package com.example.expensely_backend.service;


import com.example.expensely_backend.dto.ExpenseResponse;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Expense;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.ExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
        Category category = categoryService.getCategoryById(expense.getCategory().getId().toString());
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

    public List<Expense> getExpensesByCategoryIdAndUserID(String categoryId, String userId) {
        Category category = categoryService.getCategoryById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        User user = userService.GetUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        return expenseRepository.findByCategoryIdAndUserId(category.getId(), user.getId());
    }

    public Expense updateExpense(Expense expense) {

        Expense oldExpense = getExpenseById(expense.getId().toString());

        if (expense.getCategory() != null && expense.getCategory().getId() != null &&
                !expense.getCategory().getId().equals(oldExpense.getCategory().getId())) {
            Category category = categoryService.getCategoryById(expense.getCategory().getId().toString());
            if (category == null) {
                throw new IllegalArgumentException("Category not found");
            }
            oldExpense.setCategory(category);
        }


        if (!expenseRepository.existsById(expense.getId())) {
            throw new IllegalArgumentException("Expense not found");
        }


        if (expense.getAmount() != null && expense.getAmount().compareTo(oldExpense.getAmount()) != 0) {
            oldExpense.setAmount(expense.getAmount());
        }
        if (expense.getDescription() != null && !expense.getDescription().equals(oldExpense.getDescription())) {
            oldExpense.setDescription(expense.getDescription());
        }
        if (expense.getExpenseDate() != null && !expense.getExpenseDate().equals(oldExpense.getExpenseDate())) {
            oldExpense.setExpenseDate(expense.getExpenseDate());
        }


        return expenseRepository.save(oldExpense);
    }

    public List<ExpenseResponse> getExpenseByUserIdAndStartDateAndEndDate(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        User user = userService.GetUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        System.out.println("Getting expenses for user: " + userId + " from " + startDate + " to " + endDate);

        List<Expense> expenses = expenseRepository.findByUserIdAndTimeFrame(user.getId(), startDate, endDate);
        return expenses.stream().map(ExpenseResponse::new).collect(Collectors.toList());
    }
}
