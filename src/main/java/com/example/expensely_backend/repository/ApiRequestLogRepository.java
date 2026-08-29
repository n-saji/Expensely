package com.example.expensely_backend.repository;

import com.example.expensely_backend.dto.ApiLogRow;
import com.example.expensely_backend.dto.EndpointBreakdownRow;
import com.example.expensely_backend.dto.OverviewSummary;
import com.example.expensely_backend.dto.TimeBucketCount;
import com.example.expensely_backend.dto.TimeBucketErrorRate;
import com.example.expensely_backend.dto.TimeBucketLatency;
import com.example.expensely_backend.model.ApiRequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApiRequestLogRepository extends JpaRepository<ApiRequestLog, UUID> {

    @Query(value = """
            SELECT date_trunc(:bucket::text, created_at) AS bucketTime, COUNT(*) AS requestCount
            FROM api_request_logs
            WHERE created_at >= :startDate AND created_at < :endDate
            GROUP BY bucketTime
            ORDER BY bucketTime
            """, nativeQuery = true)
    List<TimeBucketCount> findVolumeOverTime(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate,
                                              @Param("bucket") String bucket);

    @Query(value = """
            SELECT date_trunc(:bucket::text, created_at) AS bucketTime,
                   AVG(duration_ms) AS avgDurationMs,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95DurationMs
            FROM api_request_logs
            WHERE created_at >= :startDate AND created_at < :endDate
            GROUP BY bucketTime
            ORDER BY bucketTime
            """, nativeQuery = true)
    List<TimeBucketLatency> findLatencyOverTime(@Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate,
                                                 @Param("bucket") String bucket);

    @Query(value = """
            SELECT date_trunc(:bucket::text, created_at) AS bucketTime,
                   COUNT(*) AS totalCount,
                   COUNT(*) FILTER (WHERE status_code >= 400) AS errorCount
            FROM api_request_logs
            WHERE created_at >= :startDate AND created_at < :endDate
            GROUP BY bucketTime
            ORDER BY bucketTime
            """, nativeQuery = true)
    List<TimeBucketErrorRate> findErrorRateOverTime(@Param("startDate") LocalDateTime startDate,
                                                      @Param("endDate") LocalDateTime endDate,
                                                      @Param("bucket") String bucket);

    @Query(value = """
            SELECT COUNT(*) AS totalCount,
                   AVG(duration_ms) AS avgDurationMs,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95DurationMs,
                   COUNT(*) FILTER (WHERE status_code >= 400) AS errorCount
            FROM api_request_logs
            WHERE created_at >= :startDate AND created_at < :endDate
            """, nativeQuery = true)
    OverviewSummary findOverviewSummary(@Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);

    @Query(value = """
            SELECT method, path,
                   COUNT(*) AS requestCount,
                   AVG(duration_ms) AS avgDurationMs,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95DurationMs,
                   COUNT(*) FILTER (WHERE status_code >= 400) AS errorCount
            FROM api_request_logs
            WHERE created_at >= :startDate AND created_at < :endDate
            GROUP BY method, path
            ORDER BY requestCount DESC
            """, nativeQuery = true)
    List<EndpointBreakdownRow> findEndpointBreakdown(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    @Query("""
            SELECT new com.example.expensely_backend.dto.ApiLogRow(
                l.id, l.userId, l.requestId, l.method, l.path, l.queryString, l.statusCode, l.durationMs, l.createdAt)
            FROM ApiRequestLog l
            WHERE (:userId IS NULL OR l.userId = :userId)
              AND (:method IS NULL OR l.method = :method)
              AND (:path IS NULL OR l.path LIKE %:path%)
              AND (:statusCode IS NULL OR l.statusCode = :statusCode)
              AND (:minStatus IS NULL OR l.statusCode >= :minStatus)
              AND l.createdAt >= :startDate AND l.createdAt < :endDate
            ORDER BY l.createdAt DESC
            """)
    Page<ApiLogRow> searchLogs(@Param("userId") UUID userId,
                                @Param("method") String method,
                                @Param("path") String path,
                                @Param("statusCode") Integer statusCode,
                                @Param("minStatus") Integer minStatus,
                                @Param("startDate") LocalDateTime startDate,
                                @Param("endDate") LocalDateTime endDate,
                                Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM api_request_logs
            WHERE id IN (SELECT id FROM api_request_logs WHERE created_at < :cutoff LIMIT :batchSize)
            """, nativeQuery = true)
    int deleteBatchOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
