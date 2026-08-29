package com.example.expensely_backend.dto;

public interface FunctionFailureRow {
    String getClassName();
    String getMethodName();
    Long getFailureCount();
}
