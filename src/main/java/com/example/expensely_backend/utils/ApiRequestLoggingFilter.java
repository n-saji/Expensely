package com.example.expensely_backend.utils;

import com.example.expensely_backend.model.ApiRequestLog;
import com.example.expensely_backend.service.DbLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.UUID;

@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    private final DbLogService dbLogService;

    public ApiRequestLoggingFilter(DbLogService dbLogService) {
        this.dbLogService = dbLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        String requestId = UUID.randomUUID().toString();
        requestWrapper.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        MDC.put(REQUEST_ID_ATTRIBUTE, requestId);

        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - start;
            ApiRequestLog log = new ApiRequestLog();
            log.setRequestId(requestId);
            log.setMethod(requestWrapper.getMethod());
            log.setPath(requestWrapper.getRequestURI());
            log.setQueryString(requestWrapper.getQueryString());
            log.setIpAddress(resolveIp(requestWrapper));
            log.setUserAgent(requestWrapper.getHeader("User-Agent"));
            log.setStatusCode(responseWrapper.getStatus());
            log.setDurationMs(duration);
            log.setRequestHeaders(extractHeaders(requestWrapper));
            log.setResponseHeaders(extractHeaders(responseWrapper));
            log.setRequestBody(readRequestBody(requestWrapper));
            log.setResponseBody(readResponseBody(responseWrapper));
            log.setUserId(resolveUserId());

            dbLogService.logApi(log);
            responseWrapper.copyBodyToResponse();
            MDC.remove(REQUEST_ID_ATTRIBUTE);
        }
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

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractHeaders(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                builder.append(name).append(": ").append(values.nextElement()).append("\n");
            }
        }
        return builder.toString();
    }

    private String extractHeaders(HttpServletResponse response) {
        StringBuilder builder = new StringBuilder();
        for (String name : response.getHeaderNames()) {
            for (String value : response.getHeaders(name)) {
                builder.append(name).append(": ").append(value).append("\n");
            }
        }
        return builder.toString();
    }

    private String readRequestBody(ContentCachingRequestWrapper request) {
        byte[] buffer = request.getContentAsByteArray();
        if (buffer.length == 0) {
            return null;
        }
        return new String(buffer, resolveCharset(request));
    }

    private String readResponseBody(ContentCachingResponseWrapper response) {
        byte[] buffer = response.getContentAsByteArray();
        if (buffer.length == 0) {
            return null;
        }
        return new String(buffer, StandardCharsets.UTF_8);
    }

    private java.nio.charset.Charset resolveCharset(HttpServletRequest request) {
        String encoding = request.getCharacterEncoding();
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        return java.nio.charset.Charset.forName(encoding);
    }
}

