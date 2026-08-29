package com.example.expensely_backend.dto;

import java.time.LocalDateTime;

public interface TimeBucketErrorRate {
    LocalDateTime getBucketTime();
    Long getTotalCount();
    Long getErrorCount();
}
