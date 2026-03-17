package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "function_logs",
        indexes = {
                @Index(name = "idx_function_logs_created_at", columnList = "createdAt"),
                @Index(name = "idx_function_logs_user_id", columnList = "userId"),
                @Index(name = "idx_function_logs_request_id", columnList = "requestId")
        }
)
@Data
public class FunctionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;
    private String requestId;
    private String layer;
    private String className;
    private String methodName;
    private Boolean success;
    private Long durationMs;
    private String threadName;

    @Lob
    private String arguments;

    @Lob
    private String result;

    private String errorMessage;

    @Lob
    private String stackTrace;

    private LocalDateTime createdAt = LocalDateTime.now();
}

