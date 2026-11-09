package com.example.expensely_backend.dto;

import java.util.List;

public record AiInsightsResponse(
        AiInsightStatusDto refresh,
        List<AiInsightDto> insights
) {
}
