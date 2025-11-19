package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.BudgetResponse;
import com.example.expensely_backend.dto.BudgetResponseList;
import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.service.BudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody Budget budget) {
        if (budget.getAmountLimit() == null || budget.getAmountLimit().intValue() <= 0) {
            return ResponseEntity.badRequest().body(new BudgetResponse("Budget limit must not be null", null));
        }
        if (budget.getUser().getId() == null || budget.getUser().getId().toString().isEmpty()) {
            return ResponseEntity.badRequest().body(new BudgetResponse("User ID must not be null", null));
        }
        if (budget.getCategory() == null || budget.getCategory().toString().isEmpty()) {
            return ResponseEntity.badRequest().body(new BudgetResponse("Category must not be null", null));
        }

        try {
            budgetService.save(budget);
            return ResponseEntity.ok(new BudgetResponse("", "Successfully created"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new BudgetResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Budget> getBudgetById(@PathVariable String id) {
        try {
            Budget budget = budgetService.findById(id);
            return ResponseEntity.ok(budget);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBudgetById(@PathVariable String id) {
        try {
            budgetService.deleteByIdHard(id);
            return ResponseEntity.status(204).body(new BudgetResponse("", "Successfully deleted"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new BudgetResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllBudgets() {
        try {
            return ResponseEntity.ok(new BudgetResponseList(budgetService.findAll()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new BudgetResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getBudgetsByUserId(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(budgetService.getBudgetsByUserId(userId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBudget(@PathVariable String id, @RequestBody Budget budget) {
        try {
            Budget existingBudget = budgetService.findById(id);
            if (existingBudget == null) {
                return ResponseEntity.badRequest().body(new BudgetResponse("Budget not found!", null));
            }
            if (budget.getAmountLimit() == null || budget.getAmountLimit().intValue() <= 0) {
                return ResponseEntity.badRequest().body(new BudgetResponse("Budget limit must not be null", null));
            }
            existingBudget.setAmountLimit(budget.getAmountLimit());
//            existingBudget.setCategory(budget.getCategory());
            if (budget.getPeriod() != null) existingBudget.setPeriod(budget.getPeriod());
            if (budget.getStartDate() != null) existingBudget.setStartDate(budget.getStartDate());
            if (budget.getEndDate() != null) existingBudget.setEndDate(budget.getEndDate());
//            updating budget but amount has to be 0 as recalculation happens in service
            budget.setAmountSpent(BigDecimal.ZERO);
            budgetService.updateBudget(existingBudget);
            return ResponseEntity.ok(new BudgetResponse("", "Budget updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new BudgetResponse(e.getMessage(), null));
        }
    }


}
