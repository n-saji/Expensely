package com.example.expensely_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;


@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BulkValidationResponse {

    private boolean valid;
    private String error;

    private String validationId; // present only if valid

    private int totalRows;
    private int validRows;
    private int invalidRows;

    private List<RowValidationError> errors; // empty if valid


}
