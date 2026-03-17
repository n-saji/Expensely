package com.example.expensely_backend.service;

import com.example.expensely_backend.model.ApiRequestLog;
import com.example.expensely_backend.model.FunctionLog;
import com.example.expensely_backend.repository.ApiRequestLogRepository;
import com.example.expensely_backend.repository.FunctionLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DbLogService {

    private final ApiRequestLogRepository apiRequestLogRepository;
    private final FunctionLogRepository functionLogRepository;
    private final int batchSize;

    private final ConcurrentLinkedQueue<ApiRequestLog> apiQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<FunctionLog> functionQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger apiCount = new AtomicInteger(0);
    private final AtomicInteger functionCount = new AtomicInteger(0);
    private final AtomicBoolean apiFlushing = new AtomicBoolean(false);
    private final AtomicBoolean functionFlushing = new AtomicBoolean(false);

    public DbLogService(ApiRequestLogRepository apiRequestLogRepository,
                        FunctionLogRepository functionLogRepository,
                        @Value("${logging.db.batch-size:50}") int batchSize) {
        this.apiRequestLogRepository = apiRequestLogRepository;
        this.functionLogRepository = functionLogRepository;
        this.batchSize = batchSize;
    }

    public void logApi(ApiRequestLog log) {
        if (log == null) {
            return;
        }
        apiQueue.add(log);
        if (apiCount.incrementAndGet() >= batchSize) {
            flushApi();
        }
    }

    public void logFunction(FunctionLog log) {
        if (log == null) {
            return;
        }
        functionQueue.add(log);
        if (functionCount.incrementAndGet() >= batchSize) {
            flushFunction();
        }
    }

    @Scheduled(fixedDelayString = "${logging.db.flush-interval-ms:5000}")
    public void flush() {
        flushApi();
        flushFunction();
    }

    public void flushNow() {
        flush();
    }

    private void flushApi() {
        if (!apiFlushing.compareAndSet(false, true)) {
            return;
        }
        try {
            while (true) {
                List<ApiRequestLog> batch = drain(apiQueue, apiCount);
                if (batch.isEmpty()) {
                    break;
                }
                apiRequestLogRepository.saveAll(batch);
                if (batch.size() < batchSize) {
                    break;
                }
            }
        } finally {
            apiFlushing.set(false);
        }
    }

    private void flushFunction() {
        if (!functionFlushing.compareAndSet(false, true)) {
            return;
        }
        try {
            while (true) {
                List<FunctionLog> batch = drain(functionQueue, functionCount);
                if (batch.isEmpty()) {
                    break;
                }
                functionLogRepository.saveAll(batch);
                if (batch.size() < batchSize) {
                    break;
                }
            }
        } finally {
            functionFlushing.set(false);
        }
    }

    private <T> List<T> drain(ConcurrentLinkedQueue<T> queue, AtomicInteger counter) {
        List<T> batch = new ArrayList<>(batchSize);
        while (batch.size() < batchSize) {
            T item = queue.poll();
            if (item == null) {
                break;
            }
            counter.decrementAndGet();
            batch.add(item);
        }
        return batch;
    }

    public void logMessage(String layer, String className, String methodName, String message) {
        FunctionLog log = new FunctionLog();
        log.setLayer(layer);
        log.setClassName(className);
        log.setMethodName(methodName);
        log.setThreadName(Thread.currentThread().getName());
        log.setSuccess(true);
        log.setResult(message);
        log.setUserId(resolveUserId());
        log.setRequestId(resolveRequestId());
        logFunction(log);
    }

    public void logError(String layer, String className, String methodName, String message, Throwable throwable) {
        FunctionLog log = new FunctionLog();
        log.setLayer(layer);
        log.setClassName(className);
        log.setMethodName(methodName);
        log.setThreadName(Thread.currentThread().getName());
        log.setSuccess(false);
        log.setErrorMessage(message);
        log.setStackTrace(stackTraceToString(throwable));
        log.setUserId(resolveUserId());
        log.setRequestId(resolveRequestId());
        logFunction(log);
    }

    private UUID resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String) {
            try {
                return UUID.fromString((String) principal);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String resolveRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            Object id = request.getAttribute(com.example.expensely_backend.utils.ApiRequestLoggingFilter.REQUEST_ID_ATTRIBUTE);
            return id == null ? null : id.toString();
        }
        return null;
    }

    private String stackTraceToString(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            builder.append(": ").append(throwable.getMessage());
        }
        for (StackTraceElement element : throwable.getStackTrace()) {
            builder.append("\n\tat ").append(element.toString());
        }
        return builder.toString();
    }
}
