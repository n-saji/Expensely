package com.example.expensely_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor

public class RowValidationError {

    private int row;      // 1-based index (excluding header)
    private String field;
    private String message;
}
