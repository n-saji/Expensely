package com.example.expensely_backend.controller;

import com.example.expensely_backend.service.AiInsightService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai/insights")
public class AiInsightController {
    private final AiInsightService aiInsightService;

    public AiInsightController(AiInsightService aiInsightService) {
        this.aiInsightService = aiInsightService;
    }

    @GetMapping
    public ResponseEntity<?> getInsights(Authentication authentication) {
        UUID userId = authenticatedUser(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return ResponseEntity.ok(aiInsightService.getCurrent(userId));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(Authentication authentication) {
        UUID userId = authenticatedUser(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return ResponseEntity.ok(aiInsightService.getStatus(userId));
    }

    private UUID authenticatedUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof String principal)) {
            return null;
        }
        try {
            return UUID.fromString(principal);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
