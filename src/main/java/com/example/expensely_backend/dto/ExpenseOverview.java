package com.example.expensely_backend.dto;

import lombok.Getter;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;


public class ExpenseOverview {
    @Getter
    private final String userId;
    @Getter
    private final Double TotalAmount;
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
    private final Double thisMonthTotalExpense;

    @Getter
    private final Double comparedToLastMonthExpense;

    @Getter
    private final Map<String, Long> categoryCount;

    @Getter
    private final Double averageMonthlyExpense;

    @Getter
    private final Map<String, Double> topTenMostExpenseiveItemThisMonth;


    public ExpenseOverview(List<ExpenseResponse> expenses, String userId) {
        this.userId = userId;
        this.TotalAmount = expenses.stream().mapToDouble(ExpenseResponse::getAmount).sum();
        this.amountByCategory = expenses.stream().collect(Collectors.groupingBy(ExpenseResponse::getCategoryName, Collectors.summingDouble(ExpenseResponse::getAmount)));
        this.totalCount = expenses.size();

        Map<Month, Double> monthMap = expenses.stream().collect(Collectors.groupingBy(expense -> expense.getExpenseDate().getMonth(), TreeMap::new, Collectors.summingDouble(ExpenseResponse::getAmount)));

        this.amountByMonth = monthMap.entrySet().stream().
                collect(Collectors.toMap(entry -> entry.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        this.categoryCount = expenses.stream().collect(Collectors.groupingBy(ExpenseResponse::getCategoryName, Collectors.counting()));
        this.mostFrequentCategoryCount = categoryCount.values().stream().max(Long::compare).orElse(0L).intValue();

        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);
        this.thisMonthTotalExpense = monthMap.getOrDefault(Month.of(currentMonth + 1), 0.0);
        this.comparedToLastMonthExpense = thisMonthTotalExpense - monthMap.getOrDefault(Month.of(currentMonth), 0.0);
        this.totalCategories = amountByCategory.size();
        this.mostFrequentCategory = categoryCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        this.averageMonthlyExpense = TotalAmount / (amountByMonth.size() == 0 ? 1 : amountByMonth.size());
        this.topTenMostExpenseiveItemThisMonth = expenses.stream()
                .filter(expense -> expense.getExpenseDate().getMonth() == Month.of(currentMonth + 1))
                .sorted(Comparator.comparingDouble(ExpenseResponse::getAmount).reversed())
                .limit(10)
                .collect(Collectors.toMap(ExpenseResponse::getDescription, ExpenseResponse::getAmount, (a, b) -> a, LinkedHashMap::new));
    }


}
