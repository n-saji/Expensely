package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Expense;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

public class ExpenseResponse {

    @Getter
    private final UUID id;
    @Getter
    private final double amount;
    @Getter
    private final String description;
    @Getter
    private final LocalDateTime expenseDate;
    @Getter
    private final String categoryId;
    @Getter
    private final String categoryName;

    @Getter
    private final String userId;

    @Getter
    private final String currency;

    public ExpenseResponse(
            Expense expense
    ) {
        this.id = expense.getId();
        this.amount = expense.getAmount().doubleValue();
        this.description = expense.getDescription();
        this.expenseDate = expense.getExpenseDate();
        this.categoryId = expense.getCategory().getId().toString();
        this.categoryName = expense.getCategory().getName();
        this.userId = expense.getUser().getId().toString();
        this.currency = expense.getUser().getCurrency();
    }


}
