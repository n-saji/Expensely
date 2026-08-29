package com.example.expensely_backend.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class FunctionLogRow {
    private final UUID id;
    private final UUID userId;
    private final String requestId;
    private final String layer;
    private final String className;
    private final String methodName;
    private final Boolean success;
    private final Long durationMs;
    private final LocalDateTime createdAt;

    public FunctionLogRow(UUID id, UUID userId, String requestId, String layer, String className,
                           String methodName, Boolean success, Long durationMs, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.requestId = requestId;
        this.layer = layer;
        this.className = className;
        this.methodName = methodName;
        this.success = success;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }
}
