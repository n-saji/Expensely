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


    public ExpenseOverview(List<ExpenseResponse> expenses, String userId) {
        this.userId = userId;
        this.TotalAmount = expenses.stream()
                .mapToDouble(ExpenseResponse::getAmount)
                .sum();
        this.amountByCategory = expenses.stream().collect(Collectors.groupingBy(
                ExpenseResponse::getCategoryName,
                Collectors.summingDouble(ExpenseResponse::getAmount)
        ));
        this.totalCount = expenses.size();
//        this.amountByMonth = expenses.stream().collect(Collectors.groupingBy(
//                expense -> expense.getExpenseDate().getMonth(),
//                TreeMap::new,
//                Collectors.summingDouble(ExpenseResponse::getAmount)
//        ));
        Map<Month, Double> monthMap = expenses.stream().collect(Collectors.groupingBy(
                expense -> expense.getExpenseDate().getMonth(),
                TreeMap::new,
                Collectors.summingDouble(ExpenseResponse::getAmount)
        ));

        this.amountByMonth = monthMap.entrySet().stream().collect(Collectors.toMap(
                entry -> entry.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                Map.Entry::getValue,
                (a, b) -> a,
                LinkedHashMap::new
        ));


    }


}
