package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.ApiLogRow;
import com.example.expensely_backend.dto.EndpointBreakdownRow;
import com.example.expensely_backend.dto.FunctionFailureRow;
import com.example.expensely_backend.dto.FunctionLogRow;
import com.example.expensely_backend.dto.OverviewSummary;
import com.example.expensely_backend.dto.TelemetryOverviewResponse;
import com.example.expensely_backend.dto.TimeBucketCount;
import com.example.expensely_backend.dto.TimeBucketErrorRate;
import com.example.expensely_backend.dto.TimeBucketLatency;
import com.example.expensely_backend.model.ApiRequestLog;
import com.example.expensely_backend.model.FunctionLog;
import com.example.expensely_backend.repository.ApiRequestLogRepository;
import com.example.expensely_backend.repository.FunctionLogRepository;
import com.example.expensely_backend.utils.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class TelemetryService {

    private final ApiRequestLogRepository apiRequestLogRepository;
    private final FunctionLogRepository functionLogRepository;
    private final Executor expenseExecutor;

    public TelemetryService(ApiRequestLogRepository apiRequestLogRepository,
                             FunctionLogRepository functionLogRepository,
                             @Qualifier("expenseExecutor") Executor expenseExecutor) {
        this.apiRequestLogRepository = apiRequestLogRepository;
        this.functionLogRepository = functionLogRepository;
        this.expenseExecutor = expenseExecutor;
    }

    private static final int DEFAULT_ENDPOINT_LIMIT = 20;

    public LocalDateTime defaultStart(LocalDateTime startDate) {
        return startDate != null ? startDate : LocalDateTime.now().minusHours(24);
    }

    public LocalDateTime defaultEnd(LocalDateTime endDate) {
        return endDate != null ? endDate : LocalDateTime.now();
    }

    public String normalizeBucket(String bucket) {
        return "day".equalsIgnoreCase(bucket) ? "day" : "hour";
    }

    public TelemetryOverviewResponse getOverview(LocalDateTime startDate, LocalDateTime endDate, String bucket) {
        LocalDateTime start = defaultStart(startDate);
        LocalDateTime end = defaultEnd(endDate);
        String normalizedBucket = normalizeBucket(bucket);

        CompletableFuture<List<TimeBucketCount>> volumeFuture = CompletableFuture.supplyAsync(
                () -> apiRequestLogRepository.findVolumeOverTime(start, end, normalizedBucket), expenseExecutor);

        CompletableFuture<List<TimeBucketLatency>> latencyFuture = CompletableFuture.supplyAsync(
                () -> apiRequestLogRepository.findLatencyOverTime(start, end, normalizedBucket), expenseExecutor);

        CompletableFuture<List<TimeBucketErrorRate>> errorRateFuture = CompletableFuture.supplyAsync(
                () -> apiRequestLogRepository.findErrorRateOverTime(start, end, normalizedBucket), expenseExecutor);

        CompletableFuture<OverviewSummary> summaryFuture = CompletableFuture.supplyAsync(
                () -> apiRequestLogRepository.findOverviewSummary(start, end), expenseExecutor);

        CompletableFuture.allOf(volumeFuture, latencyFuture, errorRateFuture, summaryFuture).join();

        OverviewSummary summary = summaryFuture.join();
        long totalRequests = summary.getTotalCount() != null ? summary.getTotalCount() : 0L;
        double avgDurationMs = summary.getAvgDurationMs() != null ? summary.getAvgDurationMs() : 0.0;
        double p95DurationMs = summary.getP95DurationMs() != null ? summary.getP95DurationMs() : 0.0;
        long errorCount = summary.getErrorCount() != null ? summary.getErrorCount() : 0L;
        double errorRatePercent = totalRequests > 0 ? (errorCount * 100.0) / totalRequests : 0.0;

        return new TelemetryOverviewResponse(start, end, normalizedBucket, totalRequests, avgDurationMs,
                p95DurationMs, errorRatePercent, volumeFuture.join(), latencyFuture.join(), errorRateFuture.join());
    }

    public List<EndpointBreakdownRow> getEndpointBreakdown(LocalDateTime startDate, LocalDateTime endDate,
                                                             String sortBy, Integer limit) {
        LocalDateTime start = defaultStart(startDate);
        LocalDateTime end = defaultEnd(endDate);
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_ENDPOINT_LIMIT;

        List<EndpointBreakdownRow> rows = apiRequestLogRepository.findEndpointBreakdown(start, end);

        Comparator<EndpointBreakdownRow> comparator = switch (sortBy == null ? "" : sortBy) {
            case "avgDuration" -> Comparator.comparing(
                    r -> r.getAvgDurationMs() != null ? r.getAvgDurationMs() : 0.0, Comparator.reverseOrder());
            case "p95Duration" -> Comparator.comparing(
                    r -> r.getP95DurationMs() != null ? r.getP95DurationMs() : 0.0, Comparator.reverseOrder());
            case "errorCount" -> Comparator.comparing(
                    r -> r.getErrorCount() != null ? r.getErrorCount() : 0L, Comparator.reverseOrder());
            default -> Comparator.comparing(
                    r -> r.getRequestCount() != null ? r.getRequestCount() : 0L, Comparator.reverseOrder());
        };

        return rows.stream().sorted(comparator).limit(effectiveLimit).toList();
    }

    public List<FunctionFailureRow> getFunctionFailures(LocalDateTime startDate, LocalDateTime endDate) {
        return functionLogRepository.findTopFailingFunctions(defaultStart(startDate), defaultEnd(endDate));
    }

    public Page<ApiLogRow> searchApiLogs(UUID userId, String method, String path, Integer statusCode,
                                          Integer minStatus, LocalDateTime startDate, LocalDateTime endDate,
                                          Pageable pageable) {
        return apiRequestLogRepository.searchLogs(userId, method, path, statusCode, minStatus,
                defaultStart(startDate), defaultEnd(endDate), pageable);
    }

    public ApiRequestLog getApiLogDetail(UUID id) {
        return apiRequestLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("API log not found: " + id));
    }

    public Page<FunctionLogRow> searchFunctionLogs(UUID userId, String requestId, String className,
                                                    String methodName, String layer, Boolean success,
                                                    LocalDateTime startDate, LocalDateTime endDate,
                                                    Pageable pageable) {
        return functionLogRepository.searchLogs(userId, requestId, className, methodName, layer, success,
                defaultStart(startDate), defaultEnd(endDate), pageable);
    }

    public FunctionLog getFunctionLogDetail(UUID id) {
        return functionLogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Function log not found: " + id));
    }
}
