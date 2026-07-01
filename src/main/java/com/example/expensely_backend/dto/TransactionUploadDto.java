package com.example.expensely_backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class TransactionUploadDto {
    private Double amount;
    private String category;
    private LocalDate transaction_date;
    private String description;
    private String type; // EXPENSE or INCOME
}
