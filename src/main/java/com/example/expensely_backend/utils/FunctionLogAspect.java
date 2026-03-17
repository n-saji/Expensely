package com.example.expensely_backend.utils;

import com.example.expensely_backend.model.FunctionLog;
import com.example.expensely_backend.service.DbLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Aspect
@Component
public class FunctionLogAspect {

    private final DbLogService dbLogService;
    private final ObjectMapper objectMapper;

    public FunctionLogAspect(DbLogService dbLogService, ObjectMapper objectMapper) {
        this.dbLogService = dbLogService;
        this.objectMapper = objectMapper;
    }

    @Around("(execution(* com.example.expensely_backend.controller..*(..)) || " +
            "execution(* com.example.expensely_backend.service..*(..)) || " +
            "execution(* com.example.expensely_backend.handler..*(..))) && " +
            "!within(com.example.expensely_backend.service.DbLogService)")
    public Object logFunction(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        FunctionLog log = new FunctionLog();
        log.setClassName(joinPoint.getSignature().getDeclaringTypeName());
        log.setMethodName(joinPoint.getSignature().getName());
        log.setLayer(resolveLayer(log.getClassName()));
        log.setThreadName(Thread.currentThread().getName());
        log.setUserId(resolveUserId());
        log.setRequestId(resolveRequestId());
        log.setArguments(serializeArguments(joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            log.setSuccess(true);
            log.setResult(serializeSafely(result));
            return result;
        } catch (Throwable ex) {
            log.setSuccess(false);
            log.setErrorMessage(ex.getMessage());
            log.setStackTrace(stackTraceToString(ex));
            throw ex;
        } finally {
            log.setDurationMs(System.currentTimeMillis() - start);
            dbLogService.logFunction(log);
        }
    }

    private String resolveLayer(String className) {
        if (className.contains(".controller.")) {
            return "controller";
        }
        if (className.contains(".handler.")) {
            return "handler";
        }
        if (className.contains(".service.")) {
            return "service";
        }
        return "unknown";
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
            Object id = request.getAttribute(ApiRequestLoggingFilter.REQUEST_ID_ATTRIBUTE);
            return id == null ? null : id.toString();
        }
        return null;
    }

    private String serializeSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String serializeArguments(Object[] args) {
        if (args == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(args);
        } catch (JsonProcessingException e) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(safeToString(args[i]));
            }
            builder.append("]");
            return builder.toString();
        }
    }

    private String safeToString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof HttpServletRequest) {
            return "HttpServletRequest";
        }
        if (value instanceof HttpServletResponse) {
            return "HttpServletResponse";
        }
        if (value instanceof MultipartFile file) {
            return "MultipartFile(name=" + file.getOriginalFilename() + ", size=" + file.getSize() + ")";
        }
        return String.valueOf(value);
    }

    private String stackTraceToString(Throwable throwable) {
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
