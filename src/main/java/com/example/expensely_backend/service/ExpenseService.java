package com.example.expensely_backend.service;


import com.example.expensely_backend.dto.DailyExpense;
import com.example.expensely_backend.dto.ExpenseResList;
import com.example.expensely_backend.dto.ExpenseResponse;
import com.example.expensely_backend.dto.MonthlyCategoryExpense;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.Expense;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.ExpenseRepository;
import com.example.expensely_backend.repository.ExpenseRepositoryCustomImpl;
import com.opencsv.CSVWriter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryService categoryService;
    private final UserService userService;
    private final BudgetService budgetService;
    private final ExpenseRepositoryCustomImpl expenseRepositoryCustomImpl;

    public ExpenseService(ExpenseRepository expenseRepository, CategoryService categoryService,
                          UserService userService,
                          BudgetService budgetService, ExpenseRepositoryCustomImpl expenseRepositoryCustomImpl) {
        this.expenseRepository = expenseRepository;
        this.categoryService = categoryService;
        this.userService = userService;
        this.budgetService = budgetService;
        this.expenseRepositoryCustomImpl = expenseRepositoryCustomImpl;
    }

    public void save(Expense expense) {
        if (expense.getCategory() == null || expense.getCategory().getId() == null) {
            throw new IllegalArgumentException("Category must be provided");
        }
        Category category = categoryService.getCategoryById(expense.getCategory().getId().toString());
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        expense.setCategory(category);
        if (expense.getUser() == null || expense.getUser().getId() == null) {
            throw new IllegalArgumentException("User must be provided");
        }
        User user = userService.GetActiveUserById(expense.getUser().getId().toString());
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        expense.setUser(user);
        expenseRepository.save(expense);


        // calculate if budget set

        try {
            budgetService.updateBudgetAmountByUserIdAndCategoryId(user.getId().toString(), category.getId().toString(), expense.getAmount(), expense.getExpenseDate());
        } catch (Exception e) {
            throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
        }

    }

    public Expense getExpenseById(String id) {
        return expenseRepository.findById(UUID.fromString(id)).orElseThrow(() -> new IllegalArgumentException("Expense not found"));
    }

    public void deleteExpenseById(String id) {
        try {
            if (!expenseRepository.existsById(UUID.fromString(id))) {
                throw new IllegalArgumentException("Expense not found");
            }
            expenseRepository.deleteById(UUID.fromString(id));
//            budget update
            Expense expense = getExpenseById(id);
            try {
                budgetService.updateBudgetAmountByUserIdAndCategoryId(expense.getUser().getId().toString(), expense.getCategory().getId().toString(), expense.getAmount().negate(), expense.getExpenseDate());
            } catch (Exception e) {
                throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error deleting expense: " + e.getMessage());
        }
    }

    public Iterable<Expense> getExpensesByUserId(String userId) {
        User user = userService.GetActiveUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return expenseRepository.findByUserId(user.getId());
    }

    public List<Expense> getExpensesByCategoryIdAndUserID(String categoryId, String userId) {
        Category category = categoryService.getCategoryById(categoryId);
        if (category == null) {
            throw new IllegalArgumentException("Category not found");
        }
        User user = userService.GetActiveUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        return expenseRepository.findByCategoryIdAndUserId(category.getId(), user.getId());
    }

    public Expense updateExpense(Expense expense) {

        Expense oldExpense = getExpenseById(expense.getId().toString());
        BigDecimal changed_amount = expense.getAmount().subtract(oldExpense.getAmount());

        if (expense.getCategory() != null && expense.getCategory().getId() != null && !expense.getCategory().getId().equals(oldExpense.getCategory().getId())) {
            Category category = categoryService.getCategoryById(expense.getCategory().getId().toString());
            if (category == null) {
                throw new IllegalArgumentException("Category not found");
            }
            oldExpense.setCategory(category);
        }


        if (!expenseRepository.existsById(expense.getId())) {
            throw new IllegalArgumentException("Expense not found");
        }


        if (expense.getAmount() != null && expense.getAmount().compareTo(oldExpense.getAmount()) != 0) {
            oldExpense.setAmount(expense.getAmount());
        }
        if (expense.getDescription() != null && !expense.getDescription().equals(oldExpense.getDescription())) {
            oldExpense.setDescription(expense.getDescription());
        }
        if (expense.getExpenseDate() != null && !expense.getExpenseDate().equals(oldExpense.getExpenseDate())) {
            oldExpense.setExpenseDate(expense.getExpenseDate());
        }


        Expense exp = expenseRepository.save(oldExpense);

//        update budget
        try {
            budgetService.updateBudgetAmountByUserIdAndCategoryId(exp.getUser().getId().toString(), exp.getCategory().getId().toString(), changed_amount, exp.getExpenseDate());
        } catch (Exception e) {
            throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
        }
        return exp;
    }

    public List<ExpenseResponse> getExpenseByUserIdAndStartDateAndEndDate(String userId, LocalDateTime startDate, LocalDateTime endDate, String order) {
        User user = userService.GetActiveUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        List<Expense> expenses;
        if (order == null || order.equalsIgnoreCase("desc")) {
            expenses = expenseRepository.findByUserIdAndTimeFrameDesc(user.getId(), startDate, endDate);
        } else if (order.equalsIgnoreCase("asc")) {
            expenses = expenseRepository.findByUserIdAndTimeFrameAsc(user.getId(), startDate, endDate);

        } else {
            throw new IllegalArgumentException("Order must be 'asc' or 'desc'");
        }

        return expenses.stream().map(ExpenseResponse::new).collect(Collectors.toList());
    }

    public void deleteBuUserIDAndExpenseIds(String userId, List<Expense> expenses) {
        User user = userService.GetActiveUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).getId() == null) {
                throw new IllegalArgumentException("Expense ID must be provided");
            }
            expenses.set(i, getExpenseById(expenses.get(i).getId().toString()));
        }

        for (Expense expense : expenses) {
            if (!expense.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Expense does not belong to user");
            }
        }
        expenseRepository.deleteAll(expenses);
        for (Expense expense : expenses) {
            try {
                budgetService.updateBudgetAmountByUserIdAndCategoryId(expense.getUser().getId().toString(), expense.getCategory().getId().toString(), expense.getAmount().negate(), expense.getExpenseDate());
            } catch (Exception e) {
                throw new IllegalArgumentException("Error updating budget: " + e.getMessage());
            }
        }
    }

    public ExpenseResList fetchExpensesWithConditions(String userId, LocalDateTime startDate,
                                                      LocalDateTime endDate, String order,
                                                      String categoryId, int page, int limit,
                                                      String q, String customSortBy, String customSortOrder) {
        long totalPages, totalElements = 0;
        System.out.println(customSortBy + " " + customSortOrder);
        User user = userService.GetActiveUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (page < 1) {
            throw new IllegalArgumentException("Page must be greater than 0");
        }
        int offset = (page - 1) * limit;

        List<Expense> expenses;
        UUID categoryUUID = null;
        if (q == null) q = "";
        if (order == null) order = "desc";
        else order = order.toLowerCase();

        if (categoryId != null) {
            Category category = categoryService.getCategoryById(categoryId);
            if (category == null) {
                throw new IllegalArgumentException("Category not found");
            }
            categoryUUID = category.getId();
        }
//        q = "%" + q + "%";
//        if (categoryId != null) {
//            Category category = categoryService.getCategoryById(categoryId);
//            if (category == null) {
//                throw new IllegalArgumentException("Category not found");
//            }
//            categoryUUID = category.getId();
//            if (order == null || order.equalsIgnoreCase("desc")) {
//                expenses = expenseRepository.findByUserIdAndTimeFrameAndCategoryDescWithLimit(user.getId(), startDate, endDate, categoryUUID, limit, offset, q);
//            } else if (order.equalsIgnoreCase("asc")) {
//                expenses = expenseRepository.findByUserIdAndTimeFrameAndCategoryAscWithLimit(user.getId(), startDate, endDate, categoryUUID, limit, offset, q);
//            } else {
//                throw new IllegalArgumentException("Order must be 'asc' or 'desc'");
//            }
//            totalElements = expenseRepository.countByUserIdAndTimeFrameAndCategory(user.getId(), startDate, endDate, categoryUUID, q);
//        } else {
//            if (order == null || order.equalsIgnoreCase("desc")) {
//                expenses = expenseRepository.findByUserIdAndTimeFrameDescWithLimit(user.getId(), startDate, endDate, limit, offset, q);
//            } else if (order.equalsIgnoreCase("asc")) {
//                expenses = expenseRepository.findByUserIdAndTimeFrameAscWithLimit(user.getId(), startDate, endDate, limit, offset, q);
//            } else {
//                throw new IllegalArgumentException("Order must be 'asc' or 'desc'");
//            }
//            totalElements = expenseRepository.countByUserIdAndTimeFrame(user.getId(), startDate, endDate, q);
//        }
        expenses = expenseRepositoryCustomImpl.findExpenses(user.getId(), startDate, endDate,
                categoryUUID, q, offset, limit, customSortBy, customSortOrder, order);
        totalElements = expenseRepositoryCustomImpl.countExpenses(user.getId(), startDate, endDate,
                categoryUUID, q);

        totalPages = (int) Math.ceil((double) totalElements / limit);
        return new ExpenseResList(expenses.stream().map(ExpenseResponse::new).collect(Collectors.toList()), totalPages, totalElements, page);
    }

    public List<MonthlyCategoryExpense> getMonthlyCategoryExpense(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        User user = userService.GetActiveUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        return expenseRepository.findMonthlyCategoryExpenseByUserId(user.getId(), startDate, endDate);

    }

    public List<DailyExpense> getDailyExpense(String userId, LocalDateTime startDate, LocalDateTime endDate) {
        User user = userService.GetActiveUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        return expenseRepository.findDailyExpenseByUserIdAndTimeFrame(user.getId(), startDate, endDate);
    }

    public String exportExpensesToCSV(String userId, LocalDateTime startDate, LocalDateTime endDate) throws IOException {
        UUID userIdUUID = UUID.fromString(userId);
        User user = userService.GetActiveUserById(userId);
        List<Expense> expenses = expenseRepository.findByUserIdAndTimeFrameAsc(userIdUUID, startDate, endDate);

        StringWriter sw = new StringWriter();
        CSVWriter writer = new CSVWriter(sw);

        // header
        writer.writeNext(new String[]{"Date", "Description", "Amount (in " + user.getCurrency() + ")", "Category"});

        // rows
        for (Expense expense : expenses) {
            writer.writeNext(new String[]{
                    expense.getExpenseDate().toString(),
                    expense.getDescription(),
                    String.valueOf(expense.getAmount()),
                    expense.getCategory().getName()
            });
        }
        writer.close();
        return sw.toString();
    }

}
