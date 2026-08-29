package com.example.expensely_backend.dto;

import java.time.LocalDateTime;

public interface TimeBucketLatency {
    LocalDateTime getBucketTime();
    Double getAvgDurationMs();
    Double getP95DurationMs();
}
