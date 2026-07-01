package com.example.expensely_backend.dto;

import java.util.List;

public record TransactionResList(List<TransactionResponse> transactions, long totalPages, long totalElements,
                                 long pageNumber) {
}
