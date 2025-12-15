package com.example.expensely_backend.dto;

import java.util.List;

public record ExpenseResList(List<ExpenseResponse> expenses, long totalPages, long totalElements,
                             long pageNumber) {
}
