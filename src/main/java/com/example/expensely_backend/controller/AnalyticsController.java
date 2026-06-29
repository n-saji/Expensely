package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.MonthlyAnalyticsResponse;
import com.example.expensely_backend.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

	private final AnalyticsService analyticsService;

	public AnalyticsController(AnalyticsService analyticsService) {
		this.analyticsService = analyticsService;
	}

	@GetMapping("/monthly")
	public ResponseEntity<?> getMonthlyAnalytics(
			Authentication authentication,
			@RequestParam("year") int year,
			@RequestParam("month") int month,
			@RequestParam("type") String type
	) {
		try {
			String userId = (String) authentication.getPrincipal();
			if (userId == null) {
				return ResponseEntity.status(401).body("Unauthorized");
			}
			if (month < 1 || month > 12) {
				return ResponseEntity.badRequest().body("Month must be between 1 and 12");
			}
			if (!"expense".equalsIgnoreCase(type) && !"income".equalsIgnoreCase(type)) {
				return ResponseEntity.badRequest().body("Type must be 'expense' or 'income'");
			}
			MonthlyAnalyticsResponse response = analyticsService.getMonthlyAnalytics(userId, year, month, type);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error fetching analytics: " + e.getMessage());
		}
	}
}
