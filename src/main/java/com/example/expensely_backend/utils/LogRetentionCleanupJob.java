package com.example.expensely_backend.utils;

import com.example.expensely_backend.repository.ApiRequestLogRepository;
import com.example.expensely_backend.repository.FunctionLogRepository;
import com.example.expensely_backend.service.DbLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class LogRetentionCleanupJob {

    private final ApiRequestLogRepository apiRequestLogRepository;
    private final FunctionLogRepository functionLogRepository;
    private final DbLogService dbLogService;
    private final int retentionDays;
    private final int deleteBatchSize;

    public LogRetentionCleanupJob(ApiRequestLogRepository apiRequestLogRepository,
                                   FunctionLogRepository functionLogRepository,
                                   DbLogService dbLogService,
                                   @Value("${logging.db.retention-days:30}") int retentionDays,
                                   @Value("${logging.db.cleanup-batch-size:1000}") int deleteBatchSize) {
        this.apiRequestLogRepository = apiRequestLogRepository;
        this.functionLogRepository = functionLogRepository;
        this.dbLogService = dbLogService;
        this.retentionDays = retentionDays;
        this.deleteBatchSize = deleteBatchSize;
    }

    @Scheduled(cron = "0 30 0 * * *")
    public void cleanupOldLogs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        int totalApiDeleted = 0;
        int deleted;
        do {
            deleted = apiRequestLogRepository.deleteBatchOlderThan(cutoff, deleteBatchSize);
            totalApiDeleted += deleted;
        } while (deleted == deleteBatchSize);

        int totalFunctionDeleted = 0;
        do {
            deleted = functionLogRepository.deleteBatchOlderThan(cutoff, deleteBatchSize);
            totalFunctionDeleted += deleted;
        } while (deleted == deleteBatchSize);

        dbLogService.logMessage("utils", getClass().getName(), "cleanupOldLogs",
                "Log retention cleanup ran, deleted " + totalApiDeleted + " api logs and "
                        + totalFunctionDeleted + " function logs older than " + retentionDays + " days.");
    }
}
