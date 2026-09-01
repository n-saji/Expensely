package com.example.expensely_backend.utils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TimeSeriesUtils {

	private static final DateTimeFormatter MONTH_KEY_FORMATTER =
			DateTimeFormatter.ofPattern("MMM/yy", Locale.ENGLISH);

	private TimeSeriesUtils() {
	}

	public static LinkedHashMap<String, Double> fillMonthlyTotals(
			Map<String, Double> totals,
			LocalDateTime startDate,
			LocalDateTime endDate) {
		LinkedHashMap<String, Double> normalizedTotals = new LinkedHashMap<>();
		if (totals != null) {
			totals.forEach((key, value) -> normalizedTotals.put(key.trim(), value));
		}

		LinkedHashMap<String, Double> result = new LinkedHashMap<>();
		YearMonth current = YearMonth.from(startDate);
		YearMonth last = YearMonth.from(endDate);
		while (!current.isAfter(last)) {
			String key = formatMonthKey(current);
			result.put(key, normalizedTotals.getOrDefault(key, 0.0));
			current = current.plusMonths(1);
		}
		return result;
	}

	public static LinkedHashMap<String, Map<String, Double>> fillMonthlyCategories(
			Map<String, ? extends Map<String, Double>> totals,
			Collection<String> categoryNames,
			LocalDateTime startDate,
			LocalDateTime endDate) {
		Map<String, ? extends Map<String, Double>> safeTotals = totals == null ? Map.of() : totals;
		LinkedHashMap<String, Map<String, Double>> result = new LinkedHashMap<>();

		YearMonth current = YearMonth.from(startDate);
		YearMonth last = YearMonth.from(endDate);
		while (!current.isAfter(last)) {
			String key = formatMonthKey(current);
			Map<String, Double> values = new LinkedHashMap<>();
			for (String categoryName : categoryNames) {
				values.put(categoryName, 0.0);
			}
			Map<String, Double> existing = safeTotals.get(key);
			if (existing == null) {
				existing = safeTotals.entrySet().stream()
						.filter(entry -> entry.getKey().trim().equals(key))
						.map(Map.Entry::getValue)
						.findFirst()
						.orElse(null);
			}
			if (existing != null) {
				values.putAll(existing);
			}
			result.put(key, values);
			current = current.plusMonths(1);
		}

		return result;
	}

	public static String formatMonthKey(YearMonth month) {
		return month.format(MONTH_KEY_FORMATTER);
	}
}
