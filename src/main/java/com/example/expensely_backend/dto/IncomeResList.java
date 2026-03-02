package com.example.expensely_backend.dto;

import java.util.List;

public record IncomeResList(List<IncomeResponse> incomes, long totalPages,
                            long totalElements,
                            long pageNumber) {
}

