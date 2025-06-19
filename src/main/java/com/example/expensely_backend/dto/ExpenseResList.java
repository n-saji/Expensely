package com.example.expensely_backend.dto;

import lombok.Getter;

import java.util.List;

public class ExpenseResList {
    @Getter
    private final List<ExpenseResponse> expenses;
    @Getter
    private final int totalPages;
    @Getter
    private final int totalElements;
    @Getter
    private final int pageNumber;

    public ExpenseResList(List<ExpenseResponse> expenses, int totalPages, int totalElements, int pageNumber) {
        this.expenses = expenses;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.pageNumber = pageNumber;
    }
}
