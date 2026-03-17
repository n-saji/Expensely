package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "api_request_logs",
        indexes = {
                @Index(name = "idx_api_request_logs_created_at", columnList = "createdAt"),
                @Index(name = "idx_api_request_logs_user_id", columnList = "userId")
        }
)
@Data
public class ApiRequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private String requestId;
    private String method;
    private String path;
    private String queryString;
    private String ipAddress;
    private String userAgent;
    private Integer statusCode;
    private Long durationMs;

    @Lob
    private String requestHeaders;

    @Lob
    private String responseHeaders;

    @Lob
    private String requestBody;

    @Lob
    private String responseBody;

    private LocalDateTime createdAt = LocalDateTime.now();
}

