package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Budget;

public record BudgetResponse(Budget budget, String error, String message) {

    public BudgetResponse(Budget budget, String message) {
        this(budget, null, message);
    }

    public BudgetResponse(String error, String message) {
        this(null, error, message);
    }
    public BudgetResponse(Budget budget) {
        this(budget, null, null);
    }



}
