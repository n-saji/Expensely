package com.example.expensely_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ExpenseUploadDto {
    private Double amount;
    private String category;
    private LocalDate expense_date;
    private String description;
}

