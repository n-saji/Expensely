package com.example.expensely_backend.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class TelemetryOverviewResponse {
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final String bucket;
    private final long totalRequests;
    private final double avgDurationMs;
    private final double p95DurationMs;
    private final double errorRatePercent;
    private final List<TimeBucketCount> volume;
    private final List<TimeBucketLatency> latency;
    private final List<TimeBucketErrorRate> errorRate;

    public TelemetryOverviewResponse(LocalDateTime startDate, LocalDateTime endDate, String bucket,
                                      long totalRequests, double avgDurationMs, double p95DurationMs,
                                      double errorRatePercent, List<TimeBucketCount> volume,
                                      List<TimeBucketLatency> latency, List<TimeBucketErrorRate> errorRate) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.bucket = bucket;
        this.totalRequests = totalRequests;
        this.avgDurationMs = avgDurationMs;
        this.p95DurationMs = p95DurationMs;
        this.errorRatePercent = errorRatePercent;
        this.volume = volume;
        this.latency = latency;
        this.errorRate = errorRate;
    }
}
