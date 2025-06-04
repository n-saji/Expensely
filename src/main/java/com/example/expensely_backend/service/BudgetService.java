package com.example.expensely_backend.service;

import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.BudgetRepository;
import com.example.expensely_backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;


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
}
