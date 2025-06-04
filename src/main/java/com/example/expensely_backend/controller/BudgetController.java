package com.example.expensely_backend.controller;

import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.service.BudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService budgetService;
    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }
    @PostMapping("/create")
    public ResponseEntity<String> create(@RequestBody Budget budget) {
        if ( budget.getAmountLimit() == null || budget.getAmountLimit().intValue() <= 0) {
            return ResponseEntity.badRequest().body("Error: Invalid budget limit");
        }
        if (budget.getUser().getId() == null || budget.getUser().getId().toString().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: User ID must not be null");
        }
        if (budget.getCategory() == null || budget.getCategory().toString().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Category must not be null or empty");
        }

        try {
            budgetService.save(budget);
            return ResponseEntity.ok("Budget created successfully!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}
