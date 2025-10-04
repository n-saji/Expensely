package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Budget;
import lombok.Getter;

import java.util.List;

public class BudgetResponseList {
    @Getter
    private final List<Budget> budgets;
    @Getter
    private final int totalPages;
    @Getter
    private final int currentPage;
    @Getter
    private final int totalItems;

    public BudgetResponseList(List<Budget> budgets) {

        for (Budget budget : budgets) {
            budget.setCategory(null);
            budget.setUser(null);
        }
        this.budgets = budgets;
        this.totalPages = 1;
        this.currentPage = 1;
        this.totalItems = budgets.size();

    }
}
