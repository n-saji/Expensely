package com.example.expensely_backend.dto;

public interface MonthlyCategoryExpense {
    String getMonth();
    String getCategoryName();
    Double getTotalAmount();
}
