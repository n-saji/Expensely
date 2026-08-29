package com.example.expensely_backend.repository;

import com.example.expensely_backend.dto.FunctionFailureRow;
import com.example.expensely_backend.dto.FunctionLogRow;
import com.example.expensely_backend.model.FunctionLog;
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
public interface FunctionLogRepository extends JpaRepository<FunctionLog, UUID> {

    @Query(value = """
            SELECT class_name AS className, method_name AS methodName, COUNT(*) AS failureCount
            FROM function_logs
            WHERE success = false AND created_at >= :startDate AND created_at < :endDate
            GROUP BY class_name, method_name
            ORDER BY failureCount DESC
            LIMIT 20
            """, nativeQuery = true)
    List<FunctionFailureRow> findTopFailingFunctions(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    @Query("""
            SELECT new com.example.expensely_backend.dto.FunctionLogRow(
                f.id, f.userId, f.requestId, f.layer, f.className, f.methodName, f.success, f.durationMs, f.createdAt)
            FROM FunctionLog f
            WHERE (:userId IS NULL OR f.userId = :userId)
              AND (:requestId IS NULL OR f.requestId = :requestId)
              AND (:className IS NULL OR f.className = :className)
              AND (:methodName IS NULL OR f.methodName = :methodName)
              AND (:layer IS NULL OR f.layer = :layer)
              AND (:success IS NULL OR f.success = :success)
              AND f.createdAt >= :startDate AND f.createdAt < :endDate
            ORDER BY f.createdAt DESC
            """)
    Page<FunctionLogRow> searchLogs(@Param("userId") UUID userId,
                                     @Param("requestId") String requestId,
                                     @Param("className") String className,
                                     @Param("methodName") String methodName,
                                     @Param("layer") String layer,
                                     @Param("success") Boolean success,
                                     @Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate,
                                     Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
            DELETE FROM function_logs
            WHERE id IN (SELECT id FROM function_logs WHERE created_at < :cutoff LIMIT :batchSize)
            """, nativeQuery = true)
    int deleteBatchOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
