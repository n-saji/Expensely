package com.example.expensely_backend.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AiInsightDto(
        UUID id,
        String type,
        String severity,
        String title,
        String description,
        String recommendation,
        String category,
        String confidence,
        JsonNode supportingMetrics,
        OffsetDateTime generatedAt
) {
}
