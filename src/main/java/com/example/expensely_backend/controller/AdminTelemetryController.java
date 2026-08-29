package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.ApiLogRow;
import com.example.expensely_backend.dto.EndpointBreakdownRow;
import com.example.expensely_backend.dto.FunctionFailureRow;
import com.example.expensely_backend.dto.FunctionLogRow;
import com.example.expensely_backend.dto.TelemetryOverviewResponse;
import com.example.expensely_backend.dto.UserRes;
import com.example.expensely_backend.model.ApiRequestLog;
import com.example.expensely_backend.model.FunctionLog;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.service.TelemetryService;
import com.example.expensely_backend.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admins/telemetry")
public class AdminTelemetryController {

    private final UserService userService;
    private final TelemetryService telemetryService;

    public AdminTelemetryController(UserService userService, TelemetryService telemetryService) {
        this.userService = userService;
        this.telemetryService = telemetryService;
    }

    private User requireAdmin(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        String userId = (String) authentication.getPrincipal();
        User user = userService.GetUserById(userId);
        if (user.getIsAdmin() == null || !user.getIsAdmin()) {
            return null;
        }
        if (user.getIsActive() == null || !user.getIsActive()) {
            return null;
        }
        return user;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(
            Authentication authentication,
            @RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(value = "bucket", required = false, defaultValue = "hour") String bucket) {
        if (requireAdmin(authentication) == null) {
            return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
        }
        TelemetryOverviewResponse response = telemetryService.getOverview(startDate, endDate, bucket);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/endpoints")
    public ResponseEntity<?> getEndpointBreakdown(
            Authentication authentication,
            @RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate,
            @RequestParam(value = "sort_by", required = false, defaultValue = "volume") String sortBy,
            @RequestParam(value = "limit", required = false) Integer limit) {
        if (requireAdmin(authentication) == null) {
            return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
        }
        List<EndpointBreakdownRow> response = telemetryService.getEndpointBreakdown(startDate, endDate, sortBy, limit);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/function-errors")
    public ResponseEntity<?> getFunctionFailures(
            Authentication authentication,
            @RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        if (requireAdmin(authentication) == null) {
            return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
        }
        List<FunctionFailureRow> response = telemetryService.getFunctionFailures(startDate, endDate);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api-logs")
    public ResponseEntity<?> searchApiLogs(
            Authentication authentication,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "25") int size,
            @RequestParam(value = "user_id", required = false) UUID userId,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "path", required = false) String path,
            @RequestParam(value = "status_code", required = false) Integer statusCode,
            @RequestParam(value = "min_status", required = false) Integer minStatus,
            @RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        if (requireAdmin(authentication) == null) {
            return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<ApiLogRow> response = telemetryService.searchApiLogs(
                userId, method, path, statusCode, minStatus, startDate, endDate, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api-logs/{id}")
    public ResponseEntity<?> getApiLogDetail(Authentication authentication, @PathVariable UUID id) {
        if (requireAdmin(authentication) == null) {
            return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
        }
        ApiRequestLog response = telemetryService.getApiLogDetail(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/function-logs")
    public ResponseEntity<?> searchFunctionLogs(
            Authentication authentication,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "25") int size,
            @RequestParam(value = "user_id", required = false) UUID userId,
            @RequestParam(value = "request_id", required = false) String requestId,
            @RequestParam(value = "class_name", required = false) String className,
            @RequestParam(value = "method_name", required = false) String methodName,
            @RequestParam(value = "layer", required = false) String layer,
            @RequestParam(value = "success", required = false) Boolean success,
            @RequestParam(value = "start_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startDate,
            @RequestParam(value = "end_date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endDate) {
        if (requireAdmin(authentication) == null) {
            return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<FunctionLogRow> response = telemetryService.searchFunctionLogs(
                userId, requestId, className, methodName, layer, success, startDate, endDate, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/function-logs/{id}")
    public ResponseEntity<?> getFunctionLogDetail(Authentication authentication, @PathVariable UUID id) {
        if (requireAdmin(authentication) == null) {
            return ResponseEntity.status(403).body(new UserRes(null, "Forbidden"));
        }
        FunctionLog response = telemetryService.getFunctionLogDetail(id);
        return ResponseEntity.ok(response);
    }
}
