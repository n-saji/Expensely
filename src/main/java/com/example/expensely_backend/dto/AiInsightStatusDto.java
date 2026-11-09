package com.example.expensely_backend.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AiInsightStatusDto(
        String status,
        boolean stale,
        LocalDate period,
        OffsetDateTime lastGeneratedAt,
        OffsetDateTime nextEligibleAt,
        String lastErrorCategory
) {
}
