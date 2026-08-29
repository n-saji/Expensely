package com.example.expensely_backend.dto;

import java.time.LocalDateTime;

public interface TimeBucketCount {
    LocalDateTime getBucketTime();
    Long getRequestCount();
}
