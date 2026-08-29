package com.example.expensely_backend.dto;

public interface EndpointBreakdownRow {
    String getMethod();
    String getPath();
    Long getRequestCount();
    Double getAvgDurationMs();
    Double getP95DurationMs();
    Long getErrorCount();
}
