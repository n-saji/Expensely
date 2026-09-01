package com.example.expensely_backend.utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeSeriesUtilsTest {

	@Test
	void fillsMissingMonthlyTotalsInChronologicalOrder() {
		Map<String, Double> totals = new LinkedHashMap<>();
		totals.put("Jan/26", 120.0);
		totals.put("Mar/26", 80.0);

		Map<String, Double> result = TimeSeriesUtils.fillMonthlyTotals(
				totals,
				LocalDateTime.of(2026, 1, 1, 0, 0),
				LocalDateTime.of(2026, 4, 30, 23, 59));

		assertEquals(List.of("Jan/26", "Feb/26", "Mar/26", "Apr/26"), List.copyOf(result.keySet()));
		assertEquals(0.0, result.get("Feb/26"));
		assertEquals(0.0, result.get("Apr/26"));
	}

	@Test
	void fillsMissingCategoryMonthsWithEveryCategoryAtZero() {
		Map<String, Map<String, Double>> totals = Map.of(
				"Mar/26", Map.of("Food", 45.0));

		Map<String, Map<String, Double>> result = TimeSeriesUtils.fillMonthlyCategories(
				totals,
				List.of("Food", "Travel"),
				LocalDateTime.of(2026, 1, 1, 0, 0),
				LocalDateTime.of(2026, 3, 31, 23, 59));

		assertEquals(List.of("Jan/26", "Feb/26", "Mar/26"), List.copyOf(result.keySet()));
		assertEquals(Map.of("Food", 0.0, "Travel", 0.0), result.get("Feb/26"));
		assertEquals(45.0, result.get("Mar/26").get("Food"));
		assertEquals(0.0, result.get("Mar/26").get("Travel"));
	}
}
