package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Income;
import lombok.Getter;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.round;

public class IncomeOverview {

	@Getter
	private final String userId;
	@Getter
	private final Double totalAmount;
	@Getter
	private final Map<String, Double> amountByCategory;
	@Getter
	private final LinkedHashMap<String, Double> amountByMonth;
	@Getter
	private final int totalCount;
	@Getter
	private final String mostFrequentCategory;
	@Getter
	private final int totalCategories;
	@Getter
	private final int mostFrequentCategoryCount;
	@Getter
	private final Double thisMonthTotalIncome;
	@Getter
	private final Map<String, Long> categoryCount;
	@Getter
	private final Double averageMonthlyIncome;
	@Getter
	private final Map<String, Map<String, Double>> monthlyCategoryIncome;
	@Getter
	private final Map<String, Double> topFiveMostIncomeItemThisMonth;
	@Getter
	private final Map<String, Double> overTheDaysThisMonth;
	@Getter
	private final Integer earliestStartMonth, earliestStartYear;
	@Getter
	private final Map<String, Double> thisMonthMostIncomeItem;
	@Getter
	private Double lastMonthTotalIncome = 0.0;

	public IncomeOverview(List<IncomeResponse> incomes,
	                      List<IncomeResponse> reqIncomeRange,
	                      List<IncomeResponse> reqIncomeRangeMonthly,
	                      String userId,
	                      List<MonthlyCategoryIncome> monthlyCategoryIncome,
	                      Iterable<Category> categories,
	                      List<DailyIncome> dailyIncomes,
	                      Integer reqMonth,
	                      Income firstIncome,
	                      Double lastMonthTotalIncome) {
		this.userId = userId;
		this.totalCount = incomes.size();
		this.categoryCount = incomes.stream()
				.collect(Collectors.groupingBy(IncomeResponse::getCategoryName, Collectors.counting()));
		this.mostFrequentCategoryCount = categoryCount.values().stream().max(Long::compare).orElse(0L).intValue();
		this.totalCategories = categoryCount.size();

		Calendar calendar = Calendar.getInstance();
		int currentMonth = calendar.get(Calendar.MONTH);

		Map<Month, Double> monthMap = incomes.stream()
				.collect(Collectors.groupingBy(income -> income.getIncomeDate().getMonth(), TreeMap::new,
						Collectors.summingDouble(IncomeResponse::getAmount)));
		this.thisMonthTotalIncome = round(monthMap.getOrDefault(Month.of(currentMonth + 1), 0.0) * 100.0) / 100.0;
		this.lastMonthTotalIncome = lastMonthTotalIncome == null ? 0.0 : lastMonthTotalIncome;
		this.totalAmount = incomes.stream().mapToDouble(IncomeResponse::getAmount).sum();
		this.averageMonthlyIncome = totalAmount / (currentMonth + 1);
		this.mostFrequentCategory = categoryCount.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(null);

		if (firstIncome != null && firstIncome.getIncomeDate() != null) {
			this.earliestStartMonth = firstIncome.getIncomeDate().getMonthValue();
			this.earliestStartYear = firstIncome.getIncomeDate().getYear();
		} else {
			this.earliestStartMonth = null;
			this.earliestStartYear = null;
		}

		IncomeResponse mostExpensive = incomes.stream()
				.filter(income -> income.getIncomeDate().getMonthValue() == (currentMonth + 1))
				.max(Comparator.comparingDouble(IncomeResponse::getAmount))
				.orElse(null);

		if (mostExpensive != null) {
			this.thisMonthMostIncomeItem = Map.of(
					mostExpensive.getDescription(),
					mostExpensive.getAmount()
			);
		} else {
			this.thisMonthMostIncomeItem = Map.of();
		}

		Map<Month, Double> monthMapReq = reqIncomeRange.stream()
				.collect(Collectors.groupingBy(income -> income.getIncomeDate().getMonth(), TreeMap::new,
						Collectors.summingDouble(IncomeResponse::getAmount)));
		this.amountByMonth = monthMapReq.entrySet().stream()
				.collect(Collectors.toMap(entry -> entry.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
						entry -> round(entry.getValue() * 100.0) / 100.0, (a, b) -> a, LinkedHashMap::new));

		Map<String, Double> rawSums = reqIncomeRange.stream()
				.collect(Collectors.groupingBy(
						IncomeResponse::getCategoryName,
						Collectors.summingDouble(IncomeResponse::getAmount)
				));
		this.amountByCategory = rawSums.entrySet().stream()
				.collect(Collectors.toMap(
						Map.Entry::getKey,
						entry -> round(entry.getValue() * 100.0) / 100.0
				));

		this.monthlyCategoryIncome = new LinkedHashMap<>();
		monthlyCategoryIncome.sort(Comparator.comparingInt(dto ->
				Month.valueOf(dto.getMonth().toUpperCase()).getValue()
		));
		for (MonthlyCategoryIncome dto : monthlyCategoryIncome) {
			String month = dto.getMonth().trim();

			this.monthlyCategoryIncome.computeIfAbsent(month, k -> {
				Map<String, Double> categoryMap = new LinkedHashMap<>();
				for (Category cat : categories) {
					categoryMap.put(cat.getName(), 0.0);
				}
				return categoryMap;
			});

			String category = dto.getCategoryName();
			Double amount = dto.getTotalAmount();

			if (category != null) {
				this.monthlyCategoryIncome.get(month).merge(category, amount, Double::sum);
			}
		}

		this.topFiveMostIncomeItemThisMonth = reqIncomeRangeMonthly.stream()
				.filter(income -> income.getIncomeDate().getMonth() == Month.of(reqMonth))
				.sorted(Comparator.comparingDouble(IncomeResponse::getAmount).reversed())
				.limit(5)
				.collect(Collectors.toMap(IncomeResponse::getDescription, IncomeResponse::getAmount, (a, b) -> a,
						LinkedHashMap::new));

		this.overTheDaysThisMonth = new LinkedHashMap<>();
		for (DailyIncome dailyIncome : dailyIncomes) {
			double amount = dailyIncome.getTotalAmount() != null ? dailyIncome.getTotalAmount() : 0.0;

			LocalDate date = LocalDate.parse(dailyIncome.getIncomeDate());
			String day = String.valueOf(date.getDayOfMonth());

			this.overTheDaysThisMonth.put(day, round(amount * 100.0) / 100.0);
		}
	}
}

