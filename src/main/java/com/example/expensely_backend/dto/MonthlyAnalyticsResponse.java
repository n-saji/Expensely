package com.example.expensely_backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class MonthlyAnalyticsResponse {
	private SummaryAnalytics summary;
	private MonthComparisonsAnalytics monthComparisons;
	private List<CategoryAnalytics> categoryAnalytics;
	private DailyAnalytics dailyAnalytics;
	private BudgetAnalytics budgetAnalytics; // null if Type is Income
	private InsightsAnalytics insights;
	private IncomeVsExpenseSummary incomeVsExpenseSummary;
	private List<?> recentTransactions; // list of ExpenseResponse or IncomeResponse

	@Data
	public static class SummaryAnalytics {
		private BigDecimal totalAmount;
		private long totalTransactions;
		private BigDecimal averageTransactionAmount;
		private BigDecimal highestTransactionAmount;
		private String highestSpendingEarningDay; // "YYYY-MM-DD"
		private String lowestSpendingEarningDay;  // "YYYY-MM-DD"
		private BigDecimal averageAmountPerDay;
	}

	@Data
	public static class MonthComparisonsAnalytics {
		private ComparisonDetail previousMonth;
		private ComparisonDetail sameMonthLastYear;
		private List<HistoricalYearData> historicalData;
	}

	@Data
	public static class ComparisonDetail {
		private BigDecimal totalAmount;
		private BigDecimal percentageChange; // null if not applicable/0
		private BigDecimal differenceAmount;
	}

	@Data
	public static class HistoricalYearData {
		private int year;
		private BigDecimal totalAmount;
		private long transactionCount;
	}

	@Data
	public static class CategoryAnalytics {
		private String categoryId;
		private String categoryName;
		private String categoryColor;
		private String categoryIcon;
		private BigDecimal totalAmount;
		private BigDecimal percentageOfTotal;
		private long transactionCount;
		private BigDecimal averageTransactionAmount;
		private ComparisonDetail previousMonthComparison;
		private ComparisonDetail sameMonthLastYearComparison;
	}

	@Data
	public static class DailyAnalytics {
		private List<DailyTotal> dailyTotals;
		private List<WeekdayTotal> weekdayTotals;
	}

	@Data
	public static class DailyTotal {
		private String date; // "YYYY-MM-DD"
		private BigDecimal totalAmount;
	}

	@Data
	public static class WeekdayTotal {
		private String weekday; // "MONDAY", "TUESDAY", etc.
		private BigDecimal totalAmount;
	}

	@Data
	public static class BudgetAnalytics {
		private BigDecimal budgetAmount;
		private BigDecimal amountSpent;
		private BigDecimal remainingAmount;
		private BigDecimal budgetUtilizationPercentage;
		private boolean overBudget;
	}

	@Data
	public static class InsightsAnalytics {
		private String largestCategoryName;
		private BigDecimal largestCategoryAmount;
		private String largestTransactionDescription;
		private BigDecimal largestTransactionAmount;
		private String mostFrequentCategoryName;
		private long mostFrequentCategoryCount;
		private int noSpendIncomeDaysCount;
		private CategoryChange biggestIncreaseCategory;
		private CategoryChange biggestDecreaseCategory;
	}

	@Data
	public static class CategoryChange {
		private String categoryName;
		private BigDecimal differenceAmount;
		private BigDecimal percentageChange;
	}

	@Data
	public static class IncomeVsExpenseSummary {
		private BigDecimal totalIncome;
		private BigDecimal totalExpense;
		private BigDecimal netSavings;
		private BigDecimal savingsPercentage; // null if totalIncome is 0
	}
}
