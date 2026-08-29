package com.example.expensely_backend.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
public class ApiLogRow {
    private final UUID id;
    private final UUID userId;
    private final String requestId;
    private final String method;
    private final String path;
    private final String queryString;
    private final Integer statusCode;
    private final Long durationMs;
    private final LocalDateTime createdAt;

    public ApiLogRow(UUID id, UUID userId, String requestId, String method, String path,
                      String queryString, Integer statusCode, Long durationMs, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.requestId = requestId;
        this.method = method;
        this.path = path;
        this.queryString = queryString;
        this.statusCode = statusCode;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }
}
