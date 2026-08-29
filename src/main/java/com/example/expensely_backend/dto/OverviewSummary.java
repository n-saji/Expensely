package com.example.expensely_backend.dto;

public interface OverviewSummary {
    Long getTotalCount();
    Double getAvgDurationMs();
    Double getP95DurationMs();
    Long getErrorCount();
}
