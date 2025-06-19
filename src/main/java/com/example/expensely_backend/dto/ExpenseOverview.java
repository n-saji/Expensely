package com.example.expensely_backend.dto;

import lombok.Getter;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.round;


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
    private final Map<String, Map<String, Double>> monthlyCategoryExpense;


    @Getter
    private final Map<String, Double> topFiveMostExpensiveItemThisMonth;


    public ExpenseOverview(List<ExpenseResponse> expenses, String userId, List<MonthlyCategoryExpense> monthlyCategoryExpenses) {
        this.userId = userId;
        this.TotalAmount = expenses.stream().mapToDouble(ExpenseResponse::getAmount).sum();
        Map<String, Double> rawSums = expenses.stream()
                .collect(Collectors.groupingBy(
                        ExpenseResponse::getCategoryName,
                        Collectors.summingDouble(ExpenseResponse::getAmount)
                ));
        this.amountByCategory = rawSums.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> round(entry.getValue() * 100.0) / 100.0
                ));
        this.totalCount = expenses.size();

        Map<Month, Double> monthMap = expenses.stream().collect(Collectors.groupingBy(expense -> expense.getExpenseDate().getMonth(), TreeMap::new, Collectors.summingDouble(ExpenseResponse::getAmount)));
        this.amountByMonth = monthMap.entrySet().stream().
                collect(Collectors.toMap(entry -> entry.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH), entry -> round(entry.getValue() * 100.0) / 100.0, (a, b) -> a, LinkedHashMap::new));


        this.categoryCount = expenses.stream().collect(Collectors.groupingBy(ExpenseResponse::getCategoryName, Collectors.counting()));
        this.mostFrequentCategoryCount = categoryCount.values().stream().max(Long::compare).orElse(0L).intValue();

        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);
        this.thisMonthTotalExpense = round(monthMap.getOrDefault(Month.of(currentMonth + 1), 0.0) * 100.0) / 100.0;
        this.comparedToLastMonthExpense = round((thisMonthTotalExpense - monthMap.getOrDefault(Month.of(currentMonth), 0.0)) * 100.0) / 100.0;
        this.totalCategories = amountByCategory.size();
        this.mostFrequentCategory = categoryCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        this.averageMonthlyExpense = TotalAmount / (amountByMonth.isEmpty() ? 1 : amountByMonth.size());
        this.topFiveMostExpensiveItemThisMonth = expenses.stream()
                .filter(expense -> expense.getExpenseDate().getMonth() == Month.of(currentMonth + 1))
                .sorted(Comparator.comparingDouble(ExpenseResponse::getAmount).reversed())
                .limit(5) //only 5 is required
                .collect(Collectors.toMap(ExpenseResponse::getDescription, ExpenseResponse::getAmount, (a, b) -> a, LinkedHashMap::new));


        this.monthlyCategoryExpense = new LinkedHashMap<>();

        monthlyCategoryExpenses.sort(Comparator.comparingInt(dto ->
                Month.valueOf(dto.getMonth().toUpperCase()).getValue()
        ));

        for (MonthlyCategoryExpense dto : monthlyCategoryExpenses) {
            String month = dto.getMonth().trim();
            String category = dto.getCategoryName();
            Double amount = dto.getTotalAmount();

            if (category == null) {
                System.err.println("Null value found: month=" + month + ", category=" + category);
                continue; // skip
            }

            monthlyCategoryExpense
                    .computeIfAbsent(month, k -> new LinkedHashMap<>())
                    .merge(category, amount, Double::sum);
        }
    }


}
