package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Category;
import lombok.Getter;

import java.time.LocalDate;
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
    private final Double lastMonthTotalExpense;

    @Getter
    private final Map<String, Long> categoryCount;

    @Getter
    private final Double averageMonthlyExpense;

    @Getter
    private final Map<String, Map<String, Double>> monthlyCategoryExpense;


    @Getter
    private final Map<String, Double> topFiveMostExpensiveItemThisMonth;

    @Getter
    private final Map<String,Double> overTheDaysThisMonth;


    public ExpenseOverview(List<ExpenseResponse> expenses, String userId, List<MonthlyCategoryExpense> monthlyCategoryExpenses, Iterable<Category> categories, List<DailyExpense> dailyExpenses) {
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
        this.lastMonthTotalExpense = round((monthMap.getOrDefault(Month.of(currentMonth), 0.0)) * 100.0) / 100.0;
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

            monthlyCategoryExpense.computeIfAbsent(month, k -> {
                Map<String, Double> categoryMap = new LinkedHashMap<>();
                for (Category cat : categories) {
                    categoryMap.put(cat.getName(), 0.0);
                }
                return categoryMap;
            });

            String category = dto.getCategoryName();
            Double amount = dto.getTotalAmount();

            if (category != null) {
                monthlyCategoryExpense.get(month).merge(category, amount, Double::sum);
            }
        }

        double totalSum = 0.0;
        this.overTheDaysThisMonth = new LinkedHashMap<>();

        for (DailyExpense dailyExpense : dailyExpenses) {
            double amount = dailyExpense.getTotalAmount() != null ? dailyExpense.getTotalAmount() : 0.0;
            totalSum += amount;

            LocalDate date = LocalDate.parse(dailyExpense.getExpenseDate()); // safer
            String day = String.valueOf(date.getDayOfMonth());

            overTheDaysThisMonth.put(day, round(totalSum * 100.0) / 100.0);
        }

    }


}
