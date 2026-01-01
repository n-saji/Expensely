package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.Budget;
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
    private final Map<String, Long> categoryCount;
    @Getter
    private final Double averageMonthlyExpense;
    @Getter
    private final Map<String, Map<String, Double>> monthlyCategoryExpense;
    @Getter
    private final Map<String, Double> topFiveMostExpensiveItemThisMonth;
    @Getter
    private final Map<String, Double> overTheDaysThisMonth;
    @Getter
    private final Integer earliestStartMonth, earliestStartYear;
    @Getter
    private final Map<String, Double> thisMonthMostExpensiveItem;
    @Getter
    private final Map<String, Budget> budgetServiceMap;
    @Getter
    private Double lastMonthTotalExpense = 0.0;


    public ExpenseOverview(List<ExpenseResponse> expenses, List<ExpenseResponse> req_expenses_range, List<ExpenseResponse> req_expenses_range_monthly,
                           String userId, List<MonthlyCategoryExpense> monthlyCategoryExpenses,
                           Iterable<Category> categories, List<DailyExpense> dailyExpenses,
                           ExpenseResList FirstExpense, Integer reqMonth,
                           List<Budget> budgetServiceMap,
                           Double LastMonthTotalExpense) {
        //  Overview page
        this.userId = userId;
        this.totalCount = expenses.size();
        this.categoryCount = expenses.stream().collect(Collectors.groupingBy(ExpenseResponse::getCategoryName, Collectors.counting()));
        this.mostFrequentCategoryCount = categoryCount.values().stream().max(Long::compare).orElse(0L).intValue();
        this.totalCategories = categoryCount.size();

        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);

        Map<Month, Double> monthMap = expenses.stream().collect(Collectors.groupingBy(expense -> expense.getExpenseDate().getMonth(), TreeMap::new, Collectors.summingDouble(ExpenseResponse::getAmount)));
        this.thisMonthTotalExpense = round(monthMap.getOrDefault(Month.of(currentMonth + 1), 0.0) * 100.0) / 100.0;
        this.lastMonthTotalExpense = LastMonthTotalExpense;
        this.TotalAmount = expenses.stream().mapToDouble(ExpenseResponse::getAmount).sum();
        this.averageMonthlyExpense = TotalAmount / (currentMonth + 1);
        this.mostFrequentCategory = categoryCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (FirstExpense != null && FirstExpense.expenses() != null && !FirstExpense.expenses().isEmpty()) {
            this.earliestStartMonth = FirstExpense.expenses().get(0).getExpenseDate().getMonthValue();
            this.earliestStartYear = FirstExpense.expenses().get(0).getExpenseDate().getYear();
        } else {
            this.earliestStartMonth = null;
            this.earliestStartYear = null;
        }

        ExpenseResponse mostExpensive = expenses.stream()
                .filter(expense -> expense.getExpenseDate().getMonthValue() == (currentMonth + 1))
                .max(Comparator.comparingDouble(ExpenseResponse::getAmount))
                .orElse(null);

        if (mostExpensive != null) {
            this.thisMonthMostExpensiveItem = Map.of(
                    mostExpensive.getDescription(),  // or category, description, etc.
                    mostExpensive.getAmount()
            );
        } else {
            this.thisMonthMostExpensiveItem = Map.of(); // empty map
        }


        // Requested Yearly view
        Map<Month, Double> monthMapReq = req_expenses_range.stream().collect(Collectors.groupingBy(expense -> expense.getExpenseDate().getMonth(), TreeMap::new, Collectors.summingDouble(ExpenseResponse::getAmount)));
        this.amountByMonth = monthMapReq.entrySet().stream().
                collect(Collectors.toMap(entry -> entry.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH), entry -> round(entry.getValue() * 100.0) / 100.0, (a, b) -> a, LinkedHashMap::new));

        Map<String, Double> rawSums = req_expenses_range.stream()
                .collect(Collectors.groupingBy(
                        ExpenseResponse::getCategoryName,
                        Collectors.summingDouble(ExpenseResponse::getAmount)
                ));
        this.amountByCategory = rawSums.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> round(entry.getValue() * 100.0) / 100.0
                ));
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


        // Monthly requested data
        this.topFiveMostExpensiveItemThisMonth = req_expenses_range_monthly.stream()
                .filter(expense -> expense.getExpenseDate().getMonth() == Month.of(reqMonth))
                .sorted(Comparator.comparingDouble(ExpenseResponse::getAmount).reversed())
                .limit(5) //only 5 is required
                .collect(Collectors.toMap(ExpenseResponse::getDescription, ExpenseResponse::getAmount, (a, b) -> a, LinkedHashMap::new));

        this.overTheDaysThisMonth = new LinkedHashMap<>();
        for (DailyExpense dailyExpense : dailyExpenses) {
            double amount = dailyExpense.getTotalAmount() != null ? dailyExpense.getTotalAmount() : 0.0;

            LocalDate date = LocalDate.parse(dailyExpense.getExpenseDate()); // safer
            String day = String.valueOf(date.getDayOfMonth());

            overTheDaysThisMonth.put(day, round(amount * 100.0) / 100.0);
        }

        this.budgetServiceMap = new LinkedHashMap<>();

        for (Budget budget : budgetServiceMap) {
            budget.setUser(null);
            this.budgetServiceMap.put(budget.getId().toString(), budget);
        }


    }


}
