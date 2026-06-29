package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.*;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

	private final ExpenseRepository expenseRepository;
	private final IncomeRepository incomeRepository;
	private final BudgetRepository budgetRepository;
	private final UserService userService;
	private final ExchangeRateService exchangeRateService;

	@Transactional(readOnly = true)
	public MonthlyAnalyticsResponse getMonthlyAnalytics(String userId, int year, int month, String type) {
		User user = userService.GetActiveUserById(userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found or inactive");
		}

		String displayCurrency = user.getCurrency() != null ? user.getCurrency() : globals.BASE_CURRENCY;
		BigDecimal usdToUserRate = exchangeRateService.getUsdToCurrencyRate(displayCurrency);

		// Selected month boundaries
		LocalDate startOfSelectedMonth = LocalDate.of(year, month, 1);
		LocalDate endOfSelectedMonth = startOfSelectedMonth.with(TemporalAdjusters.lastDayOfMonth());
		int daysInMonth = startOfSelectedMonth.lengthOfMonth();

		LocalDateTime startDateTime = startOfSelectedMonth.atStartOfDay();
		LocalDateTime endDateTime = endOfSelectedMonth.atTime(23, 59, 59, 999999999);

		// Previous month boundaries
		LocalDate startOfPrevMonth = startOfSelectedMonth.minusMonths(1);
		LocalDate endOfPrevMonth = startOfPrevMonth.with(TemporalAdjusters.lastDayOfMonth());
		LocalDateTime prevStartDateTime = startOfPrevMonth.atStartOfDay();
		LocalDateTime prevEndDateTime = endOfPrevMonth.atTime(23, 59, 59, 999999999);

		// Same month last year boundaries
		LocalDate startOfLastYearMonth = startOfSelectedMonth.minusYears(1);
		LocalDate endOfLastYearMonth = startOfLastYearMonth.with(TemporalAdjusters.lastDayOfMonth());
		LocalDateTime lastYearStartDateTime = startOfLastYearMonth.atStartOfDay();
		LocalDateTime lastYearEndDateTime = endOfLastYearMonth.atTime(23, 59, 59, 999999999);

		// Fetch Selected Month Transactions for selected Type
		boolean isExpense = globals.TYPE_EXPENSE.equalsIgnoreCase(type);
		List<Expense> selectedMonthExpenses = expenseRepository.findByUserIdAndTimeFrameAsc(user.getId(), startDateTime, endDateTime);
		List<Income> selectedMonthIncomes = incomeRepository.findByUserIdAndTimeFrameAsc(user.getId(), startDateTime, endDateTime);

		// All calculations will use the normalized rates
		MonthlyAnalyticsResponse response = new MonthlyAnalyticsResponse();

		if (isExpense) {
			List<Expense> prevMonthExpenses = expenseRepository.findByUserIdAndTimeFrameAsc(user.getId(), prevStartDateTime, prevEndDateTime);
			List<Expense> lastYearExpenses = expenseRepository.findByUserIdAndTimeFrameAsc(user.getId(), lastYearStartDateTime, lastYearEndDateTime);
			List<Object[]> historicalRaw = expenseRepository.findHistoricalMonthlyDataExpense(user.getId(), month);

			calculateExpenseAnalytics(response, selectedMonthExpenses, prevMonthExpenses, lastYearExpenses, historicalRaw,
					usdToUserRate, displayCurrency, daysInMonth, startOfSelectedMonth, endOfSelectedMonth, user.getId());
		} else {
			List<Income> prevMonthIncomes = incomeRepository.findByUserIdAndTimeFrameAsc(user.getId(), prevStartDateTime, prevEndDateTime);
			List<Income> lastYearIncomes = incomeRepository.findByUserIdAndTimeFrameAsc(user.getId(), lastYearStartDateTime, lastYearEndDateTime);
			List<Object[]> historicalRaw = incomeRepository.findHistoricalMonthlyDataIncome(user.getId(), month);

			calculateIncomeAnalytics(response, selectedMonthIncomes, prevMonthIncomes, lastYearIncomes, historicalRaw,
					usdToUserRate, displayCurrency, daysInMonth, startOfSelectedMonth, endOfSelectedMonth);
		}

		// Calculate Overall Income vs Expense Summary (regardless of requested type)
		calculateIncomeVsExpenseSummary(response, selectedMonthExpenses, selectedMonthIncomes, usdToUserRate);

		return response;
	}

	private BigDecimal getUsdAmount(Expense e) {
		if (e.getBaseCurrencyAmount() != null) {
			return e.getBaseCurrencyAmount();
		}
		if (e.getCurrency() == null || e.getCurrency().equalsIgnoreCase(globals.BASE_CURRENCY)) {
			return e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO;
		}
		return exchangeRateService.convertToUsd(e.getAmount(), e.getCurrency());
	}

	private BigDecimal getUsdAmount(Income i) {
		if (i.getBaseCurrencyAmount() != null) {
			return i.getBaseCurrencyAmount();
		}
		if (i.getCurrency() == null || i.getCurrency().equalsIgnoreCase(globals.BASE_CURRENCY)) {
			return i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO;
		}
		return exchangeRateService.convertToUsd(i.getAmount(), i.getCurrency());
	}

	private void calculateExpenseAnalytics(
			MonthlyAnalyticsResponse response,
			List<Expense> current,
			List<Expense> previous,
			List<Expense> lastYear,
			List<Object[]> historicalRaw,
			BigDecimal rate,
			String displayCurrency,
			int daysInMonth,
			LocalDate startOfMonth,
			LocalDate endOfMonth,
			UUID userId
	) {
		// Summary
		BigDecimal totalAmount = current.stream()
				.map(e -> getUsdAmount(e).multiply(rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		long totalTransactions = current.size();

		BigDecimal averageTransactionAmount = totalTransactions > 0
				? totalAmount.divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		BigDecimal highestTransactionAmount = current.stream()
				.map(e -> getUsdAmount(e).multiply(rate))
				.max(BigDecimal::compareTo)
				.orElse(BigDecimal.ZERO)
				.setScale(2, RoundingMode.HALF_UP);

		// Group by day for highest/lowest day
		Map<LocalDate, BigDecimal> dailySums = current.stream()
				.collect(Collectors.groupingBy(
						e -> e.getExpenseDate().toLocalDate(),
						Collectors.reducing(BigDecimal.ZERO, e -> getUsdAmount(e).multiply(rate), BigDecimal::add)
				));

		String highestSpendingDay = dailySums.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(entry -> entry.getKey().toString())
				.orElse(null);

		String lowestSpendingDay = dailySums.entrySet().stream()
				.min(Map.Entry.comparingByValue())
				.map(entry -> entry.getKey().toString())
				.orElse(null);

		BigDecimal averageAmountPerDay = totalAmount.divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);

		MonthlyAnalyticsResponse.SummaryAnalytics summary = new MonthlyAnalyticsResponse.SummaryAnalytics();
		summary.setTotalAmount(totalAmount);
		summary.setTotalTransactions(totalTransactions);
		summary.setAverageTransactionAmount(averageTransactionAmount);
		summary.setHighestTransactionAmount(highestTransactionAmount);
		summary.setHighestSpendingEarningDay(highestSpendingDay);
		summary.setLowestSpendingEarningDay(lowestSpendingDay);
		summary.setAverageAmountPerDay(averageAmountPerDay);
		response.setSummary(summary);

		// Comparisons
		BigDecimal prevTotal = previous.stream()
				.map(e -> getUsdAmount(e).multiply(rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal lastYearTotal = lastYear.stream()
				.map(e -> getUsdAmount(e).multiply(rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		MonthlyAnalyticsResponse.MonthComparisonsAnalytics comparisons = new MonthlyAnalyticsResponse.MonthComparisonsAnalytics();
		comparisons.setPreviousMonth(createComparison(totalAmount, prevTotal));
		comparisons.setSameMonthLastYear(createComparison(totalAmount, lastYearTotal));

		List<MonthlyAnalyticsResponse.HistoricalYearData> historical = new ArrayList<>();
		for (Object[] row : historicalRaw) {
			int y = ((Number) row[0]).intValue();
			BigDecimal tot = new BigDecimal(row[1].toString()).multiply(rate).setScale(2, RoundingMode.HALF_UP);
			long cnt = ((Number) row[2]).longValue();

			MonthlyAnalyticsResponse.HistoricalYearData histData = new MonthlyAnalyticsResponse.HistoricalYearData();
			histData.setYear(y);
			histData.setTotalAmount(tot);
			histData.setTransactionCount(cnt);
			historical.add(histData);
		}
		comparisons.setHistoricalData(historical);
		response.setMonthComparisons(comparisons);

		// Category Analytics
		Map<Category, List<Expense>> currentByCat = current.stream()
				.filter(e -> e.getCategory() != null)
				.collect(Collectors.groupingBy(Expense::getCategory));

		Map<UUID, BigDecimal> prevByCatId = previous.stream()
				.filter(e -> e.getCategory() != null)
				.collect(Collectors.groupingBy(
						e -> e.getCategory().getId(),
						Collectors.reducing(BigDecimal.ZERO, e -> getUsdAmount(e).multiply(rate), BigDecimal::add)
				));

		Map<UUID, BigDecimal> lastYearByCatId = lastYear.stream()
				.filter(e -> e.getCategory() != null)
				.collect(Collectors.groupingBy(
						e -> e.getCategory().getId(),
						Collectors.reducing(BigDecimal.ZERO, e -> getUsdAmount(e).multiply(rate), BigDecimal::add)
				));

		List<MonthlyAnalyticsResponse.CategoryAnalytics> catAnalyticsList = new ArrayList<>();
		for (Map.Entry<Category, List<Expense>> entry : currentByCat.entrySet()) {
			Category cat = entry.getKey();
			List<Expense> catExpenses = entry.getValue();

			BigDecimal catTotal = catExpenses.stream()
					.map(e -> getUsdAmount(e).multiply(rate))
					.reduce(BigDecimal.ZERO, BigDecimal::add)
					.setScale(2, RoundingMode.HALF_UP);

			long count = catExpenses.size();
			BigDecimal catAverage = catTotal.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
			BigDecimal percentage = totalAmount.compareTo(BigDecimal.ZERO) > 0
					? catTotal.multiply(BigDecimal.valueOf(100)).divide(totalAmount, 2, RoundingMode.HALF_UP)
					: BigDecimal.ZERO;

			BigDecimal prevCatTotal = prevByCatId.getOrDefault(cat.getId(), BigDecimal.ZERO);
			BigDecimal lyCatTotal = lastYearByCatId.getOrDefault(cat.getId(), BigDecimal.ZERO);

			MonthlyAnalyticsResponse.CategoryAnalytics catAnalytics = new MonthlyAnalyticsResponse.CategoryAnalytics();
			catAnalytics.setCategoryId(cat.getId().toString());
			catAnalytics.setCategoryName(cat.getName());
			catAnalytics.setCategoryColor(cat.getColor());
			catAnalytics.setCategoryIcon(cat.getIcon());
			catAnalytics.setTotalAmount(catTotal);
			catAnalytics.setPercentageOfTotal(percentage);
			catAnalytics.setTransactionCount(count);
			catAnalytics.setAverageTransactionAmount(catAverage);
			catAnalytics.setPreviousMonthComparison(createComparison(catTotal, prevCatTotal));
			catAnalytics.setSameMonthLastYearComparison(createComparison(catTotal, lyCatTotal));

			catAnalyticsList.add(catAnalytics);
		}
		catAnalyticsList.sort((c1, c2) -> c2.getTotalAmount().compareTo(c1.getTotalAmount()));
		response.setCategoryAnalytics(catAnalyticsList);

		// Daily Analytics
		List<MonthlyAnalyticsResponse.DailyTotal> dailyTotals = new ArrayList<>();
		for (int d = 1; d <= daysInMonth; d++) {
			LocalDate date = startOfMonth.withDayOfMonth(d);
			BigDecimal amt = dailySums.getOrDefault(date, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

			MonthlyAnalyticsResponse.DailyTotal dailyTotal = new MonthlyAnalyticsResponse.DailyTotal();
			dailyTotal.setDate(date.toString());
			dailyTotal.setTotalAmount(amt);
			dailyTotals.add(dailyTotal);
		}

		Map<DayOfWeek, BigDecimal> weekdaySums = current.stream()
				.collect(Collectors.groupingBy(
						e -> e.getExpenseDate().getDayOfWeek(),
						Collectors.reducing(BigDecimal.ZERO, e -> getUsdAmount(e).multiply(rate), BigDecimal::add)
				));

		List<MonthlyAnalyticsResponse.WeekdayTotal> weekdayTotals = new ArrayList<>();
		for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
			BigDecimal amt = weekdaySums.getOrDefault(dayOfWeek, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

			MonthlyAnalyticsResponse.WeekdayTotal wt = new MonthlyAnalyticsResponse.WeekdayTotal();
			wt.setWeekday(dayOfWeek.name());
			wt.setTotalAmount(amt);
			weekdayTotals.add(wt);
		}

		MonthlyAnalyticsResponse.DailyAnalytics dailyAnalytics = new MonthlyAnalyticsResponse.DailyAnalytics();
		dailyAnalytics.setDailyTotals(dailyTotals);
		dailyAnalytics.setWeekdayTotals(weekdayTotals);
		response.setDailyAnalytics(dailyAnalytics);

		// Budget Analytics
		List<Budget> userBudgets = budgetRepository.findActiveBudgetsByUserId(userId);
		List<Budget> overlappingBudgets = userBudgets.stream()
				.filter(b -> !b.getStartDate().isAfter(endOfMonth) && (b.getEndDate() == null || !b.getEndDate().isBefore(startOfMonth)))
				.toList();

		BigDecimal totalBudgetAmount = BigDecimal.ZERO;
		BigDecimal totalBudgetSpent = BigDecimal.ZERO;

		for (Budget budget : overlappingBudgets) {
			if (budget.getBaseCurrencyAmount() != null) {
				totalBudgetAmount = totalBudgetAmount.add(budget.getBaseCurrencyAmount().multiply(rate));
			}
			// Sum expenses for this budget category in the selected month
			UUID catId = budget.getCategory().getId();
			BigDecimal spentInMonth = current.stream()
					.filter(e -> e.getCategory() != null && e.getCategory().getId().equals(catId))
					.map(e -> getUsdAmount(e).multiply(rate))
					.reduce(BigDecimal.ZERO, BigDecimal::add);

			totalBudgetSpent = totalBudgetSpent.add(spentInMonth);
		}

		totalBudgetAmount = totalBudgetAmount.setScale(2, RoundingMode.HALF_UP);
		totalBudgetSpent = totalBudgetSpent.setScale(2, RoundingMode.HALF_UP);

		MonthlyAnalyticsResponse.BudgetAnalytics budgetAnalytics = new MonthlyAnalyticsResponse.BudgetAnalytics();
		budgetAnalytics.setBudgetAmount(totalBudgetAmount);
		budgetAnalytics.setAmountSpent(totalBudgetSpent);
		budgetAnalytics.setRemainingAmount(totalBudgetAmount.subtract(totalBudgetSpent));
		budgetAnalytics.setBudgetUtilizationPercentage(totalBudgetAmount.compareTo(BigDecimal.ZERO) > 0
				? totalBudgetSpent.multiply(BigDecimal.valueOf(100)).divide(totalBudgetAmount, 2, RoundingMode.HALF_UP)
				: BigDecimal.ZERO);
		budgetAnalytics.setOverBudget(totalBudgetSpent.compareTo(totalBudgetAmount) > 0);
		response.setBudgetAnalytics(budgetAnalytics);

		// Insights
		MonthlyAnalyticsResponse.InsightsAnalytics insights = new MonthlyAnalyticsResponse.InsightsAnalytics();

		// Largest category
		catAnalyticsList.stream().findFirst().ifPresent(c -> {
			insights.setLargestCategoryName(c.getCategoryName());
			insights.setLargestCategoryAmount(c.getTotalAmount());
		});

		// Largest transaction
		current.stream()
				.max(Comparator.comparing(this::getUsdAmount))
				.ifPresent(e -> {
					insights.setLargestTransactionDescription(e.getDescription());
					insights.setLargestTransactionAmount(getUsdAmount(e).multiply(rate).setScale(2, RoundingMode.HALF_UP));
				});

		// Most frequent category
		catAnalyticsList.stream()
				.max(Comparator.comparingLong(MonthlyAnalyticsResponse.CategoryAnalytics::getTransactionCount))
				.ifPresent(c -> {
					insights.setMostFrequentCategoryName(c.getCategoryName());
					insights.setMostFrequentCategoryCount(c.getTransactionCount());
				});

		// Days with no transactions
		long daysWithTransactions = dailySums.entrySet().stream()
				.filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
				.count();
		insights.setNoSpendIncomeDaysCount((int) (daysInMonth - daysWithTransactions));

		// Category increases and decreases (compared to previous month)
		calculateCategoryChanges(insights, currentByCat, previous, rate);

		response.setInsights(insights);

		// Recent Transactions (top 10 descending)
		List<Expense> sortedCurrent = new ArrayList<>(current);
		sortedCurrent.sort((e1, e2) -> e2.getExpenseDate().compareTo(e1.getExpenseDate()));
		List<Expense> recent = sortedCurrent.stream().limit(10).toList();

		List<ExpenseResponse> recentResponses = recent.stream()
				.map(e -> {
					BigDecimal dispAmt = getUsdAmount(e).multiply(rate).setScale(2, RoundingMode.HALF_UP);
					return new ExpenseResponse(e, displayCurrency, dispAmt);
				})
				.toList();
		response.setRecentTransactions(recentResponses);
	}

	private void calculateIncomeAnalytics(
			MonthlyAnalyticsResponse response,
			List<Income> current,
			List<Income> previous,
			List<Income> lastYear,
			List<Object[]> historicalRaw,
			BigDecimal rate,
			String displayCurrency,
			int daysInMonth,
			LocalDate startOfMonth,
			LocalDate endOfMonth
	) {
		// Summary
		BigDecimal totalAmount = current.stream()
				.map(i -> getUsdAmount(i).multiply(rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		long totalTransactions = current.size();

		BigDecimal averageTransactionAmount = totalTransactions > 0
				? totalAmount.divide(BigDecimal.valueOf(totalTransactions), 2, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		BigDecimal highestTransactionAmount = current.stream()
				.map(i -> getUsdAmount(i).multiply(rate))
				.max(BigDecimal::compareTo)
				.orElse(BigDecimal.ZERO)
				.setScale(2, RoundingMode.HALF_UP);

		// Group by day for highest/lowest day
		Map<LocalDate, BigDecimal> dailySums = current.stream()
				.collect(Collectors.groupingBy(
						i -> i.getIncomeDate().toLocalDate(),
						Collectors.reducing(BigDecimal.ZERO, i -> getUsdAmount(i).multiply(rate), BigDecimal::add)
				));

		String highestEarningDay = dailySums.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(entry -> entry.getKey().toString())
				.orElse(null);

		String lowestEarningDay = dailySums.entrySet().stream()
				.min(Map.Entry.comparingByValue())
				.map(entry -> entry.getKey().toString())
				.orElse(null);

		BigDecimal averageAmountPerDay = totalAmount.divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);

		MonthlyAnalyticsResponse.SummaryAnalytics summary = new MonthlyAnalyticsResponse.SummaryAnalytics();
		summary.setTotalAmount(totalAmount);
		summary.setTotalTransactions(totalTransactions);
		summary.setAverageTransactionAmount(averageTransactionAmount);
		summary.setHighestTransactionAmount(highestTransactionAmount);
		summary.setHighestSpendingEarningDay(highestEarningDay);
		summary.setLowestSpendingEarningDay(lowestEarningDay);
		summary.setAverageAmountPerDay(averageAmountPerDay);
		response.setSummary(summary);

		// Comparisons
		BigDecimal prevTotal = previous.stream()
				.map(i -> getUsdAmount(i).multiply(rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal lastYearTotal = lastYear.stream()
				.map(i -> getUsdAmount(i).multiply(rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		MonthlyAnalyticsResponse.MonthComparisonsAnalytics comparisons = new MonthlyAnalyticsResponse.MonthComparisonsAnalytics();
		comparisons.setPreviousMonth(createComparison(totalAmount, prevTotal));
		comparisons.setSameMonthLastYear(createComparison(totalAmount, lastYearTotal));

		List<MonthlyAnalyticsResponse.HistoricalYearData> historical = new ArrayList<>();
		for (Object[] row : historicalRaw) {
			int y = ((Number) row[0]).intValue();
			BigDecimal tot = new BigDecimal(row[1].toString()).multiply(rate).setScale(2, RoundingMode.HALF_UP);
			long cnt = ((Number) row[2]).longValue();

			MonthlyAnalyticsResponse.HistoricalYearData histData = new MonthlyAnalyticsResponse.HistoricalYearData();
			histData.setYear(y);
			histData.setTotalAmount(tot);
			histData.setTransactionCount(cnt);
			historical.add(histData);
		}
		comparisons.setHistoricalData(historical);
		response.setMonthComparisons(comparisons);

		// Category Analytics
		Map<Category, List<Income>> currentByCat = current.stream()
				.filter(i -> i.getCategory() != null)
				.collect(Collectors.groupingBy(Income::getCategory));

		Map<UUID, BigDecimal> prevByCatId = previous.stream()
				.filter(i -> i.getCategory() != null)
				.collect(Collectors.groupingBy(
						i -> i.getCategory().getId(),
						Collectors.reducing(BigDecimal.ZERO, i -> getUsdAmount(i).multiply(rate), BigDecimal::add)
				));

		Map<UUID, BigDecimal> lastYearByCatId = lastYear.stream()
				.filter(i -> i.getCategory() != null)
				.collect(Collectors.groupingBy(
						i -> i.getCategory().getId(),
						Collectors.reducing(BigDecimal.ZERO, i -> getUsdAmount(i).multiply(rate), BigDecimal::add)
				));

		List<MonthlyAnalyticsResponse.CategoryAnalytics> catAnalyticsList = new ArrayList<>();
		for (Map.Entry<Category, List<Income>> entry : currentByCat.entrySet()) {
			Category cat = entry.getKey();
			List<Income> catIncomes = entry.getValue();

			BigDecimal catTotal = catIncomes.stream()
					.map(i -> getUsdAmount(i).multiply(rate))
					.reduce(BigDecimal.ZERO, BigDecimal::add)
					.setScale(2, RoundingMode.HALF_UP);

			long count = catIncomes.size();
			BigDecimal catAverage = catTotal.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
			BigDecimal percentage = totalAmount.compareTo(BigDecimal.ZERO) > 0
					? catTotal.multiply(BigDecimal.valueOf(100)).divide(totalAmount, 2, RoundingMode.HALF_UP)
					: BigDecimal.ZERO;

			BigDecimal prevCatTotal = prevByCatId.getOrDefault(cat.getId(), BigDecimal.ZERO);
			BigDecimal lyCatTotal = lastYearByCatId.getOrDefault(cat.getId(), BigDecimal.ZERO);

			MonthlyAnalyticsResponse.CategoryAnalytics catAnalytics = new MonthlyAnalyticsResponse.CategoryAnalytics();
			catAnalytics.setCategoryId(cat.getId().toString());
			catAnalytics.setCategoryName(cat.getName());
			catAnalytics.setCategoryColor(cat.getColor());
			catAnalytics.setCategoryIcon(cat.getIcon());
			catAnalytics.setTotalAmount(catTotal);
			catAnalytics.setPercentageOfTotal(percentage);
			catAnalytics.setTransactionCount(count);
			catAnalytics.setAverageTransactionAmount(catAverage);
			catAnalytics.setPreviousMonthComparison(createComparison(catTotal, prevCatTotal));
			catAnalytics.setSameMonthLastYearComparison(createComparison(catTotal, lyCatTotal));

			catAnalyticsList.add(catAnalytics);
		}
		catAnalyticsList.sort((c1, c2) -> c2.getTotalAmount().compareTo(c1.getTotalAmount()));
		response.setCategoryAnalytics(catAnalyticsList);

		// Daily Analytics
		List<MonthlyAnalyticsResponse.DailyTotal> dailyTotals = new ArrayList<>();
		for (int d = 1; d <= daysInMonth; d++) {
			LocalDate date = startOfMonth.withDayOfMonth(d);
			BigDecimal amt = dailySums.getOrDefault(date, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

			MonthlyAnalyticsResponse.DailyTotal dailyTotal = new MonthlyAnalyticsResponse.DailyTotal();
			dailyTotal.setDate(date.toString());
			dailyTotal.setTotalAmount(amt);
			dailyTotals.add(dailyTotal);
		}

		Map<DayOfWeek, BigDecimal> weekdaySums = current.stream()
				.collect(Collectors.groupingBy(
						i -> i.getIncomeDate().getDayOfWeek(),
						Collectors.reducing(BigDecimal.ZERO, i -> getUsdAmount(i).multiply(rate), BigDecimal::add)
				));

		List<MonthlyAnalyticsResponse.WeekdayTotal> weekdayTotals = new ArrayList<>();
		for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
			BigDecimal amt = weekdaySums.getOrDefault(dayOfWeek, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

			MonthlyAnalyticsResponse.WeekdayTotal wt = new MonthlyAnalyticsResponse.WeekdayTotal();
			wt.setWeekday(dayOfWeek.name());
			wt.setTotalAmount(amt);
			weekdayTotals.add(wt);
		}

		MonthlyAnalyticsResponse.DailyAnalytics dailyAnalytics = new MonthlyAnalyticsResponse.DailyAnalytics();
		dailyAnalytics.setDailyTotals(dailyTotals);
		dailyAnalytics.setWeekdayTotals(weekdayTotals);
		response.setDailyAnalytics(dailyAnalytics);

		// Budget Analytics is null for Income
		response.setBudgetAnalytics(null);

		// Insights
		MonthlyAnalyticsResponse.InsightsAnalytics insights = new MonthlyAnalyticsResponse.InsightsAnalytics();

		// Largest category
		catAnalyticsList.stream().findFirst().ifPresent(c -> {
			insights.setLargestCategoryName(c.getCategoryName());
			insights.setLargestCategoryAmount(c.getTotalAmount());
		});

		// Largest transaction
		current.stream()
				.max(Comparator.comparing(this::getUsdAmount))
				.ifPresent(i -> {
					insights.setLargestTransactionDescription(i.getDescription());
					insights.setLargestTransactionAmount(getUsdAmount(i).multiply(rate).setScale(2, RoundingMode.HALF_UP));
				});

		// Most frequent category
		catAnalyticsList.stream()
				.max(Comparator.comparingLong(MonthlyAnalyticsResponse.CategoryAnalytics::getTransactionCount))
				.ifPresent(c -> {
					insights.setMostFrequentCategoryName(c.getCategoryName());
					insights.setMostFrequentCategoryCount(c.getTransactionCount());
				});

		// Days with no transactions
		long daysWithTransactions = dailySums.entrySet().stream()
				.filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
				.count();
		insights.setNoSpendIncomeDaysCount((int) (daysInMonth - daysWithTransactions));

		// Category increases and decreases (compared to previous month)
		calculateCategoryChangesIncome(insights, currentByCat, previous, rate);

		response.setInsights(insights);

		// Recent Transactions (top 10 descending)
		List<Income> sortedCurrent = new ArrayList<>(current);
		sortedCurrent.sort((i1, i2) -> i2.getIncomeDate().compareTo(i1.getIncomeDate()));
		List<Income> recent = sortedCurrent.stream().limit(10).toList();

		List<IncomeResponse> recentResponses = recent.stream()
				.map(i -> {
					BigDecimal dispAmt = getUsdAmount(i).multiply(rate).setScale(2, RoundingMode.HALF_UP);
					return new IncomeResponse(i, displayCurrency, dispAmt);
				})
				.toList();
		response.setRecentTransactions(recentResponses);
	}

	private void calculateIncomeVsExpenseSummary(
			MonthlyAnalyticsResponse response,
			List<Expense> selectedMonthExpenses,
			List<Income> selectedMonthIncomes,
			BigDecimal rate
	) {
		BigDecimal totalIncome = selectedMonthIncomes.stream()
				.map(i -> getUsdAmount(i).multiply(rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal totalExpense = selectedMonthExpenses.stream()
				.map(e -> getUsdAmount(e).multiply(rate))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		BigDecimal netSavings = totalIncome.subtract(totalExpense);

		BigDecimal savingsPercentage = totalIncome.compareTo(BigDecimal.ZERO) > 0
				? netSavings.multiply(BigDecimal.valueOf(100)).divide(totalIncome, 2, RoundingMode.HALF_UP)
				: BigDecimal.ZERO;

		MonthlyAnalyticsResponse.IncomeVsExpenseSummary ive = new MonthlyAnalyticsResponse.IncomeVsExpenseSummary();
		ive.setTotalIncome(totalIncome);
		ive.setTotalExpense(totalExpense);
		ive.setNetSavings(netSavings);
		ive.setSavingsPercentage(savingsPercentage);

		response.setIncomeVsExpenseSummary(ive);
	}

	private MonthlyAnalyticsResponse.ComparisonDetail createComparison(BigDecimal currentVal, BigDecimal prevVal) {
		MonthlyAnalyticsResponse.ComparisonDetail comparison = new MonthlyAnalyticsResponse.ComparisonDetail();
		comparison.setTotalAmount(prevVal);
		comparison.setDifferenceAmount(currentVal.subtract(prevVal));

		if (prevVal.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal change = currentVal.subtract(prevVal)
					.multiply(BigDecimal.valueOf(100))
					.divide(prevVal, 2, RoundingMode.HALF_UP);
			comparison.setPercentageChange(change);
		} else {
			comparison.setPercentageChange(null);
		}
		return comparison;
	}

	private void calculateCategoryChanges(
			MonthlyAnalyticsResponse.InsightsAnalytics insights,
			Map<Category, List<Expense>> currentByCat,
			List<Expense> previous,
			BigDecimal rate
	) {
		Map<String, BigDecimal> currentTotals = currentByCat.entrySet().stream()
				.collect(Collectors.toMap(
						entry -> entry.getKey().getName(),
						entry -> entry.getValue().stream()
								.map(e -> getUsdAmount(e).multiply(rate))
								.reduce(BigDecimal.ZERO, BigDecimal::add)
				));

		Map<String, BigDecimal> prevTotals = previous.stream()
				.filter(e -> e.getCategory() != null)
				.collect(Collectors.groupingBy(
						e -> e.getCategory().getName(),
						Collectors.reducing(BigDecimal.ZERO, e -> getUsdAmount(e).multiply(rate), BigDecimal::add)
				));

		Set<String> allCategories = new HashSet<>();
		allCategories.addAll(currentTotals.keySet());
		allCategories.addAll(prevTotals.keySet());

		String biggestIncreaseCategoryName = null;
		BigDecimal maxIncrease = BigDecimal.valueOf(-Double.MAX_VALUE);
		String biggestDecreaseCategoryName = null;
		BigDecimal maxDecrease = BigDecimal.valueOf(Double.MAX_VALUE);

		for (String catName : allCategories) {
			BigDecimal cur = currentTotals.getOrDefault(catName, BigDecimal.ZERO);
			BigDecimal prev = prevTotals.getOrDefault(catName, BigDecimal.ZERO);
			BigDecimal diff = cur.subtract(prev);

			if (diff.compareTo(BigDecimal.ZERO) > 0 && diff.compareTo(maxIncrease) > 0) {
				maxIncrease = diff;
				biggestIncreaseCategoryName = catName;
			}
			if (diff.compareTo(BigDecimal.ZERO) < 0 && diff.compareTo(maxDecrease) < 0) {
				maxDecrease = diff;
				biggestDecreaseCategoryName = catName;
			}
		}

		if (biggestIncreaseCategoryName != null) {
			MonthlyAnalyticsResponse.CategoryChange increase = new MonthlyAnalyticsResponse.CategoryChange();
			increase.setCategoryName(biggestIncreaseCategoryName);
			increase.setDifferenceAmount(maxIncrease.setScale(2, RoundingMode.HALF_UP));
			BigDecimal prevAmt = prevTotals.getOrDefault(biggestIncreaseCategoryName, BigDecimal.ZERO);
			increase.setPercentageChange(prevAmt.compareTo(BigDecimal.ZERO) > 0
					? maxIncrease.multiply(BigDecimal.valueOf(100)).divide(prevAmt, 2, RoundingMode.HALF_UP)
					: null);
			insights.setBiggestIncreaseCategory(increase);
		}

		if (biggestDecreaseCategoryName != null) {
			MonthlyAnalyticsResponse.CategoryChange decrease = new MonthlyAnalyticsResponse.CategoryChange();
			decrease.setCategoryName(biggestDecreaseCategoryName);
			decrease.setDifferenceAmount(maxDecrease.setScale(2, RoundingMode.HALF_UP));
			BigDecimal prevAmt = prevTotals.getOrDefault(biggestDecreaseCategoryName, BigDecimal.ZERO);
			decrease.setPercentageChange(prevAmt.compareTo(BigDecimal.ZERO) > 0
					? maxDecrease.multiply(BigDecimal.valueOf(100)).divide(prevAmt, 2, RoundingMode.HALF_UP)
					: null);
			insights.setBiggestDecreaseCategory(decrease);
		}
	}

	private void calculateCategoryChangesIncome(
			MonthlyAnalyticsResponse.InsightsAnalytics insights,
			Map<Category, List<Income>> currentByCat,
			List<Income> previous,
			BigDecimal rate
	) {
		Map<String, BigDecimal> currentTotals = currentByCat.entrySet().stream()
				.collect(Collectors.toMap(
						entry -> entry.getKey().getName(),
						entry -> entry.getValue().stream()
								.map(i -> getUsdAmount(i).multiply(rate))
								.reduce(BigDecimal.ZERO, BigDecimal::add)
				));

		Map<String, BigDecimal> prevTotals = previous.stream()
				.filter(i -> i.getCategory() != null)
				.collect(Collectors.groupingBy(
						i -> i.getCategory().getName(),
						Collectors.reducing(BigDecimal.ZERO, i -> getUsdAmount(i).multiply(rate), BigDecimal::add)
				));

		Set<String> allCategories = new HashSet<>();
		allCategories.addAll(currentTotals.keySet());
		allCategories.addAll(prevTotals.keySet());

		String biggestIncreaseCategoryName = null;
		BigDecimal maxIncrease = BigDecimal.valueOf(-Double.MAX_VALUE);
		String biggestDecreaseCategoryName = null;
		BigDecimal maxDecrease = BigDecimal.valueOf(Double.MAX_VALUE);

		for (String catName : allCategories) {
			BigDecimal cur = currentTotals.getOrDefault(catName, BigDecimal.ZERO);
			BigDecimal prev = prevTotals.getOrDefault(catName, BigDecimal.ZERO);
			BigDecimal diff = cur.subtract(prev);

			if (diff.compareTo(BigDecimal.ZERO) > 0 && diff.compareTo(maxIncrease) > 0) {
				maxIncrease = diff;
				biggestIncreaseCategoryName = catName;
			}
			if (diff.compareTo(BigDecimal.ZERO) < 0 && diff.compareTo(maxDecrease) < 0) {
				maxDecrease = diff;
				biggestDecreaseCategoryName = catName;
			}
		}

		if (biggestIncreaseCategoryName != null) {
			MonthlyAnalyticsResponse.CategoryChange increase = new MonthlyAnalyticsResponse.CategoryChange();
			increase.setCategoryName(biggestIncreaseCategoryName);
			increase.setDifferenceAmount(maxIncrease.setScale(2, RoundingMode.HALF_UP));
			BigDecimal prevAmt = prevTotals.getOrDefault(biggestIncreaseCategoryName, BigDecimal.ZERO);
			increase.setPercentageChange(prevAmt.compareTo(BigDecimal.ZERO) > 0
					? maxIncrease.multiply(BigDecimal.valueOf(100)).divide(prevAmt, 2, RoundingMode.HALF_UP)
					: null);
			insights.setBiggestIncreaseCategory(increase);
		}

		if (biggestDecreaseCategoryName != null) {
			MonthlyAnalyticsResponse.CategoryChange decrease = new MonthlyAnalyticsResponse.CategoryChange();
			decrease.setCategoryName(biggestDecreaseCategoryName);
			decrease.setDifferenceAmount(maxDecrease.setScale(2, RoundingMode.HALF_UP));
			BigDecimal prevAmt = prevTotals.getOrDefault(biggestDecreaseCategoryName, BigDecimal.ZERO);
			decrease.setPercentageChange(prevAmt.compareTo(BigDecimal.ZERO) > 0
					? maxDecrease.multiply(BigDecimal.valueOf(100)).divide(prevAmt, 2, RoundingMode.HALF_UP)
					: null);
			insights.setBiggestDecreaseCategory(decrease);
		}
	}
}
