package com.example.expensely_backend.dto;

public interface DailyExpense {
    String getExpenseDate(); // match the alias in the query
    Double getTotalAmount();
}

